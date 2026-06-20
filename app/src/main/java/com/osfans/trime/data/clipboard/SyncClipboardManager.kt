// SPDX-FileCopyrightText: 2015 - 2025 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.data.clipboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.util.Base64
import com.osfans.trime.data.db.DownloadStatus
import com.osfans.trime.data.db.SyncEntryType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

interface SyncClipboardPrefs {
    val syncClipboardEnabled: Boolean
    val syncClipboardServerBase: String
    val syncClipboardUsername: String
    val syncClipboardPassword: String
    val syncClipboardAutoPullEnabled: Boolean
    val syncClipboardPullIntervalSec: Int
    var syncClipboardLastUploadedHash: String
    var syncClipboardLastFileName: String
}

@Serializable
private data class UploadClipboardPayload(
    val hasData: Boolean = false,
    val text: String,
    val type: String = "Text",
)

@Serializable
private data class PullClipboardPayload(
    val text: String? = null,
    val type: String? = null,
    val hasData: Boolean? = null,
    val dataName: String? = null,
    @SerialName("Clipboard") val legacyClipboard: String? = null,
    @SerialName("Type") val legacyType: String? = null,
    @SerialName("File") val legacyFile: String? = null,
)

class SyncClipboardManager(
    private val context: Context,
    private val prefs: SyncClipboardPrefs,
    private val scope: CoroutineScope,
    private val listener: Listener? = null,
    private val clipboardStore: ClipboardHistoryStore? = null,
) {
    interface Listener {
        fun onPulledNewContent(text: String)
        fun onUploadSuccess()
        fun onUploadFailed(reason: String? = null)
        fun onFilePulled(type: SyncEntryType, fileName: String, serverFileName: String)
    }

    private val clipboard by lazy {
        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    }
    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .writeTimeout(8, TimeUnit.SECONDS)
            .build()
    }
    private val json by lazy { Json { ignoreUnknownKeys = true } }
    private val fileManager by lazy { ClipboardFileManager(context) }

    companion object {
        private const val TAG = "SyncClipboardManager"
    }

    private var pullJob: Job? = null
    private var listenerRegistered = false

    @Volatile private var suppressNextChange = false

    @Volatile private var lastPulledServerHash: String? = null

    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener {
        if (suppressNextChange) {
            suppressNextChange = false
            return@OnPrimaryClipChangedListener
        }
        if (!prefs.syncClipboardEnabled) return@OnPrimaryClipChangedListener
        scope.launch(Dispatchers.IO) {
            try {
                uploadCurrentClipboardText()
            } catch (e: Throwable) {
                Timber.tag(TAG).e(e, "Failed to upload clipboard text on change")
            }
        }
    }

    fun start() {
        if (!prefs.syncClipboardEnabled) return
        ensureListener()
        ensurePullLoop()
    }

    fun stop() {
        try {
            if (listenerRegistered) clipboard.removePrimaryClipChangedListener(clipListener)
        } catch (e: Throwable) {
            Timber.tag(TAG).e(e, "Failed to remove clipboard listener")
        }
        listenerRegistered = false
        pullJob?.cancel()
        pullJob = null
        suppressNextChange = false
        lastPulledServerHash = null
    }

    private fun ensureListener() {
        if (!listenerRegistered) {
            try {
                clipboard.addPrimaryClipChangedListener(clipListener)
                listenerRegistered = true
            } catch (e: Throwable) {
                Timber.tag(TAG).e(e, "Failed to add clipboard listener")
            }
        }
    }

    private fun ensurePullLoop() {
        pullJob?.cancel()
        if (!prefs.syncClipboardAutoPullEnabled) return
        val intervalSec = prefs.syncClipboardPullIntervalSec.coerceIn(1, 600)
        pullJob = scope.launch(Dispatchers.IO) {
            while (isActive && prefs.syncClipboardEnabled && prefs.syncClipboardAutoPullEnabled) {
                try {
                    pullNow(updateClipboard = true)
                } catch (e: Throwable) {
                    Timber.tag(TAG).e(e, "Failed to pull clipboard in loop")
                }
                delay(intervalSec * 1000L)
            }
        }
    }

    private fun buildUrl(): String? {
        val raw = prefs.syncClipboardServerBase.trim()
        if (raw.isBlank()) return null
        val base = raw.trimEnd('/')
        val lower = base.lowercase()
        return if (lower.endsWith(".json")) base else "$base/SyncClipboard.json"
    }

    private fun sha256Hex(s: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = md.digest(s.toByteArray(Charsets.UTF_8))
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) sb.append(String.format("%02x", b))
        return sb.toString()
    }

    private fun authHeaderB64(): String? {
        val u = prefs.syncClipboardUsername
        val p = prefs.syncClipboardPassword
        if (u.isBlank() || p.isBlank()) return null
        val token = "$u:$p".toByteArray(Charsets.UTF_8)
        val b64 = Base64.encodeToString(token, Base64.NO_WRAP)
        return "Basic $b64"
    }

    private fun readClipboardText(): String? {
        val clip = try {
            clipboard.primaryClip
        } catch (e: Throwable) {
            Timber.tag(TAG).e(e, "Failed to read clipboard")
            null
        } ?: return null
        if (clip.itemCount <= 0) return null
        val item = clip.getItemAt(0)
        val text = try {
            item.coerceToText(context)?.toString()
        } catch (e: Throwable) {
            Timber.tag(TAG).e(e, "Failed to coerce clipboard item to text")
            null
        }
        return text?.takeIf { it.isNotEmpty() }
    }

    private fun writeClipboardText(text: String) {
        val clip = ClipData.newPlainText("SyncClipboard", text)
        suppressNextChange = true
        try {
            clipboard.setPrimaryClip(clip)
        } catch (e: Throwable) {
            Timber.tag(TAG).e(e, "Failed to write clipboard text")
        } finally {
            suppressNextChange = false
        }
    }

    private fun uploadCurrentClipboardText() {
        val url = buildUrl() ?: return
        val authB64 = authHeaderB64() ?: return
        val text = readClipboardText() ?: return
        if (text.isEmpty()) return
        try {
            val newHash = sha256Hex(text)
            val last = try {
                prefs.syncClipboardLastUploadedHash
            } catch (e: Throwable) {
                Timber.tag(TAG).e(e, "Failed to read last uploaded hash")
                ""
            }
            if (newHash == last) return
        } catch (e: Throwable) {
            Timber.tag(TAG).e(e, "Failed to compute hash for clipboard text")
        }
        uploadText(url, authB64, text)
    }

    private fun uploadText(url: String, auth: String, text: String): Boolean = try {
        val payload = UploadClipboardPayload(text = text)
        val bodyJson = json.encodeToString(payload)
        val req = Request.Builder()
            .url(url)
            .header("Authorization", auth)
            .put(bodyJson.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()
        client.newCall(req).execute().use { resp ->
            if (resp.isSuccessful) {
                try {
                    prefs.syncClipboardLastUploadedHash = sha256Hex(text)
                } catch (e: Throwable) {
                    Timber.tag(TAG).e(e, "Failed to save uploaded hash")
                }
                try {
                    listener?.onUploadSuccess()
                } catch (e: Throwable) {
                    Timber.tag(TAG).e(e, "Failed to notify upload success listener")
                }
                true
            } else {
                Timber.tag(TAG).w("Upload failed with status: ${resp.code}")
                try {
                    listener?.onUploadFailed("HTTP ${resp.code}")
                } catch (e: Throwable) {
                    Timber.tag(TAG).e(e, "Failed to notify upload failed listener")
                }
                false
            }
        }
    } catch (e: Throwable) {
        Timber.tag(TAG).e(e, "Failed to upload clipboard text")
        try {
            listener?.onUploadFailed(e.message)
        } catch (t: Throwable) {
            Timber.tag(TAG).e(t, "Failed to notify upload failed listener (exception)")
        }
        false
    }

    fun uploadOnce(): Boolean {
        val url = buildUrl() ?: return false
        val authB64 = authHeaderB64() ?: return false
        val text = readClipboardText() ?: return false
        if (text.isEmpty()) return false
        return try {
            uploadText(url, authB64, text)
        } catch (e: Throwable) {
            Timber.tag(TAG).e(e, "uploadOnce failed")
            false
        }
    }

    private suspend fun <T> executeRequestWithAuth(
        requestBuilder: (auth: String) -> Request,
        responseHandler: suspend (okhttp3.Response) -> T?,
    ): T? {
        val authB64 = authHeaderB64() ?: return null
        return try {
            val req = requestBuilder(authB64)
            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    return responseHandler(resp)
                }
                Timber.tag(TAG).w("Auth failed with status: ${resp.code}")
                null
            }
        } catch (e: Throwable) {
            Timber.tag(TAG).e(e, "Auth request failed")
            null
        }
    }

    suspend fun pullNow(updateClipboard: Boolean): Pair<Boolean, String?> {
        val url = buildUrl() ?: return false to null

        val result = try {
            executeRequestWithAuth(
                requestBuilder = { auth ->
                    Request.Builder()
                        .url(url)
                        .header("Authorization", auth)
                        .get()
                        .build()
                },
                responseHandler = { resp ->
                    val body = resp.body.string().takeIf { it.isNotEmpty() }
                    if (body == null) {
                        Timber.tag(TAG).w("Pull response body is empty")
                        return@executeRequestWithAuth null
                    }

                    val payload = try {
                        json.decodeFromString<PullClipboardPayload>(body)
                    } catch (e: Throwable) {
                        Timber.tag(TAG).e(e, "Failed to parse clipboard payload")
                        return@executeRequestWithAuth null
                    }

                    val payloadType = resolvePayloadType(payload)
                    when (payloadType.lowercase()) {
                        "text" -> {
                            val textDataName = if (payload.hasData == true) {
                                payload.dataName?.takeIf { it.isNotEmpty() }
                                    ?: payload.legacyFile?.takeIf { it.isNotEmpty() }
                            } else {
                                null
                            }
                            val text = if (!textDataName.isNullOrBlank()) {
                                downloadTextData(textDataName)
                            } else {
                                resolvePayloadText(payload)
                            }
                            val nonBlankText = text?.takeIf { it.isNotBlank() }
                            if (nonBlankText == null) {
                                Timber.tag(TAG).w("Clipboard text is blank")
                                return@executeRequestWithAuth null
                            }
                            return@executeRequestWithAuth handleTextPayload(
                                nonBlankText,
                                updateClipboard,
                            )
                        }
                        "image", "file" -> {
                            val fileName = resolvePayloadFileName(payload)
                            val nonBlankFileName = fileName?.takeIf { it.isNotBlank() }
                            if (nonBlankFileName == null) {
                                Timber.tag(TAG).w("File name is blank for type: $payloadType")
                                return@executeRequestWithAuth null
                            }
                            val normalizedType = if (payloadType.equals(
                                    "image",
                                    ignoreCase = true,
                                )
                            ) {
                                "Image"
                            } else {
                                "File"
                            }
                            return@executeRequestWithAuth handleFilePayload(
                                normalizedType,
                                nonBlankFileName,
                            )
                        }
                        else -> {
                            Timber.tag(TAG).w("Unsupported payload type: $payloadType")
                            return@executeRequestWithAuth null
                        }
                    }
                },
            )
        } catch (e: Throwable) {
            Timber.tag(TAG).e(e, "pullNow failed")
            null
        }

        return if (result != null) {
            true to result
        } else {
            false to null
        }
    }

    private fun resolvePayloadType(payload: PullClipboardPayload): String {
        val explicitType = payload.type?.trim().takeUnless { it.isNullOrBlank() }
            ?: payload.legacyType?.trim().takeUnless { it.isNullOrBlank() }
        if (explicitType != null) return explicitType
        if (payload.hasData == true &&
            (!payload.dataName.isNullOrBlank() || !payload.legacyFile.isNullOrBlank())
        ) {
            return "File"
        }
        return "Text"
    }

    private fun resolvePayloadText(payload: PullClipboardPayload): String? = payload.text?.takeIf { it.isNotEmpty() }
        ?: payload.legacyClipboard?.takeIf { it.isNotEmpty() }

    private fun resolvePayloadFileName(payload: PullClipboardPayload): String? = payload.dataName?.takeIf { it.isNotEmpty() }
        ?: payload.legacyFile?.takeIf { it.isNotEmpty() }
        ?: payload.text?.takeIf { payload.hasData == true && it.isNotEmpty() }
        ?: payload.legacyClipboard?.takeIf { payload.hasData == true && it.isNotEmpty() }

    private suspend fun downloadTextData(dataName: String): String? {
        val fileUrl = buildFileUrl(dataName) ?: run {
            Timber.tag(TAG).w("Failed to build text data url for: $dataName")
            return null
        }
        return executeRequestWithAuth(
            requestBuilder = { auth ->
                Request.Builder()
                    .url(fileUrl)
                    .header("Authorization", auth)
                    .get()
                    .build()
            },
            responseHandler = { resp ->
                val text = resp.body.string()
                if (text.isEmpty()) {
                    Timber.tag(TAG).w("Downloaded text data is empty: $dataName")
                    return@executeRequestWithAuth null
                }
                text
            },
        )
    }

    private suspend fun handleTextPayload(text: String, updateClipboard: Boolean): String {
        try {
            clipboardStore?.clearFileEntries()
            prefs.syncClipboardLastFileName = ""
        } catch (t: Throwable) {
            Timber.tag(TAG).e(t, "Failed to clear file entries on text payload")
        }

        val newServerHash = try {
            sha256Hex(text)
        } catch (e: Throwable) {
            Timber.tag(TAG).e(e, "Failed to compute hash for pulled text")
            null
        }
        val prevServerHash = lastPulledServerHash
        lastPulledServerHash = newServerHash

        if (updateClipboard) {
            if (newServerHash != null && newServerHash == prevServerHash) {
                return text
            }
            val cur = readClipboardText()
            if (text.isNotEmpty() && text != cur) {
                writeClipboardText(text)
                try {
                    prefs.syncClipboardLastUploadedHash = sha256Hex(text)
                } catch (e: Throwable) {
                    Timber.tag(TAG).e(e, "Failed to save pulled hash")
                }
                try {
                    listener?.onPulledNewContent(text)
                } catch (e: Throwable) {
                    Timber.tag(TAG).e(e, "Failed to notify pulled content listener")
                }
            }
        }
        return text
    }

    private suspend fun handleFilePayload(type: String, fileName: String): String {
        try {
            val prevName = try {
                prefs.syncClipboardLastFileName
            } catch (e: Throwable) {
                Timber.tag(TAG).e(e, "Failed to read last file name")
                ""
            }
            if (fileName.isNotEmpty() && fileName == prevName) {
                Timber.tag(TAG).d("File payload unchanged, skip preview: $fileName")
                return fileName
            }

            val entryType = when (type.lowercase()) {
                "image" -> SyncEntryType.IMAGE
                "file" -> SyncEntryType.FILE
                else -> SyncEntryType.FILE
            }

            val localFile = fileManager.getFile(fileName)
            val downloadStatus = if (localFile.exists()) {
                DownloadStatus.COMPLETED
            } else {
                DownloadStatus.NONE
            }

            val localPath = if (localFile.exists()) localFile.absolutePath else null

            clipboardStore?.addFileEntry(
                type = entryType,
                fileName = fileName,
                serverFileName = fileName,
                fileSize = if (localFile.exists()) localFile.length() else null,
                localFilePath = localPath,
                downloadStatus = downloadStatus,
            )

            try {
                listener?.onFilePulled(entryType, fileName, fileName)
            } catch (e: Throwable) {
                Timber.tag(TAG).e(e, "Failed to notify file pulled listener")
            }

            try {
                prefs.syncClipboardLastFileName = fileName
            } catch (e: Throwable) {
                Timber.tag(TAG).e(e, "Failed to save last file name")
            }

            Timber.tag(TAG).d("File payload handled: $fileName (type: $type, status: $downloadStatus)")
            return fileName
        } catch (e: Throwable) {
            Timber.tag(TAG).e(e, "Failed to handle file payload: $fileName")
            return fileName
        }
    }

    suspend fun downloadFile(uuid: String, progressCallback: ((Long, Long) -> Unit)? = null): Boolean {
        val store = clipboardStore ?: return false
        val entry = store.getEntryByUuid(uuid) ?: return false
        val serverFileName = entry.serverFileName ?: entry.fileName ?: return false

        if (fileManager.fileExists(serverFileName, entry.fileSize)) {
            Timber.tag(TAG).d("File already downloaded: $serverFileName")
            store.updateFileEntry(
                uuid,
                fileManager.getFile(serverFileName).absolutePath,
                DownloadStatus.COMPLETED,
            )
            return true
        }

        store.updateFileEntry(uuid, null, DownloadStatus.DOWNLOADING)

        val (ok, localPath) = downloadFileDirectInternal(
            serverFileName = serverFileName,
            expectedSize = entry.fileSize,
            progressCallback = progressCallback,
        )

        if (ok && localPath != null) {
            store.updateFileEntry(uuid, localPath, DownloadStatus.COMPLETED)
            return true
        }

        store.updateFileEntry(uuid, null, DownloadStatus.FAILED)
        return false
    }

    suspend fun downloadFileDirect(
        fileName: String,
        progressCallback: ((Long, Long) -> Unit)? = null,
    ): Pair<Boolean, String?> {
        if (fileName.isBlank()) return false to null

        if (fileManager.fileExists(fileName)) {
            val local = fileManager.getFile(fileName)
            Timber.tag(TAG).d("File already downloaded (direct): $fileName -> ${local.absolutePath}")
            return true to local.absolutePath
        }

        return downloadFileDirectInternal(
            serverFileName = fileName,
            expectedSize = null,
            progressCallback = progressCallback,
        )
    }

    private fun downloadFileDirectInternal(
        serverFileName: String,
        expectedSize: Long?,
        progressCallback: ((Long, Long) -> Unit)?,
    ): Pair<Boolean, String?> {
        val fileUrl = buildFileUrl(serverFileName) ?: run {
            Timber.tag(TAG).w("Failed to build file url for: $serverFileName")
            return false to null
        }

        val authB64 = authHeaderB64() ?: run {
            Timber.tag(TAG).w("Missing auth header for file download")
            return false to null
        }

        if (fileManager.fileExists(serverFileName, expectedSize)) {
            val local = fileManager.getFile(serverFileName)
            Timber.tag(TAG).d(
                "File already exists with expected size: $serverFileName -> ${local.absolutePath}",
            )
            return true to local.absolutePath
        }

        return try {
            val req = Request.Builder()
                .url(fileUrl)
                .header("Authorization", authB64)
                .get()
                .build()

            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    Timber.tag(TAG).w("Download failed: ${resp.code}")
                    return false to null
                }

                val body = resp.body

                val totalBytes = body.contentLength()
                val localPath = fileManager.saveFile(
                    serverFileName,
                    body.byteStream(),
                    totalBytes,
                    progressCallback,
                )

                if (localPath != null) {
                    Timber.tag(TAG).d("File downloaded successfully: $serverFileName -> $localPath")
                    true to localPath
                } else {
                    Timber.tag(TAG).w("Failed to save downloaded file: $serverFileName")
                    false to null
                }
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Download error: $serverFileName")
            false to null
        }
    }

    private fun buildFileUrl(fileName: String): String? {
        val raw = prefs.syncClipboardServerBase.trim()
        if (raw.isBlank()) return null
        val base = raw.trimEnd('/')
        val encodedFileName = Uri.encode(fileName)
        return "$base/file/$encodedFileName"
    }

    fun proactiveUploadIfChanged() {
        val url = buildUrl() ?: return
        val authB64 = authHeaderB64() ?: return
        val text = readClipboardText() ?: return
        if (text.isEmpty()) return
        val newHash = try {
            sha256Hex(text)
        } catch (e: Throwable) {
            Timber.tag(TAG).e(e, "Failed to compute hash for proactive upload")
            return
        }
        val last = try {
            prefs.syncClipboardLastUploadedHash
        } catch (e: Throwable) {
            Timber.tag(TAG).e(e, "Failed to read last uploaded hash")
            ""
        }
        if (newHash != last) {
            try {
                uploadText(url, authB64, text)
            } catch (e: Throwable) {
                Timber.tag(TAG).e(e, "proactiveUploadIfChanged failed")
            }
        }
    }
}
