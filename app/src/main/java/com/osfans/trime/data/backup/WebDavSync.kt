/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.backup

import com.osfans.trime.data.base.DataManager
import com.osfans.trime.data.prefs.AppPrefs
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.w3c.dom.Element
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import javax.xml.parsers.DocumentBuilderFactory

object WebDavSync {
    private val prefs get() = AppPrefs.defaultInstance().profile

    private val client: OkHttpClient by lazy {
        val trustAllCerts = arrayOf<TrustManager>(
            object : X509TrustManager {
                override fun checkClientTrusted(
                    chain: Array<X509Certificate>,
                    authType: String,
                ) {
                }

                override fun checkServerTrusted(
                    chain: Array<X509Certificate>,
                    authType: String,
                ) {
                }

                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            },
        )
        val sslContext = SSLContext.getInstance("TLS").apply {
            init(null, trustAllCerts, SecureRandom())
        }
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
            .hostnameVerifier { _, _ -> true }
            .addInterceptor { chain ->
                val authHeader = Credentials.basic(
                    prefs.webdavUsername.getValue(),
                    prefs.webdavPassword.getValue(),
                )
                chain.proceed(chain.request().newBuilder().header("Authorization", authHeader).build())
            }
            .build()
    }

    private fun buildBaseUrl(): String {
        val url = prefs.webdavUrl.getValue().trimEnd('/')
        if (url.isEmpty()) throw IllegalStateException("WebDAV URL not configured")
        return "$url/"
    }

    private fun joinUrl(
        base: String,
        path: String,
    ): String = "$base$path"

    private fun newRequest(
        url: String,
        method: String,
    ): Request.Builder = Request.Builder().url(url).method(method, null)

    fun testConnection(): Result<String> = runCatching {
        val baseUrl = buildBaseUrl()
        var lastError: String? = null

        for (method in listOf("PROPFIND", "OPTIONS", "GET")) {
            try {
                val request = newRequest(baseUrl, method).apply {
                    if (method == "PROPFIND") {
                        header("Depth", "0")
                    }
                }.build()
                client.newCall(request).execute().use { response ->
                    return@runCatching "OK (HTTP ${response.code})"
                }
            } catch (e: Exception) {
                lastError = e.message
            }
        }
        throw Exception(lastError ?: "Unknown error")
    }

    fun pushToServer(): Result<Int> = runCatching {
        val baseUrl = buildBaseUrl()
        val syncDir = getSyncDir()
        if (!syncDir.exists() || !syncDir.isDirectory) {
            throw Exception("Sync directory not found")
        }

        val remoteBase = joinUrl(baseUrl, remoteSyncPath())
        ensureRemoteDir(remoteBase)

        var count = 0
        syncDir.walkTopDown().filter { it.isFile }.forEach { file ->
            val relativePath = file.relativeTo(syncDir).path.replace(File.separatorChar, '/')
            val remoteUrl = joinUrl(remoteBase, relativePath)
            try {
                uploadFile(file, remoteUrl)
                count++
            } catch (e: Exception) {
                Timber.e(e, "Failed to upload: $remoteUrl")
            }
        }

        count
    }

    fun pullFromServer(): Result<Int> = runCatching {
        val baseUrl = buildBaseUrl()
        val syncDir = getSyncDir()
        syncDir.mkdirs()

        val remoteBase = joinUrl(baseUrl, remoteSyncPath())

        var count = 0
        downloadDir(remoteBase, syncDir) { count++ }
        count
    }

    private fun ensureRemoteDir(url: String) {
        val baseUrl = buildBaseUrl()
        if (url == baseUrl || !url.startsWith(baseUrl)) return

        val relative = url.removePrefix(baseUrl).trim('/')
        if (relative.isEmpty()) return

        var current = baseUrl
        for (part in relative.split('/')) {
            if (part.isEmpty()) continue
            current = joinUrl(current, "$part/")
            if (!probeDir(current)) {
                createDir(current)
            }
        }
    }

    private fun probeDir(url: String): Boolean {
        try {
            val request = newRequest(url, "PROPFIND").apply {
                header("Depth", "0")
            }.build()
            return client.newCall(request).execute().use { it.isSuccessful }
        } catch (e: Exception) {
            Timber.d("PROPFIND not supported for $url")
            return false
        }
    }

    private fun createDir(url: String): Boolean {
        if (tryRequest("MKCOL", url)) return true
        Timber.w("Failed to create directory: $url")
        return false
    }

    private fun tryRequest(
        method: String,
        url: String,
    ): Boolean {
        try {
            val request = newRequest(url, method).build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Timber.d("$method $url -> HTTP ${response.code}")
                    return true
                }
                Timber.d("$method $url -> HTTP ${response.code}")
            }
        } catch (e: Exception) {
            Timber.d("$method $url failed: ${e.message}")
        }
        return false
    }

    private fun downloadDir(
        url: String,
        localDir: File,
        onFile: () -> Unit,
    ) {
        try {
            val request = newRequest(url, "PROPFIND").apply {
                header("Depth", "1")
            }.build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val xml = response.body?.string() ?: return
                    parsePropfind(xml).forEach { item ->
                        if (item.isDirectory) {
                            if (item.href != url.substringAfterLast('/') &&
                                item.href.endsWith("/")
                            ) {
                                val subDir = localDir.resolve(item.name)
                                subDir.mkdirs()
                                downloadDir(joinUrl(url, item.name + "/"), subDir, onFile)
                            }
                        } else {
                            val localFile = localDir.resolve(item.name)
                            downloadFile(joinUrl(url, item.name), localFile)
                            onFile()
                        }
                    }
                } else {
                    Timber.e("PROPFIND failed: HTTP ${response.code} for $url")
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "PROPFIND not supported for $url, skipping directory listing")
        }
    }

    private fun parsePropfind(xml: String): List<DavItem> {
        val items = mutableListOf<DavItem>()
        try {
            val factory = DocumentBuilderFactory.newInstance()
            factory.isNamespaceAware = true
            val builder = factory.newDocumentBuilder()
            val doc = builder.parse(xml.byteInputStream())
            val responses = doc.getElementsByTagNameNS("DAV:", "response")
            for (i in 0 until responses.length) {
                val response = responses.item(i) as Element
                val href =
                    response.getElementsByTagNameNS("DAV:", "href").item(0)?.textContent?.trimStart('/')
                        ?: continue
                val propstat = response.getElementsByTagNameNS("DAV:", "propstat").item(0) as? Element ?: continue
                val prop = propstat.getElementsByTagNameNS("DAV:", "prop").item(0) as? Element ?: continue
                val isCollection = prop.getElementsByTagNameNS("DAV:", "resourcetype").item(0)
                    ?.let { (it as? Element)?.getElementsByTagNameNS("DAV:", "collection")?.length ?: 0 > 0 } ?: false

                val name = href.substringAfterLast('/').ifEmpty { href }
                if (name.isNotEmpty() && name != href) {
                    items.add(DavItem(name, href, isCollection))
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to parse PROPFIND response")
        }
        return items
    }

    private fun uploadFile(
        localFile: File,
        remoteUrl: String,
    ) {
        val requestBody = localFile.readBytes().toRequestBody("application/octet-stream".toMediaType())
        val request = Request.Builder().url(remoteUrl).put(requestBody).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("Upload failed: HTTP ${response.code} for $remoteUrl")
            }
            Timber.i("Uploaded: $remoteUrl")
        }
    }

    private fun downloadFile(
        remoteUrl: String,
        localFile: File,
    ) {
        val request = Request.Builder().url(remoteUrl).get().build()
        client.newCall(request).execute().use { response ->
            if (response.isSuccessful) {
                localFile.parentFile?.mkdirs()
                FileOutputStream(localFile).use { output ->
                    response.body?.byteStream()?.copyTo(output)
                }
                Timber.i("Downloaded: $remoteUrl -> $localFile")
            } else {
                Timber.e("Download failed: HTTP ${response.code} for $remoteUrl")
            }
        }
    }

    private fun getSyncDir(): File {
        val installationId = readInstallationId()
        return File(DataManager.userDataDir, "sync").resolve(installationId)
    }

    private fun remoteSyncPath(): String {
        val folder = prefs.webdavRemotePath.getValue().trimEnd('/')
        return "${folder.ifEmpty { "Trime" }}/${readInstallationId()}/"
    }

    private fun readInstallationId(): String {
        val file = File(DataManager.userDataDir, "installation.yaml")
        if (file.exists()) {
            val content = file.readText()
            Regex("""installation_id:\s*["']?([^"'\n\r]+)""").find(content)
                ?.groupValues?.get(1)?.trim()?.let { return it }
        }
        return "unknown"
    }

    private data class DavItem(
        val name: String,
        val href: String,
        val isDirectory: Boolean,
    )
}
