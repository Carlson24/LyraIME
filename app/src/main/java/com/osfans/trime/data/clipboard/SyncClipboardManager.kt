// SPDX-FileCopyrightText: 2015 - 2025 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.data.clipboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.util.Base64
import com.osfans.trime.data.clipboard.model.ClipboardContent
import com.osfans.trime.data.clipboard.model.ClipboardContentType
import com.osfans.trime.data.clipboard.model.ProfileDto
import com.osfans.trime.data.clipboard.model.contentToProfileDto
import com.osfans.trime.data.clipboard.model.profileDtoToContent
import com.osfans.trime.data.clipboard.sync.BackendFactory
import com.osfans.trime.data.clipboard.sync.ISyncClipboardAPI
import com.osfans.trime.data.clipboard.sync.ServerConfig
import com.osfans.trime.data.clipboard.sync.ServerType
import com.osfans.trime.data.clipboard.sync.SignalRClient
import com.osfans.trime.data.clipboard.util.HashUtils
import com.osfans.trime.data.db.DownloadStatus
import com.osfans.trime.data.db.SyncEntryType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

interface SyncClipboardPrefs {
    val syncClipboardEnabled: Boolean
    val syncClipboardServerBase: String
    val syncClipboardUsername: String
    val syncClipboardPassword: String
    val syncClipboardAutoPullEnabled: Boolean
    val syncClipboardPullIntervalSec: Int
    var syncClipboardLastUploadedHash: String
    var syncClipboardLastFileName: String
    val syncClipboardServerType: String
    val syncClipboardS3Region: String
    val syncClipboardS3BucketName: String
    val syncClipboardS3ObjectPrefix: String
    val syncClipboardS3ForcePathStyle: Boolean
    val syncClipboardSignalREnabled: Boolean
    val syncClipboardAutoDownloadMaxSize: Long
    val syncClipboardWebdavRemotePath: String
}

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

    companion object {
        private const val TAG = "SyncClipboardManager"
    }

    private val clipboard by lazy {
        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    }
    private val fileManager by lazy { ClipboardFileManager(context) }
    private val backendLock = Any()

    @Volatile private var backend: ISyncClipboardAPI? = null

    @Volatile private var signalRClient: SignalRClient? = null

    private var pullJob: Job? = null
    private var listenerRegistered = false

    @Volatile private var suppressNextChange = false

    @Volatile private var lastPulledServerHash: String? = null

    @Volatile private var justUploadedHash: String? = null

    @Volatile private var justSetLocalHash: String? = null

    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener {
        if (suppressNextChange) {
            suppressNextChange = false
            return@OnPrimaryClipChangedListener
        }
        if (!prefs.syncClipboardEnabled) return@OnPrimaryClipChangedListener
        scope.launch(Dispatchers.IO) {
            try {
                handleLocalClipboardChange()
            } catch (e: Throwable) {
                Timber.tag(TAG).e(e, "Failed to handle local clipboard change")
            }
        }
    }

    fun start() {
        if (!prefs.syncClipboardEnabled) return
        ensureListener()
        ensurePullLoop()
        connectSignalR()
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
        signalRClient?.disconnect()
        signalRClient = null
        suppressNextChange = false
        lastPulledServerHash = null
        justUploadedHash = null
        justSetLocalHash = null
        synchronized(backendLock) {
            BackendFactory.clearCache()
            backend = null
        }
    }

    fun onPrefsChanged() {
        pullJob?.cancel()
        pullJob = null
        signalRClient?.disconnect()
        signalRClient = null
        synchronized(backendLock) {
            BackendFactory.clearCache()
            backend = null
        }
        if (prefs.syncClipboardEnabled) {
            ensureListener()
            ensurePullLoop()
            connectSignalR()
        } else {
            try {
                if (listenerRegistered) clipboard.removePrimaryClipChangedListener(clipListener)
            } catch (e: Throwable) {
                Timber.tag(TAG).e(e, "Failed to remove clipboard listener")
            }
            listenerRegistered = false
        }
    }

    private fun getBackend(): ISyncClipboardAPI? {
        synchronized(backendLock) {
            if (backend != null) return backend
            val config = buildServerConfig() ?: return null
            backend = BackendFactory.createBackend(config, fileManager)
            return backend
        }
    }

    private fun buildServerConfig(): ServerConfig? {
        val url = prefs.syncClipboardServerBase.trim()
        if (url.isBlank()) return null
        val serverType = ServerType.fromString(prefs.syncClipboardServerType)
        return ServerConfig(
            type = serverType,
            url = url,
            username = prefs.syncClipboardUsername.takeIf { it.isNotBlank() },
            password = prefs.syncClipboardPassword.takeIf { it.isNotBlank() },
            region = prefs.syncClipboardS3Region.takeIf { it.isNotBlank() },
            bucketName = prefs.syncClipboardS3BucketName.takeIf { it.isNotBlank() },
            objectPrefix = prefs.syncClipboardS3ObjectPrefix.takeIf { it.isNotBlank() },
            forcePathStyle = prefs.syncClipboardS3ForcePathStyle,
            remotePath = prefs.syncClipboardWebdavRemotePath.takeIf { it.isNotBlank() },
        )
    }

    private fun connectSignalR() {
        val serverType = ServerType.fromString(prefs.syncClipboardServerType)
        if (serverType != ServerType.SYNC_CLIPBOARD) return
        if (!prefs.syncClipboardSignalREnabled) return

        val url = prefs.syncClipboardServerBase.trim()
        if (url.isBlank()) return
        val hubUrl = "${url.trimEnd('/')}/SyncClipboardHub"
        val authHeader = buildAuthHeader()

        val client = SignalRClient(
            hubUrl = hubUrl,
            authHeader = authHeader,
            scope = scope,
        ).apply {
            listener = object : SignalRClient.Listener {
                override fun onProfileChanged(profile: ProfileDto) {
                    scope.launch(Dispatchers.IO) {
                        handleRemoteProfileDto(profile, updateClipboard = true)
                    }
                }

                override fun onHistoryChanged(hash: String, type: String) {
                    Timber.tag(TAG).d("History changed: $hash ($type)")
                }

                override fun onStateChanged(state: SignalRClient.ConnectionState) {
                    Timber.tag(TAG).d("SignalR state: $state")
                }
            }
        }

        signalRClient = client
        client.connect()
    }

    private fun buildAuthHeader(): String? {
        val u = prefs.syncClipboardUsername
        val p = prefs.syncClipboardPassword
        if (u.isBlank() || p.isBlank()) return null
        val token = "$u:$p".toByteArray(Charsets.UTF_8)
        val b64 = Base64.encodeToString(token, Base64.NO_WRAP)
        return "Basic $b64"
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

    private suspend fun handleLocalClipboardChange() {
        val content = readClipboardContent() ?: return
        val profileHash = content.profileHash
            ?: HashUtils.calculateTextHash(content.text)
        if (profileHash.isEmpty()) return

        if (profileHash == justSetLocalHash) {
            justSetLocalHash = null
            Timber.tag(TAG).d("Skipping local change: just set by remote pull")
            return
        }

        val lastHash = try {
            prefs.syncClipboardLastUploadedHash
        } catch (e: Throwable) {
            Timber.tag(TAG).e(e, "Failed to read last uploaded hash")
            ""
        }
        if (profileHash == lastHash) {
            Timber.tag(TAG).d("Skipping local change: same as last uploaded")
            return
        }

        uploadContent(content)
    }

    private fun readClipboardContent(): ClipboardContent? {
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

        val uri = try {
            item.uri
        } catch (e: Throwable) {
            null
        }

        val html = try {
            item.htmlText
        } catch (e: Throwable) {
            null
        }

        return when {
            text != null && text.isNotEmpty() -> {
                val hash = HashUtils.calculateTextHash(text)
                ClipboardContent(
                    type = ClipboardContentType.Text,
                    text = text,
                    profileHash = hash,
                    localClipboardHash = hash,
                )
            }

            uri != null -> {
                ClipboardContent(
                    type = ClipboardContentType.File,
                    text = text ?: "",
                    fileUri = uri.toString(),
                    hasData = true,
                )
            }

            html != null && html.isNotEmpty() -> {
                val hash = HashUtils.calculateTextHash(html)
                ClipboardContent(
                    type = ClipboardContentType.Text,
                    text = html,
                    profileHash = hash,
                    localClipboardHash = hash,
                )
            }

            else -> null
        }
    }

    private suspend fun uploadContent(content: ClipboardContent) {
        val backend = getBackend() ?: return
        try {
            val profile = contentToProfileDto(content)
            Timber.tag(TAG).i("Upload: type=${profile.type} text_len=${content.text.length}")
            justUploadedHash = profile.hash
            backend.putContent(content)

            val hash = profile.hash
            if (hash != null && hash.isNotEmpty()) {
                try {
                    prefs.syncClipboardLastUploadedHash = hash
                } catch (e: Throwable) {
                    Timber.tag(TAG).e(e, "Failed to save uploaded hash")
                }
            }
            Timber.tag(TAG).i("Upload success")
            try {
                listener?.onUploadSuccess()
            } catch (e: Throwable) {
                Timber.tag(TAG).e(e, "Failed to notify upload success listener")
            }
        } catch (e: Throwable) {
            Timber.tag(TAG).i("Upload failed: ${e.message}")
            try {
                listener?.onUploadFailed(e.message)
            } catch (t: Throwable) {
                Timber.tag(TAG).e(t, "Failed to notify upload failed listener")
            }
        }
    }

    suspend fun pullNow(updateClipboard: Boolean): Pair<Boolean, String?> {
        val backend = getBackend() ?: return false to null
        return try {
            val profile = backend.getClipboard()
            Timber.tag(TAG).i("Pull: type=${profile.type} hasData=${profile.hasData} text_len=${profile.text.length}")
            if (!profile.hasData && profile.text.isEmpty()) {
                Timber.tag(TAG).i("Pull: empty clipboard, skipping")
                return false to null
            }
            val result = handleRemoteProfileDto(profile, updateClipboard)
            Timber.tag(TAG).i("Pull: done, result_len=${result.length}")
            true to result
        } catch (e: Throwable) {
            Timber.tag(TAG).i("Pull failed: ${e.message}")
            false to null
        }
    }

    private suspend fun handleRemoteProfileDto(
        profile: ProfileDto,
        updateClipboard: Boolean,
    ): String {
        val profileHash = profile.hash

        if (profileHash != null && profileHash == justUploadedHash) {
            justUploadedHash = null
            Timber.tag(TAG).d("Skipping remote profile: just uploaded from this device")
            return profile.text
        }

        val content = profileDtoToContent(profile)

        return when (content.type) {
            ClipboardContentType.Text -> handleTextPayload(content, profile, updateClipboard)

            ClipboardContentType.Image -> handleFilePayload(
                content,
                profile,
                SyncEntryType.IMAGE,
                updateClipboard,
            )

            ClipboardContentType.File -> handleFilePayload(
                content,
                profile,
                SyncEntryType.FILE,
                updateClipboard,
            )

            ClipboardContentType.Group -> handleFilePayload(
                content,
                profile,
                SyncEntryType.FILE,
                updateClipboard,
            )
        }
    }

    private suspend fun handleTextPayload(
        content: ClipboardContent,
        profile: ProfileDto,
        updateClipboard: Boolean,
    ): String {
        val text = content.text
        val newServerHash = profile.hash

        try {
            clipboardStore?.clearFileEntries()
            prefs.syncClipboardLastFileName = ""
        } catch (t: Throwable) {
            Timber.tag(TAG).e(t, "Failed to clear file entries on text payload")
        }

        val prevServerHash = lastPulledServerHash
        lastPulledServerHash = newServerHash

        if (updateClipboard) {
            if (newServerHash != null && newServerHash == prevServerHash) {
                Timber.tag(TAG).d("Pull: text unchanged, skipping")
                return text
            }
            val cur = readClipboardText()
            if (text.isNotEmpty() && text != cur) {
                justSetLocalHash = if (newServerHash != null) newServerHash else HashUtils.calculateTextHash(text)
                writeClipboardText(text)
                Timber.tag(TAG).i("Pull: wrote text to clipboard (len=${text.length})")
                try {
                    prefs.syncClipboardLastUploadedHash = HashUtils.calculateTextHash(text)
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

    private suspend fun handleFilePayload(
        content: ClipboardContent,
        profile: ProfileDto,
        entryType: SyncEntryType,
        updateClipboard: Boolean,
    ): String {
        val fileName = content.fileName
        if (fileName.isNullOrBlank()) {
            Timber.tag(TAG).w("File payload has no file name")
            return ""
        }

        val prevName = try {
            prefs.syncClipboardLastFileName
        } catch (e: Throwable) {
            Timber.tag(TAG).e(e, "Failed to read last file name")
            ""
        }
        if (fileName == prevName) {
            Timber.tag(TAG).d("File payload unchanged, skip: $fileName")
            return fileName
        }

        val shouldAutoDownload = content.fileSize != null &&
            content.fileSize <= prefs.syncClipboardAutoDownloadMaxSize * 1024 * 1024

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
            fileSize = content.fileSize,
            localFilePath = localPath,
            downloadStatus = downloadStatus,
        )

        if (updateClipboard && shouldAutoDownload && !localFile.exists()) {
            scope.launch(Dispatchers.IO) {
                try {
                    downloadFileForPayload(fileName, content.fileSize)
                } catch (e: Throwable) {
                    Timber.tag(TAG).e(e, "Auto-download failed: $fileName")
                }
            }
        }

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

        Timber.tag(TAG).d("File payload handled: $fileName (type: $entryType)")
        return fileName
    }

    private suspend fun downloadFileForPayload(fileName: String, expectedSize: Long?) {
        val backend = getBackend() ?: return
        val store = clipboardStore ?: return

        val entries = store.getAll()
        val entry = entries.find { it.serverFileName == fileName || it.fileName == fileName }
            ?: return

        if (fileManager.fileExists(fileName, expectedSize)) {
            val localFile = fileManager.getFile(fileName)
            store.updateFileEntry(entry.uuid, localFile.absolutePath, DownloadStatus.COMPLETED)
            return
        }

        store.updateFileEntry(entry.uuid, null, DownloadStatus.DOWNLOADING)

        val (ok, localPath) = backend.downloadFile(fileName)

        if (ok && localPath != null) {
            store.updateFileEntry(entry.uuid, localPath, DownloadStatus.COMPLETED)
        } else {
            store.updateFileEntry(entry.uuid, null, DownloadStatus.FAILED)
        }
    }

    suspend fun downloadFile(uuid: String, progressCallback: ((Long, Long) -> Unit)? = null): Boolean {
        val backend = getBackend() ?: return false
        val store = clipboardStore ?: return false
        val entry = store.getEntryByUuid(uuid) ?: return false
        val serverFileName = entry.serverFileName ?: entry.fileName ?: return false

        if (fileManager.fileExists(serverFileName, entry.fileSize)) {
            Timber.tag(TAG).d("File already downloaded: $serverFileName")
            store.updateFileEntry(uuid, fileManager.getFile(serverFileName).absolutePath, DownloadStatus.COMPLETED)
            return true
        }

        store.updateFileEntry(uuid, null, DownloadStatus.DOWNLOADING)

        val (ok, localPath) = backend.downloadFile(serverFileName, progressCallback)

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
        val backend = getBackend() ?: return false to null
        if (fileName.isBlank()) return false to null

        if (fileManager.fileExists(fileName)) {
            val local = fileManager.getFile(fileName)
            return true to local.absolutePath
        }

        return backend.downloadFile(fileName, progressCallback)
    }

    fun uploadOnce(): Boolean {
        if (!prefs.syncClipboardEnabled) return false
        val content = readClipboardContent() ?: return false
        var succeeded = false
        scope.launch(Dispatchers.IO) {
            try {
                uploadContent(content)
                succeeded = true
            } catch (e: Throwable) {
                Timber.tag(TAG).e(e, "uploadOnce failed")
            }
        }
        return succeeded
    }

    fun proactiveUploadIfChanged() {
        if (!prefs.syncClipboardEnabled) return
        val content = readClipboardContent() ?: return
        val profileHash = content.profileHash
            ?: HashUtils.calculateTextHash(content.text)
        if (profileHash.isEmpty()) return
        val last = try {
            prefs.syncClipboardLastUploadedHash
        } catch (e: Throwable) {
            Timber.tag(TAG).e(e, "Failed to read last uploaded hash")
            ""
        }
        if (profileHash != last) {
            scope.launch(Dispatchers.IO) {
                try {
                    uploadContent(content)
                } catch (e: Throwable) {
                    Timber.tag(TAG).e(e, "proactiveUploadIfChanged failed")
                }
            }
        }
    }

    fun refreshServerConfig() {
        synchronized(backendLock) {
            BackendFactory.clearCache()
            backend = null
        }
        signalRClient?.disconnect()
        signalRClient = null
        connectSignalR()
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
        }
    }
}
