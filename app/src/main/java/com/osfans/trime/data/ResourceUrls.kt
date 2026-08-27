/*
 * SPDX-FileCopyrightText: 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.osfans.trime.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap

object ResourceUrls {
    const val USER_AGENT = "Mozilla/5.0"

    // ---- Voice Model (QNN-only, punctuation always enabled) ----
    const val VOICE_MODEL_QNN_RELEASE_BASE =
        "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models-qnn"

    fun buildQnnVoiceModelUrl(chunkMs: Int): String =
        "$VOICE_MODEL_QNN_RELEASE_BASE/sherpa-onnx-qnn-x-asr-streaming-zipformer-transducer-zh-en-punct-2026-06-05-chunk-size-${chunkMs}ms-android-aarch64.tar.bz2"

    // ---- GitHub Release API URLs ----
    private const val RELEASE_API_K2FSA_SHERPA_ONNX =
        "https://api.github.com/repos/k2-fsa/sherpa-onnx/releases/tags"

    const val VOICE_MODEL_QNN_RELEASE_API =
        "$RELEASE_API_K2FSA_SHERPA_ONNX/asr-models-qnn"

    // ---- SHA256 cache (fetched from GitHub release API) ----
    object GitHubAssetCache {
        private val cache = ConcurrentHashMap<String, String>()
        private val client = OkHttpClient()

        private suspend fun fetchReleaseAssets(releaseApiUrl: String) {
            val request = Request.Builder()
                .url(releaseApiUrl)
                .header("User-Agent", USER_AGENT)
                .get()
                .build()
            val content = withContext(Dispatchers.IO) {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Timber.w("GitHubAssetCache: fetch failed ${response.code}")
                        return@withContext null
                    }
                    response.body.string()
                }
            } ?: return
            val json = JSONObject(content)
            val assets = json.optJSONArray("assets") ?: return
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                val url = asset.optString("browser_download_url", "")
                val digest = asset.optString("digest", "")
                if (url.isNotEmpty() && digest.startsWith("sha256:")) {
                    cache[url] = digest.removePrefix("sha256:")
                }
            }
        }

        suspend fun getSha256(downloadUrl: String, releaseApiUrl: String): String? {
            cache[downloadUrl]?.let { return it }
            fetchReleaseAssets(releaseApiUrl)
            return cache[downloadUrl]
        }

        suspend fun getAllKnownSha256s(vararg releaseApiUrls: String): List<String> {
            for (apiUrl in releaseApiUrls) {
                fetchReleaseAssets(apiUrl)
            }
            return cache.values.toList()
        }

        fun isCached(downloadUrl: String): Boolean = cache.containsKey(downloadUrl)
    }
}
