package com.osfans.trime.data.packaging

import android.content.Context
import android.content.SharedPreferences
import com.osfans.trime.util.appContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

object PackageStateManager {
    private const val PREFS_NAME = "package_states"
    private const val KEY_PREFIX = "state_"

    @Serializable
    data class PackageState(
        val lastSchemaId: String = "",
        val lastThemeId: String = "trime",
    )

    private val json = Json { ignoreUnknownKeys = true }

    private val prefs: SharedPreferences
        get() = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getState(packageId: String): PackageState {
        val raw = prefs.getString(KEY_PREFIX + packageId, null) ?: return PackageState()
        return runCatching {
            json.decodeFromString<PackageState>(raw)
        }.getOrDefault(PackageState())
    }

    fun saveState(packageId: String, state: PackageState) {
        prefs.edit().putString(KEY_PREFIX + packageId, json.encodeToString(PackageState.serializer(), state)).apply()
    }

    fun getAllStates(): Map<String, PackageState> {
        val result = mutableMapOf<String, PackageState>()
        prefs.all.forEach { (key, value) ->
            if (key.startsWith(KEY_PREFIX) && value is String) {
                val pkgId = key.removePrefix(KEY_PREFIX)
                runCatching {
                    json.decodeFromString<PackageState>(value)
                }.onSuccess { result[pkgId] = it }
            }
        }
        return result
    }
}
