// SPDX-FileCopyrightText: 2015 - 2025 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.data.clipboard.sync

import android.net.Uri
import com.osfans.trime.data.clipboard.ClipboardFileManager
import com.osfans.trime.data.clipboard.model.ClipboardContent
import com.osfans.trime.data.clipboard.model.ProfileDto
import com.osfans.trime.data.clipboard.model.contentToProfileDto
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber
import java.io.File
import java.util.concurrent.TimeUnit

class WebDAVBackend(
    private val baseUrl: String,
    private val authHeader: String?,
    private val remotePath: String?,
    private val fileManager: ClipboardFileManager,
) : ISyncClipboardAPI {

    companion object {
        private const val TAG = "WebDAVBackend"
        private const val PROFILE_FILE = "SyncClipboard.json"
        private const val FILE_FOLDER = "file"
    }

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val json: Json = Json { ignoreUnknownKeys = true }

    private val normalizedBaseUrl: String
        get() {
            val base = baseUrl.trimEnd('/')
            val sub = remotePath?.trim('/')?.takeIf { it.isNotBlank() }
            return if (sub != null) "$base/$sub" else base
        }

    private fun profileUrl(): String = "$normalizedBaseUrl/$PROFILE_FILE"

    private fun fileUrl(fileName: String): String = "$normalizedBaseUrl/$FILE_FOLDER/${Uri.encode(fileName)}"

    private fun Request.Builder.withAuth(): Request.Builder = if (authHeader != null) {
        header("Authorization", authHeader).apply {
            header("Cache-Control", "no-cache")
            header("Pragma", "no-cache")
        }
    } else {
        this
    }

    private suspend inline fun <T> executeRequest(
        requestBuilder: () -> Request,
        crossinline handler: (okhttp3.Response) -> T?,
    ): T? = try {
        val req = requestBuilder()
        client.newCall(req).execute().use { resp ->
            if (resp.isSuccessful) {
                handler(resp)
            } else {
                Timber.tag(TAG).w("Request failed: ${resp.code} ${resp.message}")
                null
            }
        }
    } catch (e: Throwable) {
        Timber.tag(TAG).e(e, "Request failed")
        null
    }

    override suspend fun getClipboard(): ProfileDto = try {
        executeRequest(
            requestBuilder = {
                Request.Builder()
                    .url(profileUrl())
                    .withAuth()
                    .get()
                    .build()
            },
            handler = { resp ->
                val body = resp.body.string()
                if (body.isNotEmpty()) {
                    json.decodeFromString<ProfileDto>(body)
                } else {
                    ProfileDto(type = "Text", text = "", hasData = false)
                }
            },
        ) ?: ProfileDto(type = "Text", text = "", hasData = false)
    } catch (e: Throwable) {
        if (e.message?.contains("404") == true) {
            ProfileDto(type = "Text", text = "", hasData = false)
        } else {
            Timber.tag(TAG).e(e, "getClipboard failed")
            ProfileDto(type = "Text", text = "", hasData = false)
        }
    }

    override suspend fun putClipboard(profile: ProfileDto) {
        val body = json.encodeToString(profile)
        val req = Request.Builder()
            .url(profileUrl())
            .withAuth()
            .put(body.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                Timber.tag(TAG).w("putClipboard failed: ${resp.code} ${resp.message}")
                throw RuntimeException("putClipboard: HTTP ${resp.code}")
            }
        }
    }

    override suspend fun downloadFile(
        fileName: String,
        progressCallback: ((Long, Long) -> Unit)?,
    ): Pair<Boolean, String?> {
        if (fileManager.fileExists(fileName)) {
            val local = fileManager.getFile(fileName)
            return true to local.absolutePath
        }
        return try {
            executeRequest(
                requestBuilder = {
                    Request.Builder()
                        .url(fileUrl(fileName))
                        .withAuth()
                        .get()
                        .build()
                },
                handler = { resp ->
                    val totalBytes = resp.body.contentLength()
                    val localPath = fileManager.saveFile(
                        fileName,
                        resp.body.byteStream(),
                        totalBytes,
                        progressCallback,
                    )
                    if (localPath != null) true to localPath else false to null
                },
            ) ?: (false to null)
        } catch (e: Throwable) {
            Timber.tag(TAG).e(e, "downloadFile failed: $fileName")
            false to null
        }
    }

    override suspend fun putFile(
        fileName: String,
        localFilePath: String,
        progressCallback: ((Long, Long) -> Unit)?,
    ) {
        ensureFileFolderExists()
        val file = File(localFilePath)
        if (!file.exists()) {
            Timber.tag(TAG).w("putFile: local file not found: $localFilePath")
            return
        }
        try {
            executeRequest(
                requestBuilder = {
                    val body = file.asRequestBody("application/octet-stream".toMediaType())
                    Request.Builder()
                        .url(fileUrl(fileName))
                        .withAuth()
                        .put(body)
                        .build()
                },
                handler = { },
            )
        } catch (e: Throwable) {
            Timber.tag(TAG).e(e, "putFile failed: $fileName")
        }
    }

    override suspend fun putContent(
        content: ClipboardContent,
        options: PutContentOptions?,
    ) {
        val profile = contentToProfileDto(content)
        if (profile.hasData && content.fileUri != null) {
            val fileName = profile.dataName ?: File(content.fileUri).name
            putFile(fileName, content.fileUri)
        }
        putClipboard(profile)
    }

    override suspend fun getServerInfo(): ServerInfo = try {
        val version = executeRequest(
            requestBuilder = {
                Request.Builder()
                    .url(normalizedBaseUrl)
                    .withAuth()
                    .method("OPTIONS", null)
                    .build()
            },
            handler = { resp -> resp.header("Server", "") ?: "" },
        ) ?: ""

        val serverTime = executeRequest(
            requestBuilder = {
                Request.Builder()
                    .url(normalizedBaseUrl)
                    .withAuth()
                    .method("PROPFIND", null)
                    .header("Depth", "0")
                    .build()
            },
            handler = { resp ->
                val dateHeader = resp.header("Date") ?: ""
                parseDateHeader(dateHeader)
            },
        ) ?: 0L

        ServerInfo(version = version, serverTime = serverTime)
    } catch (e: Throwable) {
        Timber.tag(TAG).e(e, "getServerInfo failed")
        ServerInfo()
    }

    override suspend fun testConnection() {
        getServerInfo()
    }

    private suspend fun ensureFileFolderExists() {
        val folderUrl = "$normalizedBaseUrl/$FILE_FOLDER"
        try {
            executeRequest(
                requestBuilder = {
                    Request.Builder()
                        .url(folderUrl)
                        .withAuth()
                        .method("PROPFIND", null)
                        .header("Depth", "0")
                        .build()
                },
                handler = { resp ->
                    if (resp.code == 404) {
                        client.newCall(
                            Request.Builder()
                                .url(folderUrl)
                                .withAuth()
                                .method("MKCOL", null)
                                .build(),
                        ).execute().use { }
                    }
                },
            )
        } catch (e: Throwable) {
            Timber.tag(TAG).e(e, "ensureFileFolderExists failed")
        }
    }

    private fun parseDateHeader(dateHeader: String): Long {
        return try {
            val cleaned = dateHeader.trim()
            if (cleaned.isEmpty()) return 0L
            java.text.SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", java.util.Locale.US).run {
                parse(cleaned)?.time ?: 0L
            }
        } catch (e: Throwable) {
            0L
        }
    }
}
