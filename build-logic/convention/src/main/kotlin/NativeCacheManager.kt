/*
 * SPDX-FileCopyrightText: 2025 - 2026 LyraIME community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

import org.gradle.api.Project
import java.io.File
import java.security.MessageDigest

object NativeCacheManager {
    private const val CACHE_DIR_NAME = ".native-cache"
    private val ALL_DEPS = listOf("CMakeLists.txt", "cmake")

    enum class Component(
        val cacheKey: String,
        val srcDirs: List<String>,
        val soFiles: List<String>,
    ) {
        RIME(
            "rime",
            ALL_DEPS + listOf(
                "librime",
                "librime_jni",
                "librime-plugins",
                "OpenCC",
                "yaml-cpp",
                "boost",
            ),
            listOf("librime_jni.so"),
        ),
        LUA_THEME(
            "lua_theme",
            ALL_DEPS + listOf(
                "lua_theme_jni",
                "yaml-cpp",
                "lua55",
            ),
            listOf("liblua_theme_jni.so"),
        ),
        SHERPA(
            "sherpa",
            ALL_DEPS + listOf("sherpa-onnx"),
            listOf(
                "libsherpa-onnx-jni.so",
                "libsherpa-onnx-c-api.so",
                "libsherpa-onnx-cxx-api.so",
            ),
        ),
    }

    fun hashTree(baseDir: File, relativePaths: List<String>): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val toHash = relativePaths.sorted()
        for (relPath in toHash) {
            val path = File(baseDir, relPath)
            if (!path.exists()) continue
            if (path.isFile) {
                digest.update(relPath.toByteArray(Charsets.UTF_8))
                val nameBytes = path.name.toByteArray(Charsets.UTF_8)
                digest.update(nameBytes)
                digest.update(path.readBytes())
            } else if (path.isDirectory) {
                path.walk()
                    .filter { it.isFile }
                    .sortedBy { it.absolutePath }
                    .forEach { file ->
                        val rel = file.relativeTo(baseDir).invariantSeparatorsPath
                        digest.update(rel.toByteArray(Charsets.UTF_8))
                        digest.update(file.readBytes())
                    }
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun computeHashes(
        jniDir: File,
    ): Map<Component, String> = Component.entries.associateWith { comp ->
        hashTree(jniDir, comp.srcDirs)
    }

    fun cacheDir(project: Project, buildType: String): File = File(project.file("src/main/jni"), "$CACHE_DIR_NAME/$buildType")

    fun hashFile(cacheDir: File, comp: Component): File = File(cacheDir, "${comp.cacheKey}.sha256")

    fun soCacheDir(cacheDir: File, comp: Component): File = File(cacheDir, comp.cacheKey)

    fun readCachedHash(cacheDir: File, comp: Component): String? {
        val f = hashFile(cacheDir, comp)
        return if (f.exists()) f.readText().trim().takeIf { it.length == 64 } else null
    }

    fun writeCachedHash(cacheDir: File, comp: Component, hash: String) {
        hashFile(cacheDir, comp).apply {
            parentFile.mkdirs()
            writeText(hash)
        }
    }

    fun restoreFromCache(
        cacheDir: File,
        comp: Component,
        abi: String,
        objDir: File,
    ) {
        val cache = soCacheDir(cacheDir, comp)
        val targetDir = File(objDir, abi)
        targetDir.mkdirs()
        comp.soFiles.forEach { so ->
            val src = File(cache, so)
            if (src.exists()) {
                src.copyTo(File(targetDir, so), overwrite = true)
            }
        }
    }

    fun cacheFromObjDir(
        project: Project,
        comp: Component,
        abi: String,
        cacheDir: File,
    ) {
        val objDir = project.file("build/intermediates/cxx")
        val cache = soCacheDir(cacheDir, comp)
        cache.mkdirs()
        comp.soFiles.forEach { so ->
            val file = project.fileTree(objDir)
                .matching { include("**/obj/$abi/$so") }
                .firstOrNull()
            file?.copyTo(File(cache, so), overwrite = true)
        }
    }
}
