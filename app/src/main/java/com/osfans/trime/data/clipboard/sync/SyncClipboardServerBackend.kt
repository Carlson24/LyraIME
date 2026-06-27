// SPDX-FileCopyrightText: 2015 - 2025 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.data.clipboard.sync

import android.net.Uri
import android.util.Base64
import com.osfans.trime.data.clipboard.ClipboardFileManager
import com.osfans.trime.data.clipboard.model.ClipboardContent
import com.osfans.trime.data.clipboard.model.HistoryQueryParams
import com.osfans.trime.data.clipboard.model.HistoryRecordDto
import com.osfans.trime.data.clipboard.model.HistoryRecordUpdateDto
import com.osfans.trime.data.clipboard.model.HistoryStatisticsDto
import com.osfans.trime.data.clipboard.model.ProfileDto
import com.osfans.trime.data.clipboard.model.contentToProfileDto
import com.osfans.trime.data.clipboard.util.HashUtils
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber
import java.io.File
import java.util.concurrent.TimeUnit

class SyncClipboardServerBackend(
    private val baseUrl: String,
    private val authHeader: String?,
    private val fileManager: ClipboardFileManager,
) : ISyncClipboardAPI,
    IHistoryAPI {

    companion object {
        private const val TAG = "SyncClipboardServerBackend"
        private const val PROFILE_FILE = "SyncClipboard.json"
        private const val FILE_ENDPOINT = "file"
        private const val HISTORY_API_PREFIX = "/api/history"
    }

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val json: Json = Json { ignoreUnknownKeys = true }

    private val normalizedBaseUrl: String
        get() = baseUrl.trimEnd('/')

    private fun profileUrl(): String = "$normalizedBaseUrl/$PROFILE_FILE"

    private fun fileUrl(fileName: String): String = "$normalizedBaseUrl/$FILE_ENDPOINT/${Uri.encode(fileName)}"

    private fun historyUrl(path: String): String = "$normalizedBaseUrl$HISTORY_API_PREFIX$path"

    private fun serverTimeUrl(): String = "$normalizedBaseUrl/api/time"

    private fun versionUrl(): String = "$normalizedBaseUrl/version"

    private fun Request.Builder.withAuth(): Request.Builder = if (authHeader != null) header("Authorization", authHeader) else this

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
        Timber.tag(TAG).e(e, "getClipboard failed")
        ProfileDto(type = "Text", text = "", hasData = false)
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
                val errorBody = try {
                    resp.body.string()
                } catch (e: Exception) {
                    ""
                }
                Timber.tag(TAG).w("putClipboard failed: ${resp.code} ${resp.message} $errorBody")
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
                    if (localPath != null) {
                        Timber.tag(TAG).d("File downloaded: $fileName -> $localPath")
                        true to localPath
                    } else {
                        false to null
                    }
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
        val profileId = buildProfileId(profile)
        if (profileId != null) {
            try {
                getRecord(profileId)
            } catch (e: Exception) {
                if (!isRecordNotFound(e)) throw e
                if (profile.hasData && content.fileUri != null) {
                    try {
                        val record = HistoryRecordDto(
                            hash = profile.hash ?: "",
                            type = profile.type,
                            text = profile.text,
                            hasData = true,
                            size = profile.size,
                            version = 1,
                        )
                        uploadRecord(record, content.fileUri)
                    } catch (re: Throwable) {
                        Timber.tag(TAG).e(re, "Failed to upload history record")
                    }
                }
            }
        }
        putClipboard(profile)
    }

    override suspend fun getServerInfo(): ServerInfo = try {
        var version = ""
        var serverTime = 0L
        executeRequest(
            requestBuilder = {
                Request.Builder().url(versionUrl()).withAuth().get().build()
            },
            handler = { resp -> version = resp.body.string() },
        )
        executeRequest(
            requestBuilder = {
                Request.Builder().url(serverTimeUrl()).withAuth().get().build()
            },
            handler = { resp ->
                val body = resp.body.string()
                serverTime = body.trim().toLongOrNull() ?: 0L
            },
        )
        ServerInfo(version = version, serverTime = serverTime)
    } catch (e: Throwable) {
        Timber.tag(TAG).e(e, "getServerInfo failed")
        ServerInfo()
    }

    override suspend fun testConnection() {
        executeRequest(
            requestBuilder = {
                Request.Builder().url(serverTimeUrl()).withAuth().get().build()
            },
            handler = { },
        )
    }

    override suspend fun queryRecords(params: HistoryQueryParams): List<HistoryRecordDto> {
        val builder = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
        params.page?.let { builder.addFormDataPart("page", it.toString()) }
        params.before?.let { builder.addFormDataPart("before", it) }
        params.after?.let { builder.addFormDataPart("after", it) }
        params.modifiedAfter?.let { builder.addFormDataPart("modifiedAfter", it) }
        params.types?.let { builder.addFormDataPart("types", it.toString()) }
        params.searchText?.let { builder.addFormDataPart("searchText", it) }
        params.starred?.let { builder.addFormDataPart("starred", it.toString()) }
        params.sortByLastAccessed?.let { builder.addFormDataPart("sortByLastAccessed", it.toString()) }
        return executeRequest(
            requestBuilder = {
                Request.Builder()
                    .url(historyUrl("/query"))
                    .withAuth()
                    .post(builder.build())
                    .build()
            },
            handler = { resp ->
                val body = resp.body.string()
                json.decodeFromString<List<HistoryRecordDto>>(body)
            },
        ) ?: emptyList()
    }

    override suspend fun getRecord(profileId: String): HistoryRecordDto {
        val encoded = Uri.encode(profileId)
        return executeRequest(
            requestBuilder = {
                Request.Builder()
                    .url(historyUrl("/$encoded"))
                    .withAuth()
                    .get()
                    .build()
            },
            handler = { resp ->
                if (resp.code == 404) {
                    throw RecordNotFoundException("Record not found: $profileId")
                }
                json.decodeFromString<HistoryRecordDto>(resp.body.string())
            },
        ) ?: throw RecordNotFoundException("Record not found: $profileId")
    }

    override suspend fun updateRecord(
        type: String,
        profileId: String,
        update: HistoryRecordUpdateDto,
    ): HistoryRecordDto {
        val encoded = Uri.encode(profileId)
        val encodedType = Uri.encode(type)
        val updateJson = json.encodeToString(update)
        return executeRequest(
            requestBuilder = {
                Request.Builder()
                    .url(historyUrl("/$encodedType/$encoded"))
                    .withAuth()
                    .patch(updateJson.toRequestBody("application/json; charset=utf-8".toMediaType()))
                    .build()
            },
            handler = { resp ->
                json.decodeFromString<HistoryRecordDto>(resp.body.string())
            },
        )!!
    }

    override suspend fun downloadData(
        profileId: String,
        progressCallback: ((Long, Long) -> Unit)?,
    ): Pair<Boolean, String?> {
        val encoded = Uri.encode(profileId)
        return try {
            executeRequest(
                requestBuilder = {
                    Request.Builder()
                        .url(historyUrl("/$encoded/data"))
                        .withAuth()
                        .get()
                        .build()
                },
                handler = { resp ->
                    val totalBytes = resp.body.contentLength()
                    val fileName = "$profileId.data"
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
            Timber.tag(TAG).e(e, "downloadData failed: $profileId")
            false to null
        }
    }

    override suspend fun uploadRecord(
        record: HistoryRecordDto,
        fileUri: String?,
        progressCallback: ((Long, Long) -> Unit)?,
    ): HistoryRecordDto {
        val builder = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("hash", record.hash)
            .addFormDataPart("type", record.type)
        record.text?.let { builder.addFormDataPart("text", it) }
        record.createTime?.let { builder.addFormDataPart("createTime", it) }
        record.lastModified?.let { builder.addFormDataPart("lastModified", it) }
        record.lastAccessed?.let { builder.addFormDataPart("lastAccessed", it) }
        builder.addFormDataPart("starred", (record.starred == true).toString())
        builder.addFormDataPart("pinned", (record.pinned == true).toString())
        record.size?.let { builder.addFormDataPart("size", it.toString()) }
        builder.addFormDataPart("hasData", (record.hasData == true).toString())
        builder.addFormDataPart("isDeleted", (record.isDeleted == true).toString())
        record.version?.let { builder.addFormDataPart("version", it.toString()) }
        if (fileUri != null) {
            val file = File(fileUri)
            if (file.exists()) {
                builder.addFormDataPart(
                    "file",
                    file.name,
                    file.asRequestBody("application/octet-stream".toMediaType()),
                )
            }
        }
        val result = executeRequest(
            requestBuilder = {
                Request.Builder()
                    .url(historyUrl(""))
                    .withAuth()
                    .post(builder.build())
                    .build()
            },
            handler = { resp ->
                json.decodeFromString<HistoryRecordDto>(resp.body.string())
            },
        )
        return result!!
    }

    override suspend fun getStatistics(): HistoryStatisticsDto = try {
        executeRequest(
            requestBuilder = {
                Request.Builder()
                    .url(historyUrl("/statistics"))
                    .withAuth()
                    .get()
                    .build()
            },
            handler = { resp -> json.decodeFromString<HistoryStatisticsDto>(resp.body.string()) },
        ) ?: HistoryStatisticsDto()
    } catch (e: Throwable) {
        Timber.tag(TAG).e(e, "getStatistics failed")
        HistoryStatisticsDto()
    }

    override suspend fun getServerTime(): Long = try {
        executeRequest(
            requestBuilder = {
                Request.Builder().url(serverTimeUrl()).withAuth().get().build()
            },
            handler = { resp -> resp.body.string().trim().toLongOrNull() ?: 0L },
        ) ?: 0L
    } catch (e: Throwable) {
        Timber.tag(TAG).e(e, "getServerTime failed")
        0L
    }

    private fun buildProfileId(profile: ProfileDto): String? {
        val hash = profile.hash ?: return null
        return "${profile.type}-$hash"
    }

    private fun isRecordNotFound(e: Exception): Boolean = e is RecordNotFoundException

    class RecordNotFoundException(message: String) : Exception(message)
}
