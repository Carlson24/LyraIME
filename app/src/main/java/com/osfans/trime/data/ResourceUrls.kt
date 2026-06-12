/*
 * SPDX-FileCopyrightText: 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.osfans.trime.data

object ResourceUrls {
    // ---- Wanxiang API ----
    const val WANXIANG_API_LATEST_RELEASE =
        "https://api.github.com/repos/amzxyz/rime-wanxiang/releases/latest"
    const val WANXIANG_API_RIME_LMDG_TAGS_LTS =
        "https://api.github.com/repos/amzxyz/RIME-LMDG/releases/tags/LTS"

    // ---- Wanxiang CNB downloads ----
    const val WANXIANG_CNB_RELEASES_BASE =
        "https://cnb.cool/amzxyz/rime-wanxiang/-/releases/download"
    const val WANXIANG_CNB_DICTS_BASE =
        "https://cnb.cool/amzxyz/rime-wanxiang/-/releases/download/v1.0.0"
    const val WANXIANG_CNB_MODEL =
        "https://cnb.cool/amzxyz/rime-wanxiang/-/releases/download/model/wanxiang-lts-zh-hans.gram"

    // ---- Wanxiang GitHub downloads ----
    const val WANXIANG_GITHUB_RELEASES_BASE =
        "https://github.com/amzxyz/rime-wanxiang/releases/download"
    const val WANXIANG_GITHUB_DICTS_BASE =
        "https://github.com/amzxyz/rime-wanxiang/releases/download/dict-nightly"
    const val WANXIANG_GITHUB_MODEL =
        "https://github.com/amzxyz/RIME-LMDG/releases/download/LTS/wanxiang-lts-zh-hans.gram"

    // ---- Voice Model ----
    const val VOICE_MODEL_DOWNLOAD =
        "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-x-asr-480ms-streaming-zipformer-transducer-zh-en-punct-2026-06-05.tar.bz2"
    const val VOICE_MODEL_SHA256 =
        "67ad368298674eac2aed66676632be2672c05807f95bae1d66f5d04813f34a99"
    const val VOICE_MODEL_INT8_DOWNLOAD =
        "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-x-asr-480ms-streaming-zipformer-transducer-zh-en-punct-int8-2026-06-05.tar.bz2"
    const val VOICE_MODEL_INT8_SHA256 =
        "7f19daf70818a9727cce21f27c577d89522aebab0e7025be5594ef93a46d41f3"
}
