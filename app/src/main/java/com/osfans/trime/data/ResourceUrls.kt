/*
 * SPDX-FileCopyrightText: 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.osfans.trime.data

object ResourceUrls {
    const val USER_AGENT = "Mozilla/5.0"

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

    // ---- QNN DSP Libraries (per SoC) ----
    // https://github.com/Carlson24/LyraIME/releases/tag/libQnnHtp
    data class QnnDspEntry(val url: String, val sha256: String)

    val QNN_DSP_MAP: Map<String, QnnDspEntry> = mapOf(
        "SM8350" to QnnDspEntry(
            "https://github.com/Carlson24/LyraIME/releases/download/libQnnHtp/libQnnHtpV68S.tar.gz",
            "66b5c438462207ac9ee2a9b44f241f84da50bad4e60d96ac4210eeb09ebcdaba",
        ),
        "SM8450" to QnnDspEntry(
            "https://github.com/Carlson24/LyraIME/releases/download/libQnnHtp/libQnnHtpV69S.tar.gz",
            "1e44c873a03ad56e45c303d6992377df9c50ce07811e6aa2df62ab2e990769dc",
        ),
        "SM8475" to QnnDspEntry(
            "https://github.com/Carlson24/LyraIME/releases/download/libQnnHtp/libQnnHtpV69S.tar.gz",
            "1e44c873a03ad56e45c303d6992377df9c50ce07811e6aa2df62ab2e990769dc",
        ),
        "SM8550" to QnnDspEntry(
            "https://github.com/Carlson24/LyraIME/releases/download/libQnnHtp/libQnnHtpV73S.tar.gz",
            "77d7f86ef12fda071759b04518b5de39574cb99b792fea2e4ca4f431b5d00d73",
        ),
        "SM8650" to QnnDspEntry(
            "https://github.com/Carlson24/LyraIME/releases/download/libQnnHtp/libQnnHtpV75S.tar.gz",
            "790be5ce570db420784f3fabf1381fbe68b12bd7ba4a646702dc34f0fb1ac2f4",
        ),
        "SM8750" to QnnDspEntry(
            "https://github.com/Carlson24/LyraIME/releases/download/libQnnHtp/libQnnHtpV79S.tar.gz",
            "38a54d8dab7ec6f7bfaf4d939c3c408541ec7f798eaf5c8d7b70ea66599ac2d1",
        ),
        "SM8850" to QnnDspEntry(
            "https://github.com/Carlson24/LyraIME/releases/download/libQnnHtp/libQnnHtpV81S.tar.gz",
            "bae491e349b64e73e6f04e6fb4b03bf01f1e345eaf97eb901921c21822cbd5b4",
        ),
    )

    // ---- Voice Model QNN Binary (per SOC) ----
    // https://github.com/k2-fsa/sherpa-onnx/releases/tag/asr-models-qnn-binary
    data class QnnModelEntry(val url: String, val sha256: String)
    val VOICE_MODEL_QNN_MAP: Map<String, QnnModelEntry> = mapOf(
        "SM8450" to QnnModelEntry(
            "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models-qnn-binary/sherpa-onnx-qnn-SM8450-binary-x-asr-streaming-zipformer-transducer-zh-en-punct-2026-06-05-chunk-size-480ms.tar.bz2",
            "3b88994a66831801767b9889a1f0e4f45a86d87480f5517e0f005b1e2e5e12bc",
        ),
        "SM8475" to QnnModelEntry(
            "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models-qnn-binary/sherpa-onnx-qnn-SM8475-binary-x-asr-streaming-zipformer-transducer-zh-en-punct-2026-06-05-chunk-size-480ms.tar.bz2",
            "7fe97f63e3ffe3f6ba578f38f76bf8a0aa1848d00e9fad13bd3d21e76f50614e",
        ),
        "SM8550" to QnnModelEntry(
            "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models-qnn-binary/sherpa-onnx-qnn-SM8550-binary-x-asr-streaming-zipformer-transducer-zh-en-punct-2026-06-05-chunk-size-480ms.tar.bz2",
            "4165b1821b379ec175bb59b65de254faf56f4b27fd88bade50ed1e5bcb24c473",
        ),
        "SM8650" to QnnModelEntry(
            "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models-qnn-binary/sherpa-onnx-qnn-SM8650-binary-x-asr-streaming-zipformer-transducer-zh-en-punct-2026-06-05-chunk-size-480ms.tar.bz2",
            "722c165095b6fee042b4fc45afe7617a208e552d7f9d9c7c5e5242d8b339ae2f",
        ),
        "SM8750" to QnnModelEntry(
            "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models-qnn-binary/sherpa-onnx-qnn-SM8750-binary-x-asr-streaming-zipformer-transducer-zh-en-punct-2026-06-05-chunk-size-480ms.tar.bz2",
            "2b9817775dd92ea6b66afa058fd6f001d71065855f3a70c19d951b48dc80f1c2",
        ),
        "SM8850" to QnnModelEntry(
            "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models-qnn-binary/sherpa-onnx-qnn-SM8850-binary-x-asr-streaming-zipformer-transducer-zh-en-punct-2026-06-05-chunk-size-480ms.tar.bz2",
            "72627a9e8daf4a6bf60acadc054a436b0e6f90b71027366154ab17709c26cbcb",
        ),
    )
}
