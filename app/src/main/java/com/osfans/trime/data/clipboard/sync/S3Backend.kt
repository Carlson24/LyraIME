// SPDX-FileCopyrightText: 2015 - 2025 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.data.clipboard.sync

import android.net.Uri
import android.util.Base64
import com.osfans.trime.data.clipboard.ClipboardFileManager
import com.osfans.trime.data.clipboard.model.ClipboardContent
import com.osfans.trime.data.clipboard.model.ProfileDto
import com.osfans.trime.data.clipboard.model.contentToProfileDto
import com.osfans.trime.data.clipboard.util.HashUtils
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber
import java.io.File
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class S3Backend(
    private val serviceUrl: String?,
    private val region: String,
    private val bucketName: String,
    private val objectPrefix: String?,
    private val forcePathStyle: Boolean,
    private val accessKeyId: String,
    private val secretAccessKey: String,
    private val fileManager: ClipboardFileManager,
) : ISyncClipboardAPI {

    companion object {
        private const val TAG = "S3Backend"
        private const val PROFILE_FILENAME = "SyncClipboard.json"
        private const val FILE_FOLDER = "file"
        private const val AWS_HMAC_SHA256 = "AWS4-HMAC-SHA256"
        private const val AWS_SERVICE = "s3"
        private const val AWS_REQUEST = "aws4_request"
        private const val UNSIGNED_PAYLOAD = "UNSIGNED-PAYLOAD"
    }

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val json: Json = Json { ignoreUnknownKeys = true }

    private val regionVal: String get() = region.ifBlank { "us-east-1" }

    private fun buildEndpoint(): String {
        val svcUrl = serviceUrl?.trimEnd('/')?.takeIf { it.isNotBlank() }
        return if (svcUrl != null) {
            if (forcePathStyle) "$svcUrl/$bucketName" else "$bucketName.$svcUrl"
        } else {
            "$bucketName.s3.$regionVal.amazonaws.com"
        }
    }

    private fun objectKey(suffix: String): String {
        val prefix = objectPrefix?.trimEnd('/')?.takeIf { it.isNotEmpty() }
        return if (prefix != null) "$prefix/$suffix" else suffix
    }

    private fun absoluteUrl(objectKey: String): String {
        val endpoint = buildEndpoint()
        val scheme = if (endpoint.startsWith("http")) "" else "https://"
        return "$scheme$endpoint/${Uri.encode(objectKey)}"
    }

    override suspend fun getClipboard(): ProfileDto = try {
        val key = objectKey(PROFILE_FILENAME)
        val url = absoluteUrl(key)
        val signedReq = signRequest("GET", url, UNSIGNED_PAYLOAD.toByteArray(), emptyMap())
        client.newCall(signedReq).execute().use { resp ->
            if (resp.isSuccessful) {
                val body = resp.body.string()
                if (body.isNotEmpty()) {
                    json.decodeFromString<ProfileDto>(body)
                } else {
                    ProfileDto(type = "Text", text = "", hasData = false)
                }
            } else if (resp.code == 404) {
                ProfileDto(type = "Text", text = "", hasData = false)
            } else {
                Timber.tag(TAG).w("getClipboard failed: ${resp.code}")
                ProfileDto(type = "Text", text = "", hasData = false)
            }
        }
    } catch (e: Throwable) {
        Timber.tag(TAG).e(e, "getClipboard failed")
        ProfileDto(type = "Text", text = "", hasData = false)
    }

    override suspend fun putClipboard(profile: ProfileDto) {
        val bodyJson = json.encodeToString(profile)
        val bodyBytes = bodyJson.toByteArray(Charsets.UTF_8)
        val md5Base64 = md5Base64(bodyBytes)
        val key = objectKey(PROFILE_FILENAME)
        val url = absoluteUrl(key)
        val headers = mutableMapOf("Content-MD5" to md5Base64)
        val signedReq = signRequest("PUT", url, bodyBytes, headers)
        client.newCall(signedReq).execute().use { resp ->
            if (!resp.isSuccessful) {
                Timber.tag(TAG).w("putClipboard failed: ${resp.code}")
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
            val key = objectKey("$FILE_FOLDER/$fileName")
            val url = absoluteUrl(key)
            val signedReq = signRequest("GET", url, UNSIGNED_PAYLOAD.toByteArray(), emptyMap())
            client.newCall(signedReq).execute().use { resp ->
                if (!resp.isSuccessful) {
                    Timber.tag(TAG).w("downloadFile failed: ${resp.code}")
                    return false to null
                }
                val totalBytes = resp.body.contentLength()
                val localPath = fileManager.saveFile(
                    fileName,
                    resp.body.byteStream(),
                    totalBytes,
                    progressCallback,
                )
                if (localPath != null) true to localPath else false to null
            }
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
            val fileBytes = file.readBytes()
            val md5Base64 = md5Base64(fileBytes)
            val key = objectKey("$FILE_FOLDER/$fileName")
            val url = absoluteUrl(key)
            val headers = mutableMapOf("Content-MD5" to md5Base64)
            val signedReq = signRequest("PUT", url, fileBytes, headers)
            client.newCall(signedReq).execute().use { resp ->
                if (!resp.isSuccessful) {
                    Timber.tag(TAG).w("putFile failed: ${resp.code}")
                }
            }
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
        val key = objectKey("")
        val url = absoluteUrl("")
        val queryUrl = if (url.endsWith("/")) "$url?list-type=2&max-keys=1" else "$url?list-type=2&max-keys=1"
        val signedReq = signRequest("GET", queryUrl, UNSIGNED_PAYLOAD.toByteArray(), emptyMap())
        client.newCall(signedReq).execute().use { resp ->
            ServerInfo(
                version = "s3",
                serverTime = resp.header("Date")?.let { parseDateHeader(it) } ?: 0L,
            )
        }
    } catch (e: Throwable) {
        Timber.tag(TAG).e(e, "getServerInfo failed")
        ServerInfo()
    }

    override suspend fun testConnection() {
        getServerInfo()
    }

    private suspend fun ensureFileFolderExists() {
        try {
            val key = objectKey("$FILE_FOLDER/")
            val url = absoluteUrl(key)
            val signedReq = signRequest("HEAD", url, UNSIGNED_PAYLOAD.toByteArray(), emptyMap())
            client.newCall(signedReq).execute().use { resp ->
                if (resp.code == 404) {
                    val putUrl = absoluteUrl(key)
                    val putReq = signRequest(
                        "PUT",
                        putUrl,
                        ByteArray(0),
                        mapOf("Content-Type" to "application/x-directory"),
                    )
                    client.newCall(putReq).execute().use { }
                }
            }
        } catch (e: Throwable) {
            Timber.tag(TAG).e(e, "ensureFileFolderExists failed")
        }
    }

    private fun signRequest(
        method: String,
        url: String,
        payload: ByteArray,
        extraHeaders: Map<String, String>,
    ): Request {
        val uri = java.net.URI(url)
        val host = uri.host ?: buildEndpoint()
        val path = uri.rawPath.ifEmpty { "/" }
        val query = uri.rawQuery ?: ""

        val amzDate = SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date())
        val dateStamp = amzDate.substring(0, 8)

        val contentType = extraHeaders["Content-Type"] ?: "application/json; charset=utf-8"
        val contentMd5 = extraHeaders["Content-MD5"]

        val payloadHash = if (payload.isEmpty()) {
            HashUtils.sha256HexLower("")
        } else {
            HashUtils.sha256HexLower(String(payload, Charsets.UTF_8))
        }

        val signedHeadersList = mutableListOf("host", "x-amz-content-sha256", "x-amz-date")
        if (contentMd5 != null) signedHeadersList.add("content-md5")
        signedHeadersList.add("content-type")
        val signedHeaders = signedHeadersList.joinToString(";")

        val canonicalHeaders = buildString {
            append("host:$host\n")
            append("x-amz-content-sha256:UNSIGNED-PAYLOAD\n")
            append("x-amz-date:$amzDate\n")
            if (contentMd5 != null) append("content-md5:$contentMd5\n")
            append("content-type:$contentType\n")
        }

        val canonicalRequest = buildString {
            append("$method\n")
            append("${uri.rawPath.ifEmpty { "/" }}\n")
            append("$query\n")
            append(canonicalHeaders)
            append("\n")
            append("$signedHeaders\n")
            append(UNSIGNED_PAYLOAD)
        }

        val credentialScope = "$dateStamp/$regionVal/$AWS_SERVICE/$AWS_REQUEST"
        val stringToSign = buildString {
            append("$AWS_HMAC_SHA256\n")
            append("$amzDate\n")
            append("$credentialScope\n")
            append(HashUtils.sha256HexLower(canonicalRequest))
        }

        val signingKey = getSignatureKey(secretAccessKey, dateStamp, regionVal, AWS_SERVICE)
        val signature = hmacSha256(signingKey, stringToSign.toByteArray(Charsets.UTF_8))
        val authHeader = "$AWS_HMAC_SHA256 Credential=$accessKeyId/$credentialScope, SignedHeaders=$signedHeaders, Signature=$signature"

        val builder = Request.Builder().url("$url").header("Authorization", authHeader)
        builder.header("x-amz-content-sha256", UNSIGNED_PAYLOAD)
        builder.header("x-amz-date", amzDate)
        builder.header("Host", host)
        if (contentMd5 != null) builder.header("Content-MD5", contentMd5)

        when (method.uppercase()) {
            "GET" -> builder.get()
            "PUT" -> {
                builder.header("Content-Type", contentType)
                if (payload.isNotEmpty()) {
                    builder.put(payload.toRequestBody(contentType.toMediaType()))
                } else {
                    builder.put(ByteArray(0).toRequestBody(contentType.toMediaType()))
                }
            }
            "HEAD" -> builder.head()
            "DELETE" -> builder.delete()
            else -> builder.method(method, null)
        }

        return builder.build()
    }

    private fun getSignatureKey(
        secret: String,
        date: String,
        region: String,
        service: String,
    ): ByteArray {
        val kSecret = "AWS4$secret".toByteArray(Charsets.UTF_8)
        val kDate = hmacSha256Raw(kSecret, date.toByteArray(Charsets.UTF_8))
        val kRegion = hmacSha256Raw(kDate, region.toByteArray(Charsets.UTF_8))
        val kService = hmacSha256Raw(kRegion, service.toByteArray(Charsets.UTF_8))
        return hmacSha256Raw(kService, AWS_REQUEST.toByteArray(Charsets.UTF_8))
    }

    private fun hmacSha256(key: ByteArray, data: ByteArray): String {
        val mac = Mac.getInstance("HmacSHA256")
        val secretKey = SecretKeySpec(key, "HmacSHA256")
        mac.init(secretKey)
        val hash = mac.doFinal(data)
        val sb = StringBuilder(hash.size * 2)
        for (b in hash) sb.append(String.format("%02x", b))
        return sb.toString()
    }

    private fun hmacSha256Raw(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        val secretKey = SecretKeySpec(key, "HmacSHA256")
        mac.init(secretKey)
        return mac.doFinal(data)
    }

    private fun md5Base64(bytes: ByteArray): String {
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(bytes)
        return Base64.encodeToString(digest, Base64.NO_WRAP)
    }

    private fun parseDateHeader(dateHeader: String): Long {
        return try {
            val cleaned = dateHeader.trim()
            if (cleaned.isEmpty()) return 0L
            SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US).run {
                parse(cleaned)?.time ?: 0L
            }
        } catch (e: Throwable) {
            0L
        }
    }
}
