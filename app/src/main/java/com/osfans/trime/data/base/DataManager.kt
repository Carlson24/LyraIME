// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.data.base

import android.content.res.AssetManager
import android.os.Build
import android.os.Environment
import com.osfans.trime.BuildConfig
import com.osfans.trime.data.packaging.SchemaPackageManager
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.util.FileUtils
import com.osfans.trime.util.ResourceUtils
import com.osfans.trime.util.appContext
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.File
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

object DataManager {
    private const val DEFAULT_CUSTOM_FILE_NAME = "default.custom.yaml"

    private const val DATA_CHECKSUMS_NAME = "checksums.json"

    private const val SCHEMA_LIST_CUSTOM_PATCH = """
      patch:
        schema_list:
          - schema: luna_pinyin
          - schema: luna_pinyin_simp
    """

    private val lock = ReentrantLock()

    private val json by lazy { Json }

    private fun deserializeDataChecksums(raw: String): DataChecksums = json.decodeFromString<DataChecksums>(raw)

    // If Android version supports direct boot, we put the hierarchy in device encrypted storage
    // instead of credential encrypted storage so that data can be accessed before user unlock
    private val dataDir: File =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Timber.d("Using device protected storage")
            appContext.createDeviceProtectedStorageContext().dataDir
        } else {
            File(appContext.applicationInfo.dataDir)
        }

    private fun AssetManager.dataChecksums(): DataChecksums = open(DATA_CHECKSUMS_NAME)
        .bufferedReader()
        .use { it.readText() }
        .let { deserializeDataChecksums(it) }

    private val prefs by lazy { AppPrefs.defaultInstance() }

    val defaultDataDir = File(Environment.getExternalStorageDirectory(), "Documents/LyraIME${if (BuildConfig.DEBUG) "Debug" else ""}")

    internal val externalFilesDir: File
        get() = appContext.getExternalFilesDir(null)!!

    val themesDir = File(externalFilesDir, "themes").also { it.mkdirs() }

    val userThemesDir get() = File(userDataBaseDir, "themes").also { it.mkdirs() }

    val themesBuildDir get() = File(userDataBaseDir, "themes/build").also { it.mkdirs() }

    val sharedDataDir
        get() = File(externalFilesDir, SchemaPackageManager.activePackageId).also { it.mkdirs() }

    val voiceDataDir = File(externalFilesDir, "voice").also { it.mkdirs() }

    val userDataBaseDir
        get() = File(prefs.profile.userDataDir.getValue()).also { it.mkdirs() }

    val userDataDir
        get() = File(userDataBaseDir, SchemaPackageManager.activePackageId).also { it.mkdirs() }

    val prebuiltDataDir get() = File(sharedDataDir, "build")
    val stagingDir get() = File(userDataDir, "build")

    /**
     * Return the absolute path of the compiled config file
     * based on given resource id.
     *
     * @param resourceId usually equals the config file name without the extension
     * @return the absolute path of the compiled config file
     */
    @JvmStatic
    fun resolveDeployedResourcePath(resourceId: String): String {
        val defaultPath = File(stagingDir, "$resourceId.yaml")
        if (!defaultPath.exists()) {
            val fallbackPath = File(prebuiltDataDir, "$resourceId.yaml")
            if (fallbackPath.exists()) return fallbackPath.absolutePath
        }
        return defaultPath.absolutePath
    }

    fun sync() = lock.withLock {
        Timber.d("DataManager.sync() called")

        val oldChecksumsFile = File(dataDir, DATA_CHECKSUMS_NAME)
        val oldChecksums =
            oldChecksumsFile
                .runCatching { deserializeDataChecksums(bufferedReader().use { it.readText() }) }
                .getOrElse { DataChecksums("", emptyMap()) }

        val newChecksums = appContext.assets.dataChecksums()

        val diffs = DataDiff.diff(oldChecksums, newChecksums).sortedByDescending { it.ordinal }
        Timber.d("DataManager.sync: ${diffs.size} diffs to apply")
        diffs.forEach {
            Timber.d("DataManager.sync diff: $it")
            when (it) {
                is DataDiff.CreateFile,
                is DataDiff.UpdateFile,
                -> {
                    val destPath = externalFilesDir.resolve(it.path).absolutePath
                    ResourceUtils.copyFile(it.path, destPath)
                }
                is DataDiff.DeleteDir,
                is DataDiff.DeleteFile,
                -> FileUtils.delete(externalFilesDir.resolve(it.path)).getOrThrow()
            }
        }

        ResourceUtils.copyFile(DATA_CHECKSUMS_NAME, dataDir.resolve(DATA_CHECKSUMS_NAME).absolutePath)

        Timber.d("DataManager.sync: sharedDataDir=$sharedDataDir, exists=${sharedDataDir.exists()}")
        sharedDataDir.listFiles()?.forEach { f ->
            Timber.d("DataManager.sync: sharedDataDir file: ${f.name} (dir=${f.isDirectory})")
        }

        SchemaPackageManager.discoverPackages().forEach { pkg ->
            val userPkgDir = File(userDataBaseDir, pkg.id)
            val custom = userPkgDir.resolve(DEFAULT_CUSTOM_FILE_NAME)
            if (!custom.exists()) {
                userPkgDir.mkdirs()
                if (custom.createNewFile()) {
                    custom.writeText(SCHEMA_LIST_CUSTOM_PATCH.trimIndent())
                }
            }
        }

        Timber.d("Synced!")
    }
}
