/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
import com.android.build.api.dsl.ApplicationExtension
import com.android.build.gradle.tasks.ExternalNativeBuildTask
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.Delete
import org.gradle.kotlin.dsl.register
import java.io.File

open class NativeBaseConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        target.pluginManager.apply("com.android.application")
        target.extensions.configure(ApplicationExtension::class.java) {
            ndkVersion = target.ndkVersion
            defaultConfig {
                @Suppress("UnstableApiUsage")
                externalNativeBuild {
                    cmake {
                        val args = mutableListOf("-DANDROID_STL=c++_static")
                        if (target.qnnSdkRoot != null) {
                            args.add("-DQNN_SDK_ROOT=${target.qnnSdkRoot}")
                        }
                        arguments(*args.toTypedArray())
                    }
                }
            }

            if (target.file("prebuilt").exists()) {
                sourceSets.getByName("main").jniLibs.directories.add("prebuilt")
            } else {
                externalNativeBuild {
                    cmake {
                        version = target.cmakeVersion
                        path("src/main/jni/CMakeLists.txt")
                    }
                }
            }

            // Pre-packaged voice runtime libs (onnxruntime + QNN DSP) are staged here
            sourceSets.getByName("main").jniLibs.directories.add(
                target.layout.buildDirectory.dir("generated/voiceJniLibs").get().asFile.absolutePath,
            )

            splits.abi {
                isEnable = true
                isUniversalApk = false
                reset()
                (target.buildAbiOverride?.split(",") ?: Versions.supportedAbis).forEach {
                    include(it)
                }
            }
        }
        registerCleanCxxTask(target)
        registerPatchApplyTask(target)
        registerVoiceRuntimePackagingTask(target)
    }

    private fun registerVoiceRuntimePackagingTask(project: Project) {
        val outDir = project.file("build/generated/voiceJniLibs")
        val abis = project.buildAbiOverride?.split(",") ?: Versions.supportedAbis.toList()

        val packageTask =
            project.tasks.register("packageVoiceRuntimeLibs") {
                group = "native"
                description = "Pre-package voice runtime libraries (onnxruntime + QNN DSP) into the APK"
                outputs.dir(outDir)

                doLast {
                    if (project.file("prebuilt").exists()) {
                        project.logger.warn(
                            "app/prebuilt exists; voice runtime pre-packaging skipped " +
                                "(prebuilt/ must include the runtime .so libraries)",
                        )
                        return@doLast
                    }

                    // libonnxruntime.so — produced locally by the sherpa-onnx native build
                    for (abi in abis) {
                        val onnx =
                            project.fileTree(project.file(".cxx")).matching {
                                include("**/onnxruntime-android-*/jni/$abi/libonnxruntime.so")
                            }.firstOrNull()
                                ?: error(
                                    "libonnxruntime.so for $abi not found under .cxx. " +
                                        "Run the external native build first.",
                                )
                        val targetDir = File(outDir, abi).apply { mkdirs() }
                        val dest = File(targetDir, "libonnxruntime.so")
                        if (!dest.exists() || dest.length() != onnx.length()) {
                            onnx.copyTo(dest, overwrite = true)
                            project.logger.lifecycle("Pre-packaged libonnxruntime.so ($abi)")
                        }
                    }

                    // QNN DSP (arm64-v8a only, from the local QNN SDK), variant from qnnVariant
                    if (abis.contains("arm64-v8a")) {
                        val qnnRoot = project.qnnSdkRoot?.let { File(it) }
                        val variant = project.qnnVariant
                        if (qnnRoot != null && qnnRoot.isDirectory && variant != null) {
                            val version = variant.removePrefix("v")
                            val libs =
                                listOf(
                                    "libQnnHtp.so",
                                    "libQnnSystem.so",
                                    "libQnnHtpPrepare.so",
                                    "libQnnHtpV${version}Stub.so",
                                ).map { File(qnnRoot, "lib/aarch64-android/$it") } +
                                    File(
                                        qnnRoot,
                                        "lib/hexagon-$variant/unsigned/libQnnHtpV${version}Skel.so",
                                    )
                            val missing = libs.filterNot { it.isFile }
                            if (missing.isNotEmpty()) {
                                throw GradleException(
                                    "QNN SDK incomplete at $qnnRoot for variant $variant; missing: " +
                                        missing.joinToString { it.path },
                                )
                            }
                            val targetDir = File(outDir, "arm64-v8a").apply { mkdirs() }
                            for (src in libs) {
                                val dest = File(targetDir, src.name)
                                if (!dest.exists() || dest.length() != src.length()) {
                                    src.copyTo(dest, overwrite = true)
                                }
                            }
                            project.logger.lifecycle("Pre-packaged QNN DSP $variant libraries")
                        } else {
                            project.logger.warn(
                                "QNN_SDK_ROOT/qnnSdkRoot or QNN_VARIANT/qnnVariant not set; " +
                                    "QNN DSP libs will NOT be pre-packaged (QNN voice will be unavailable)",
                            )
                        }
                    }
                }
            }

        project.tasks.withType(ExternalNativeBuildTask::class.java).configureEach {
            // Order after the native build so the .so files exist, but do not force
            // unrelated variants to build (mustRunAfter only affects tasks in the graph).
            packageTask.get().mustRunAfter(this)
        }

        // Package the staged libs into the APK before native lib merging
        project.tasks.configureEach {
            val isMergeConsumer =
                (name.startsWith("merge") && name.endsWith("NativeLibs")) ||
                    (name.startsWith("merge") && name.endsWith("JniLibFolders"))
            if (isMergeConsumer) {
                dependsOn(packageTask)
            }
        }
    }

    private fun registerPatchApplyTask(project: Project) {
        val rootDir = project.rootDir
        val jniDir = "app/src/main/jni"
        val macrosHeader = project.file("src/main/jni/sherpa-onnx/sherpa-onnx/csrc/macros.h")
        val rimeApiH = project.file("src/main/jni/librime/src/rime_api.h")
        val luaCmake = project.file("src/main/jni/librime-plugins/librime-lua/CMakeLists.txt")
        val luaLiolib = project.file("src/main/jni/librime-plugins/librime-lua-deps/lua5.5/liolib.c")
        val applyPatches =
            project.tasks.register("applyNativePatches") {
                group = "native"
                description = "Apply patches required for native build (sherpa-onnx-qnn + librime-custom + librime-plugins)"
                doLast {
                    ProcessBuilder(
                        "git",
                        "apply",
                        "--directory=$jniDir/sherpa-onnx",
                        "patches/sherpa-onnx-qnn.patch",
                    ).directory(rootDir).inheritIO().start().waitFor()
                    ProcessBuilder(
                        "git",
                        "apply",
                        "--directory=$jniDir/librime",
                        "patches/librime-custom.patch",
                    ).directory(rootDir).inheritIO().start().waitFor()
                    ProcessBuilder(
                        "git",
                        "apply",
                        "--directory=$jniDir/librime-plugins/librime-lua",
                        "patches/librime-lua.patch",
                    ).directory(rootDir).inheritIO().start().waitFor()
                    ProcessBuilder(
                        "git",
                        "apply",
                        "--directory=$jniDir/librime-plugins/librime-lua-deps",
                        "patches/lua.patch",
                    ).directory(rootDir).inheritIO().start().waitFor()
                }
                outputs.upToDateWhen {
                    (macrosHeader.exists() && macrosHeader.readText().contains("throw std::runtime_error")) &&
                        (rimeApiH.exists() && rimeApiH.readText().contains("char* type;")) &&
                        (luaLiolib.exists() && luaLiolib.readText().contains("!defined(ANDROID)")) &&
                        (luaCmake.exists() && luaCmake.readText().contains("lua-utf8"))
                }
            }

        project.tasks.withType(ExternalNativeBuildTask::class.java).configureEach {
            dependsOn(applyPatches)
        }
    }

    private fun registerCleanCxxTask(project: Project) {
        project
            .tasks.register<Delete>("cleanCxxIntermediates") {
                delete(project.file(".cxx"))
            }.also {
                project.cleanTask.dependsOn(it)
            }
    }
}
