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
        "https://github.com/BryceWG/BiBi-Keyboard/releases/download/models/sherpa-onnx-streaming-x-asr-480ms-zh-en.zip"
    const val VOICE_MODEL_SHA256 =
        "56b5be59a57aa7893b98040690c87238f026cb1c9cd58db98547be9ecd43ee87"
}
