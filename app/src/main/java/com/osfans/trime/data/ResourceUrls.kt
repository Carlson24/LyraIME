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

    // ---- Wanxiang GitHub API ----
    const val WANXIANG_GH_API_SCHEMA =
        "https://api.github.com/repos/amzxyz/rime-wanxiang/releases/latest"
    const val WANXIANG_GH_API_DICT =
        "https://api.github.com/repos/amzxyz/rime-wanxiang/releases/tags/dict-nightly"
    const val WANXIANG_GH_API_MODEL =
        "https://api.github.com/repos/amzxyz/RIME-LMDG/releases/tags/LTS"

    // ---- Wanxiang CNB API ----
    const val WANXIANG_CNB_API_SCHEMA =
        "https://api.cnb.cool/amzxyz/rime-wanxiang/-/releases/latest"
    const val WANXIANG_CNB_API_DICT =
        "https://api.cnb.cool/amzxyz/rime-wanxiang/-/releases/tags/refs/tags/v1.0.0"
    const val WANXIANG_CNB_API_MODEL =
        "https://api.cnb.cool/amzxyz/rime-wanxiang/-/releases/tags/refs/tags/model"

    data class ReleaseAssetInfo(
        val name: String,
        val downloadUrl: String,
        val sha256: String,
        val releaseUpdatedAt: String,
    )

    fun schemaApi(source: String) = if (source == "CNB") WANXIANG_CNB_API_SCHEMA else WANXIANG_GH_API_SCHEMA
    fun dictApi(source: String) = if (source == "CNB") WANXIANG_CNB_API_DICT else WANXIANG_GH_API_DICT
    fun modelApi(source: String) = if (source == "CNB") WANXIANG_CNB_API_MODEL else WANXIANG_GH_API_MODEL

    suspend fun fetchReleaseJson(
        apiUrl: String,
        token: String,
        client: OkHttpClient,
    ): JSONObject? {
        return withContext(Dispatchers.IO) {
            try {
                val req = Request.Builder()
                    .url(apiUrl)
                    .header("Accept", "application/json")
                    .header("User-Agent", USER_AGENT)
                    .apply { if (token.isNotEmpty()) header("Authorization", "Bearer $token") }
                    .get()
                    .build()
                client.newCall(req).execute().use { response ->
                    if (!response.isSuccessful) return@withContext null
                    JSONObject(response.body.string())
                }
            } catch (_: Exception) {
                null
            }
        }
    }

    fun findAssetByName(
        json: JSONObject,
        namePattern: String,
        source: String,
    ): ReleaseAssetInfo? {
        val assets = json.optJSONArray("assets") ?: return null
        for (i in 0 until assets.length()) {
            val asset = assets.getJSONObject(i)
            val name = asset.optString("name", "")
            if (name == namePattern) {
                val downloadUrl = asset.optString("browser_download_url", "")
                if (downloadUrl.isEmpty()) continue
                val sha256 = if (source == "CNB") {
                    asset.optString("hash_value", "")
                } else {
                    asset.optString("digest", "").removePrefix("sha256:")
                }
                val updatedAt = asset.optString("updated_at", "")
                return ReleaseAssetInfo(name, downloadUrl, sha256, updatedAt)
            }
        }
        return null
    }

    fun parseAllAssetsSha256(
        json: JSONObject,
        source: String,
    ): Map<String, String> {
        val assets = json.optJSONArray("assets") ?: return emptyMap()
        val result = mutableMapOf<String, String>()
        for (i in 0 until assets.length()) {
            val asset = assets.getJSONObject(i)
            val name = asset.optString("name", "")
            if (name.isEmpty()) continue
            val sha256 = if (source == "CNB") {
                val hv = asset.optString("hash_value", "")
                if (hv.isNotEmpty()) hv else continue
            } else {
                val digest = asset.optString("digest", "")
                if (digest.isNotEmpty()) digest.removePrefix("sha256:") else continue
            }
            result[name] = sha256
        }
        return result
    }

    // ---- Voice Model ----
    const val VOICE_MODEL_RELEASE_BASE =
        "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models"
    const val VOICE_MODEL_QNN_RELEASE_BASE =
        "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models-qnn-binary"

    fun buildVoiceModelUrl(
        chunkMs: Int,
        punct: Boolean,
        variant: VoiceModelVariant,
    ): String {
        val punctSegment = if (punct) "-punct" else ""
        return when (variant) {
            VoiceModelVariant.INT8 ->
                "$VOICE_MODEL_RELEASE_BASE/sherpa-onnx-x-asr-${chunkMs}ms-streaming-zipformer-transducer-zh-en$punctSegment-int8-2026-06-05.tar.bz2"
            else ->
                "$VOICE_MODEL_RELEASE_BASE/sherpa-onnx-x-asr-${chunkMs}ms-streaming-zipformer-transducer-zh-en$punctSegment-2026-06-05.tar.bz2"
        }
    }

    fun buildQnnVoiceModelUrl(
        soc: String,
        chunkMs: Int,
        punct: Boolean,
    ): String {
        val punctSegment = if (punct) "-punct" else ""
        return "$VOICE_MODEL_QNN_RELEASE_BASE/sherpa-onnx-qnn-$soc-binary-x-asr-streaming-zipformer-transducer-zh-en$punctSegment-2026-06-05-chunk-size-${chunkMs}ms.tar.bz2"
    }

    enum class VoiceModelVariant {
        STANDARD,
        INT8,
        QNN,
    }

    // ---- QNN DSP Libraries (per SoC) ----
    // https://github.com/Carlson24/LyraIME/releases/tag/libVoiceRuntime
    data class QnnDspEntry(val url: String)

    val QNN_DSP_MAP: Map<String, QnnDspEntry> = mapOf(
        "SM8350" to QnnDspEntry(
            "https://github.com/Carlson24/LyraIME/releases/download/libVoiceRuntime/libQnnHtpV68.tar.bz2",
        ),
        "SM8450" to QnnDspEntry(
            "https://github.com/Carlson24/LyraIME/releases/download/libVoiceRuntime/libQnnHtpV69.tar.bz2",
        ),
        "SM8475" to QnnDspEntry(
            "https://github.com/Carlson24/LyraIME/releases/download/libVoiceRuntime/libQnnHtpV69.tar.bz2",
        ),
        "SM8550" to QnnDspEntry(
            "https://github.com/Carlson24/LyraIME/releases/download/libVoiceRuntime/libQnnHtpV73.tar.bz2",
        ),
        "SM8650" to QnnDspEntry(
            "https://github.com/Carlson24/LyraIME/releases/download/libVoiceRuntime/libQnnHtpV75.tar.bz2",
        ),
        "SM8750" to QnnDspEntry(
            "https://github.com/Carlson24/LyraIME/releases/download/libVoiceRuntime/libQnnHtpV79.tar.bz2",
        ),
        "SM8850" to QnnDspEntry(
            "https://github.com/Carlson24/LyraIME/releases/download/libVoiceRuntime/libQnnHtpV81.tar.bz2",
        ),
    )

    // ---- Voice Model QNN Binary (per SOC) ----
    // https://github.com/k2-fsa/sherpa-onnx/releases/tag/asr-models-qnn-binary
    val QNN_SOC_SET: Set<String> = setOf(
        "SM8450",
        "SM8475",
        "SM8550",
        "SM8650",
        "SM8750",
        "SM8850",
    )

    fun resolveQnnSoc(): String {
        val soc = android.os.Build.SOC_MODEL
        return if (soc in QNN_SOC_SET) soc else "SM8850"
    }

    // ---- GitHub Release API URLs ----
    private const val RELEASE_API_K2FSA_SHERPA_ONNX =
        "https://api.github.com/repos/k2-fsa/sherpa-onnx/releases/tags"
    private const val RELEASE_API_LYRAIME =
        "https://api.github.com/repos/Carlson24/LyraIME/releases/tags"

    const val VOICE_MODEL_RELEASE_API =
        "$RELEASE_API_K2FSA_SHERPA_ONNX/asr-models"
    const val VOICE_MODEL_QNN_RELEASE_API =
        "$RELEASE_API_K2FSA_SHERPA_ONNX/asr-models-qnn-binary"
    const val QNN_DSP_RELEASE_API =
        "$RELEASE_API_LYRAIME/libVoiceRuntime"

    const val ONNX_RUNTIME_URL =
        "https://github.com/Carlson24/LyraIME/releases/download/libVoiceRuntime/libonnxruntime.tar.bz2"

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
