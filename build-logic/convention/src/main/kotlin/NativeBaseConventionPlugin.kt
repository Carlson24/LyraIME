/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
import com.android.build.api.dsl.ApplicationExtension
import com.android.build.gradle.tasks.ExternalNativeBuildTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.Delete
import org.gradle.kotlin.dsl.register

open class NativeBaseConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        target.pluginManager.apply("com.android.application")
        target.extensions.configure(ApplicationExtension::class.java) {
            ndkVersion = target.ndkVersion
            defaultConfig {
                @Suppress("UnstableApiUsage")
                externalNativeBuild {
                    cmake {
                        arguments(
                            "-DANDROID_STL=c++_static",
                            "-DQNN_SDK_ROOT=${target.qnnSdkRoot ?: ""}",
                        )
                    }
                }
            }
            // Use prebuilt JNI library if the "app/prebuilt" exists
            //
            // Steps to generate the prebuilt directory:
            // $ ./gradlew app:assembleRelease
            // $ cp --recursive app/build/intermediates/stripped_native_libs/universalRelease/out/lib app/prebuilt
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
    }

    private fun registerPatchApplyTask(project: Project) {
        val rootDir = project.rootDir
        val jniDir = "app/src/main/jni"
        val macrosHeader = project.file("src/main/jni/sherpa-onnx/sherpa-onnx/csrc/macros.h")
        val luaLiolib = project.file("src/main/jni/librime-lua-deps/lua5.4/liolib.c")
        val resourceH = project.file("src/main/jni/librime/src/rime/resource.h")
        val applyPatches =
            project.tasks.register("applyNativePatches") {
                group = "native"
                description = "Apply patches required for native build (lua + sherpa-onnx-qnn + librime-config-search-path)"
                doLast {
                    ProcessBuilder(
                        "git",
                        "apply",
                        "--directory=$jniDir/librime-lua-deps",
                        "patches/lua.patch",
                    ).directory(rootDir).inheritIO().start().waitFor()
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
                        "patches/librime-config-search-path.patch",
                    ).directory(rootDir).inheritIO().start().waitFor()
                }
                outputs.upToDateWhen {
                    (macrosHeader.exists() && macrosHeader.readText().contains("throw std::runtime_error")) &&
                        (luaLiolib.exists() && luaLiolib.readText().contains("!defined(ANDROID)")) &&
                        (resourceH.exists() && resourceH.readText().contains("extra_fallback_paths_"))
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
