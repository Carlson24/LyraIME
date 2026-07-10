/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.backup

import android.content.Context
import android.util.Base64
import androidx.paging.PagingSource
import androidx.preference.PreferenceManager
import androidx.room.Room
import androidx.room.withTransaction
import com.osfans.trime.data.db.Database
import com.osfans.trime.data.db.DatabaseBean
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.util.appContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.serializer
import timber.log.Timber
import java.io.File
import java.security.MessageDigest

object BackupManager {
    private const val CUSTOM_TASKS_KEY = "custom_tasks_data"

    private val passwordKeys = setOf(
        AppPrefs.Profile.WEBDAV_PASSWORD,
        AppPrefs.Clipboard.SYNC_CLIPBOARD_PASSWORD,
        AppPrefs.Wanxiang.GH_TOKEN,
        AppPrefs.Wanxiang.CNB_TOKEN,
    )

    fun computeSettingsFingerprint(): String {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(appContext)
        val allPrefs = sharedPreferences.all
        val wanxiangPrefs = appContext.getSharedPreferences("WanxiangPrefs", Context.MODE_PRIVATE)

        val sb = StringBuilder()

        val keys = allPrefs.keys
            .filter { it != AppPrefs.Internal.PID }
            .sorted()
        for (key in keys) {
            val value = allPrefs[key]
            sb.append(key).append('=').append(value).append('\n')
        }

        val customTasks = wanxiangPrefs.getString(CUSTOM_TASKS_KEY, null)
        sb.append(CUSTOM_TASKS_KEY).append('=').append(customTasks).append('\n')

        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(sb.toString().toByteArray())
        return hash.joinToString("") { "%02x".format(it) }
    }

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    private val backupMutex = Mutex()
    private val restoreMutex = Mutex()

    suspend fun createBackup(
        includePreferences: Boolean = true,
        includeClipboard: Boolean = true,
        includeWanxiang: Boolean = true,
        includeCustomTasks: Boolean = true,
        onlyPinnedClipboard: Boolean = false,
    ): BackupData = backupMutex.withLock {
        withContext(Dispatchers.IO) {
            Timber.d("Creating backup: preferences=$includePreferences, clipboard=$includeClipboard, wanxiang=$includeWanxiang, customTasks=$includeCustomTasks, onlyPinnedClipboard=$onlyPinnedClipboard")
            BackupData(
                preferences = if (includePreferences) exportPreferences() else null,
                clipboard = if (includeClipboard) exportClipboard(onlyPinnedClipboard) else null,
                wanxiangPrefs = if (includeWanxiang) exportWanxiangPrefs() else null,
                customTasks = if (includeCustomTasks) exportCustomTasks() else null,
            ).also {
                Timber.d("Backup created successfully")
            }
        }
    }

    suspend fun restoreBackup(
        backupData: BackupData,
        restorePreferences: Boolean = true,
        restoreClipboard: Boolean = true,
        restoreWanxiang: Boolean = true,
        restoreCustomTasks: Boolean = true,
    ): Result<Unit> = restoreMutex.withLock {
        withContext(Dispatchers.IO) {
            try {
                Timber.d("Restoring backup: preferences=$restorePreferences, clipboard=$restoreClipboard, wanxiang=$restoreWanxiang, customTasks=$restoreCustomTasks")
                val migratedData = migrateBackup(backupData)

                // Temporarily disable clipboard listener to avoid conflicts during restore
                var wasClipboardListeningEnabled = false
                if (restoreClipboard) {
                    try {
                        val clipPref = AppPrefs.defaultInstance().clipboard
                        wasClipboardListeningEnabled = clipPref.clipboardListening.getValue()
                        if (wasClipboardListeningEnabled) {
                            clipPref.clipboardListening.setValue(false)
                            Timber.d("Temporarily disabled clipboard listener during restore")
                        }
                    } catch (e: Exception) {
                        Timber.w(e, "Failed to disable clipboard listener, continuing anyway")
                    }
                }

                try {
                    if (restorePreferences && migratedData.preferences != null) {
                        importPreferences(migratedData.preferences)
                    }
                    if (restoreClipboard && migratedData.clipboard != null) {
                        importClipboard(migratedData.clipboard)
                    }
                    if (restoreWanxiang && migratedData.wanxiangPrefs != null) {
                        importWanxiangPrefs(migratedData.wanxiangPrefs)
                    }
                    if (restoreCustomTasks && migratedData.customTasks != null) {
                        importCustomTasks(migratedData.customTasks)
                    }
                    Timber.d("Backup restored successfully")
                    Result.success(Unit)
                } finally {
                    // Re-enable clipboard listener if it was enabled before
                    if (restoreClipboard && wasClipboardListeningEnabled) {
                        try {
                            val clipPref = AppPrefs.defaultInstance().clipboard
                            clipPref.clipboardListening.setValue(true)
                            Timber.d("Re-enabled clipboard listener after restore")
                        } catch (e: Exception) {
                            Timber.e(e, "Failed to re-enable clipboard listener")
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to restore backup")
                Result.failure(e)
            }
        }
    }

    /**
     * 迁移备份数据到当前版本
     * @param backupData 原始备份数据
     * @return 迁移后的备份数据
     * @throws Exception 如果备份版本比当前版本新
     */
    suspend fun migrateBackup(backupData: BackupData): BackupData = when {
        backupData.version > BackupData.CURRENT_VERSION -> {
            throw Exception("Backup version ${backupData.version} is newer than current version ${BackupData.CURRENT_VERSION}")
        }
        backupData.version < BackupData.CURRENT_VERSION -> {
            performMigration(backupData)
        }
        else -> backupData
    }

    /**
     * 执行备份数据的版本迁移
     * @param backupData 原始备份数据
     * @return 迁移后的备份数据
     * @note 当前版本为1，尚未需要迁移，此方法为未来版本升级预留
     * @example 当版本从1升级到2时，添加case 1 -> migrateFromVersion1ToVersion2(backupData)
     */
    private suspend fun performMigration(backupData: BackupData): BackupData {
        var migrated = backupData

        for (version in backupData.version until BackupData.CURRENT_VERSION) {
            migrated = when (version) {
                // 示例：当版本从1升级到2时，添加
                // 1 -> migrateFromVersion1ToVersion2(migrated)
                else -> {
                    migrated
                }
            }
        }

        return migrated.copy(version = BackupData.CURRENT_VERSION)
    }

    private fun JsonElement.sortKeys(): JsonElement = when (this) {
        is JsonObject -> JsonObject(entries.map { (k, v) -> k to v.sortKeys() }.sortedBy { it.first }.toMap())
        is JsonArray -> JsonArray(map { it.sortKeys() })
        else -> this
    }

    suspend fun saveBackupToFile(
        backupData: BackupData,
        file: File,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Timber.d("Saving backup to file: ${file.absolutePath}")
            val sorted = json.encodeToJsonElement(serializer(), backupData).sortKeys()
            val jsonString = json.encodeToString(sorted)
            file.writeText(jsonString)
            Timber.d("Backup saved successfully, file size: ${file.length()} bytes")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to save backup to file: ${file.absolutePath}")
            Result.failure(e)
        }
    }

    suspend fun loadBackupFromFile(file: File): Result<BackupData> = withContext(Dispatchers.IO) {
        try {
            Timber.d("Loading backup from file: ${file.absolutePath}")
            val jsonString = file.readText()
            val backupData = json.decodeFromString<BackupData>(jsonString)
            Timber.d("Backup loaded successfully, version: ${backupData.version}")
            Result.success(backupData)
        } catch (e: Exception) {
            Timber.e(e, "Failed to load backup from file: ${file.absolutePath}")
            Result.failure(e)
        }
    }

    private fun exportPreferences(): Map<String, BackupPreference> {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(appContext)
        val allPrefs = sharedPreferences.all
        val filteredPrefs = mutableMapOf<String, BackupPreference>()

        for ((key, value) in allPrefs) {
            if (key != AppPrefs.Internal.PID && !key.startsWith("wanxiang_") && key != "screenshot_sync_handled_items") {
                filteredPrefs[key] = valueToBackupPreference(key, value)
            }
        }

        return filteredPrefs
    }

    private fun valueToBackupPreference(key: String, value: Any?): BackupPreference = when (value) {
        null -> BackupPreference(JsonNull, PreferenceType.STRING)
        is Int -> BackupPreference(JsonPrimitive(value), PreferenceType.INT)
        is Long -> BackupPreference(JsonPrimitive(value), PreferenceType.LONG)
        is Float -> BackupPreference(JsonPrimitive(value), PreferenceType.FLOAT)
        is Double -> BackupPreference(JsonPrimitive(value), PreferenceType.FLOAT)
        is Boolean -> BackupPreference(JsonPrimitive(value), PreferenceType.BOOLEAN)
        is String -> {
            if (key in passwordKeys && value.isNotEmpty()) {
                val encoded = Base64.encodeToString(value.toByteArray(), Base64.NO_WRAP)
                BackupPreference(JsonPrimitive(encoded), PreferenceType.STRING, encoded = true)
            } else if (value.contains('\n')) {
                BackupPreference(
                    JsonArray(value.lines().map { JsonPrimitive(it) }),
                    PreferenceType.STRING,
                )
            } else {
                BackupPreference(tryParseJsonValue(value), PreferenceType.STRING)
            }
        }
        is Set<*> -> {
            val jsonArray = JsonArray(value.map { JsonPrimitive(it.toString()) })
            BackupPreference(jsonArray, PreferenceType.STRING_SET)
        }
        else -> BackupPreference(JsonPrimitive(value.toString()), PreferenceType.STRING)
    }

    private fun tryParseJsonValue(value: String): JsonElement {
        if (!value.startsWith("[") && !value.startsWith("{")) {
            return JsonPrimitive(value)
        }
        return try {
            json.parseToJsonElement(value)
        } catch (_: Exception) {
            JsonPrimitive(value)
        }
    }

    private suspend fun importPreferences(prefs: Map<String, BackupPreference>) {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(appContext)
        val editor = sharedPreferences.edit()

        // 获取 AppPrefs 实例用于验证
        val appPrefs = AppPrefs.defaultInstance()

        prefs.forEach { (key, backupPref) ->
            val value = backupPref.value
            val type = backupPref.type

            // 验证偏好键和类型
            val validationResult = appPrefs.validatePreference(key, type.name)

            if (!validationResult.isValid) {
                Timber.w("Skipping preference '$key': ${validationResult.message}")
                return@forEach
            }

            when {
                value is JsonArray -> {
                    when (type) {
                        PreferenceType.STRING_SET -> {
                            val stringSet = value.map<JsonElement, String> { (it as JsonPrimitive).content }.toSet()
                            editor.putStringSet(key, stringSet)
                        }
                        PreferenceType.STRING -> {
                            val joined = value.joinToString("\n") { (it as JsonPrimitive).content }
                            editor.putString(key, joined)
                        }
                        else -> {
                            val jsonString = value.toString()
                            editor.putString(key, jsonString)
                        }
                    }
                }
                value is JsonPrimitive -> {
                    val content = value.content

                    when (type) {
                        PreferenceType.BOOLEAN -> {
                            editor.putBoolean(key, content.toBoolean())
                        }
                        PreferenceType.INT -> {
                            editor.putInt(key, content.toIntOrNull() ?: 0)
                        }
                        PreferenceType.LONG -> {
                            editor.putLong(key, content.toLongOrNull() ?: 0L)
                        }
                        PreferenceType.FLOAT -> {
                            editor.putFloat(key, content.toFloatOrNull() ?: 0f)
                        }
                        PreferenceType.STRING -> {
                            editor.putString(key, importStringValue(key, content, backupPref.encoded))
                        }
                        else -> {
                            editor.putString(key, content)
                        }
                    }
                }
                value is JsonNull -> editor.putString(key, null)
                else -> {
                    val jsonString = value.toString()
                    editor.putString(key, jsonString)
                }
            }
        }

        editor.apply()
    }

    private fun importStringValue(
        key: String,
        content: String,
        encoded: Boolean,
    ): String {
        if (!encoded || key !in passwordKeys) return content
        return try {
            String(Base64.decode(content, Base64.NO_WRAP))
        } catch (e: Exception) {
            Timber.w(e, "Failed to decode base64 for key '$key', using raw value")
            content
        }
    }

    private suspend fun exportClipboard(onlyPinned: Boolean = false): List<BackupBean> {
        var retryCount = 0
        val maxRetries = 3
        var lastException: Exception? = null

        while (retryCount < maxRetries) {
            try {
                val db = Room.databaseBuilder(appContext, Database::class.java, "clipboard.db")
                    .fallbackToDestructiveMigration(true)
                    .build()
                val dao = db.databaseDao()
                val pagingSource = if (onlyPinned) dao.favoriteEntries() else dao.allEntries()
                val beans = mutableListOf<DatabaseBean>()
                val params = PagingSource.LoadParams.Refresh<Int>(null, Int.MAX_VALUE, false)
                val result = pagingSource.load(params) as PagingSource.LoadResult.Page
                beans.addAll(result.data)
                db.close()
                Timber.d("Successfully exported clipboard data with ${beans.size} items (onlyPinned=$onlyPinned)")
                return beans.map { bean ->
                    BackupBean(
                        text = bean.text,
                        type = bean.type,
                        time = bean.time,
                        pinned = bean.pinned,
                    )
                }
            } catch (e: Exception) {
                lastException = e
                retryCount++
                Timber.w("Failed to export clipboard data, attempt $retryCount/$maxRetries: ${e.message}")

                if (retryCount < maxRetries) {
                    kotlinx.coroutines.delay(500L * retryCount)
                }
            }
        }

        throw lastException ?: Exception("Failed to export clipboard data after $maxRetries attempts")
    }

    private suspend fun importClipboard(beans: List<BackupBean>) {
        var retryCount = 0
        val maxRetries = 3
        var lastException: Exception? = null

        while (retryCount < maxRetries) {
            try {
                val db = Room.databaseBuilder(appContext, Database::class.java, "clipboard.db")
                    .fallbackToDestructiveMigration(true)
                    .build()
                val dao = db.databaseDao()

                db.withTransaction {
                    dao.deleteAll()

                    beans.forEach { backupBean ->
                        val bean =
                            DatabaseBean(
                                text = backupBean.text ?: "",
                                type = backupBean.type,
                                time = backupBean.time,
                                pinned = backupBean.pinned,
                            )
                        dao.insert(bean)
                    }
                }

                db.close()
                Timber.d("Successfully imported clipboard data with ${beans.size} items")
                return
            } catch (e: Exception) {
                lastException = e
                retryCount++
                Timber.w("Failed to import clipboard data, attempt $retryCount/$maxRetries: ${e.message}")

                if (retryCount < maxRetries) {
                    kotlinx.coroutines.delay(500L * retryCount)
                }
            }
        }

        throw lastException ?: Exception("Failed to import clipboard data after $maxRetries attempts")
    }

    private fun exportWanxiangPrefs(): Map<String, BackupPreference> {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(appContext)
        val allPrefs = sharedPreferences.all
        val result = mutableMapOf<String, BackupPreference>()

        for ((key, value) in allPrefs) {
            if (key.startsWith("wanxiang_")) {
                result[key] = valueToBackupPreference(key, value)
            }
        }

        Timber.d("Successfully exported Wanxiang preferences with ${result.size} items")
        return result
    }

    private fun exportCustomTasks(): String? {
        val sharedPreferences = appContext.getSharedPreferences("WanxiangPrefs", Context.MODE_PRIVATE)
        return sharedPreferences.getString(CUSTOM_TASKS_KEY, null)
    }

    private fun importCustomTasks(data: String) {
        val sharedPreferences = appContext.getSharedPreferences("WanxiangPrefs", Context.MODE_PRIVATE)
        sharedPreferences.edit().putString(CUSTOM_TASKS_KEY, data).apply()
        Timber.d("Successfully imported custom tasks")
    }

    private suspend fun importWanxiangPrefs(prefs: Map<String, BackupPreference>) {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(appContext)
        val editor = sharedPreferences.edit()
        val appPrefs = AppPrefs.defaultInstance()

        prefs.forEach { (key, backupPref) ->
            val value = backupPref.value
            val type = backupPref.type

            val validationResult = appPrefs.validatePreference(key, type.name)
            if (!validationResult.isValid) {
                Timber.w("Skipping Wanxiang preference '$key': ${validationResult.message}")
                return@forEach
            }

            when {
                value is JsonArray -> {
                    when (type) {
                        PreferenceType.STRING_SET -> {
                            val stringSet = value.map<JsonElement, String> { (it as JsonPrimitive).content }.toSet()
                            editor.putStringSet(key, stringSet)
                        }
                        PreferenceType.STRING -> {
                            val joined = value.joinToString("\n") { (it as JsonPrimitive).content }
                            editor.putString(key, joined)
                        }
                        else -> {
                            editor.putString(key, value.toString())
                        }
                    }
                }
                value is JsonPrimitive -> {
                    val content = value.content

                    when (type) {
                        PreferenceType.BOOLEAN -> {
                            editor.putBoolean(key, content.toBoolean())
                        }
                        PreferenceType.INT -> {
                            editor.putInt(key, content.toIntOrNull() ?: 0)
                        }
                        PreferenceType.LONG -> {
                            editor.putLong(key, content.toLongOrNull() ?: 0L)
                        }
                        PreferenceType.FLOAT -> {
                            editor.putFloat(key, content.toFloatOrNull() ?: 0f)
                        }
                        PreferenceType.STRING -> {
                            editor.putString(key, importStringValue(key, content, backupPref.encoded))
                        }
                        else -> {
                            editor.putString(key, content)
                        }
                    }
                }
                value is JsonNull -> editor.putString(key, null)
                else -> {
                    editor.putString(key, value.toString())
                }
            }
        }

        editor.apply()
        Timber.d("Successfully imported Wanxiang preferences with ${prefs.size} items")
    }
}
