// SPDX-FileCopyrightText: 2015 - 2025 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.data.clipboard.sync

import android.util.Base64
import com.osfans.trime.data.clipboard.ClipboardFileManager
import timber.log.Timber

object BackendFactory {

    private const val TAG = "BackendFactory"

    private var cachedBackend: ISyncClipboardAPI? = null
    private var cachedConfigKey: String? = null

    fun createBackend(config: ServerConfig, fileManager: ClipboardFileManager): ISyncClipboardAPI? {
        val configKey = buildConfigKey(config)
        if (cachedBackend != null && cachedConfigKey == configKey) {
            return cachedBackend
        }

        cachedConfigKey = configKey

        val backend = when (config.type) {
            ServerType.SYNC_CLIPBOARD -> {
                val authHeader = buildBasicAuthHeader(config.username, config.password)
                if (authHeader == null) {
                    Timber.tag(TAG).w("SyncClipboard server requires username and password")
                    null
                } else {
                    SyncClipboardServerBackend(config.url, authHeader, fileManager)
                }
            }

            ServerType.WEBDAV -> {
                val authHeader = buildBasicAuthHeader(config.username, config.password)
                if (authHeader == null) {
                    Timber.tag(TAG).w("WebDAV server requires username and password")
                    null
                } else {
                    WebDAVBackend(config.url, authHeader, config.remotePath, fileManager)
                }
            }

            ServerType.S3 -> {
                val region = config.region ?: "us-east-1"
                val bucketName = config.bucketName ?: return null.also {
                    Timber.tag(TAG).w("S3 requires bucket name")
                }
                val accessKeyId = config.username ?: return null.also {
                    Timber.tag(TAG).w("S3 requires access key ID (username)")
                }
                val secretAccessKey = config.password ?: return null.also {
                    Timber.tag(TAG).w("S3 requires secret access key (password)")
                }
                S3Backend(
                    serviceUrl = config.url.takeIf { it.isNotBlank() },
                    region = region,
                    bucketName = bucketName,
                    objectPrefix = config.objectPrefix,
                    forcePathStyle = config.forcePathStyle ?: false,
                    accessKeyId = accessKeyId,
                    secretAccessKey = secretAccessKey,
                    fileManager = fileManager,
                )
            }
        }
        cachedBackend = backend
        return backend
    }

    fun clearCache() {
        cachedBackend = null
        cachedConfigKey = null
    }

    private fun buildBasicAuthHeader(username: String?, password: String?): String? {
        if (username.isNullOrBlank() || password.isNullOrBlank()) return null
        val token = "$username:$password".toByteArray(Charsets.UTF_8)
        val b64 = Base64.encodeToString(token, Base64.NO_WRAP)
        return "Basic $b64"
    }

    private fun buildConfigKey(config: ServerConfig): String {
        val parts = listOfNotNull(
            config.type.name,
            config.url,
            config.username?.takeIf { it.isNotEmpty() },
            config.password?.takeIf { it.isNotEmpty() }?.let { "****" },
            config.region,
            config.bucketName,
            config.objectPrefix,
            config.forcePathStyle?.toString(),
            config.remotePath,
        )
        return parts.joinToString("|")
    }
}
