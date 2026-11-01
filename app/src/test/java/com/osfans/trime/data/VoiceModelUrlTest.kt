/*
 * SPDX-FileCopyrightText: 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class VoiceModelUrlTest :
    StringSpec({
        "QNN voice model URL points at the qnn release with the requested chunk size" {
            val expected =
                "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models-qnn/" +
                    "sherpa-onnx-qnn-x-asr-streaming-zipformer-transducer-zh-en-punct-2026-06-05-" +
                    "chunk-size-480ms-android-aarch64.tar.bz2"
            ResourceUrls.buildQnnVoiceModelUrl(480) shouldBe expected
        }

        "CPU voice model URL points at the fp32 X-ASR punct archive" {
            val expected =
                "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/" +
                    "sherpa-onnx-x-asr-960ms-streaming-zipformer-transducer-zh-en-punct-2026-06-05.tar.bz2"
            ResourceUrls.buildCpuVoiceModelUrl(960) shouldBe expected
        }
    })
