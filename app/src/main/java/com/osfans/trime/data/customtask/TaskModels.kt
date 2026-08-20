/*
 * SPDX-FileCopyrightText: 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.osfans.trime.data.customtask

import android.content.SharedPreferences
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class TaskState(
    val title: String,
    val url: String,
    val expectedSha256: String? = null,
    val releaseUpdatedAt: String? = null,
    val needsDecompress: Boolean = true,
    var progress: Float = 0f,
    var downloadedBytes: Long = 0L,
    var totalBytes: Long = 0L,
    var status: String = "",
    var isFinished: Boolean = false,
    var isError: Boolean = false,
)

data class CustomTask(
    val id: String,
    val name: String = "",
    val url: String = "",
    val boundPaths: List<String> = emptyList(),
    val isSelected: Boolean = false,
    val isExpanded: Boolean = true,
    val needsDecompress: Boolean = true,
    val excludeRules: String = "",
)

fun loadCustomTasks(jsonStr: String): MutableList<CustomTask> {
    val list = mutableListOf<CustomTask>()
    try {
        val array = JSONArray(jsonStr)
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            list.add(
                CustomTask(
                    id = obj.optString("id", UUID.randomUUID().toString()),
                    name = obj.optString("name", ""),
                    url = obj.optString("url", ""),
                    boundPaths = run {
                        val arr = obj.optJSONArray("boundPaths")
                        if (arr != null) {
                            (0 until arr.length()).map { arr.getString(it) }
                        } else {
                            emptyList()
                        }
                    },
                    isSelected = obj.optBoolean("isSelected", false),
                    isExpanded = obj.optBoolean("isExpanded", true),
                    needsDecompress = obj.optBoolean("needsDecompress", true),
                    excludeRules = obj.optString("excludeRules", ""),
                ),
            )
        }
    } catch (_: Exception) {
    }
    return list
}

fun saveCustomTasks(tasks: List<CustomTask>, sharedPref: SharedPreferences) {
    val array = JSONArray()
    tasks.forEach {
        val obj = JSONObject()
        obj.put("id", it.id)
        obj.put("name", it.name)
        obj.put("url", it.url)
        val pathsArr = JSONArray()
        it.boundPaths.forEach { pathsArr.put(it) }
        obj.put("boundPaths", pathsArr)
        obj.put("isSelected", it.isSelected)
        obj.put("isExpanded", it.isExpanded)
        obj.put("needsDecompress", it.needsDecompress)
        obj.put("excludeRules", it.excludeRules)
        array.put(obj)
    }
    sharedPref.edit { putString("custom_tasks_data", array.toString()) }
}

data class DeployTarget(
    val path: String,
    val enabled: Boolean = true,
)

fun loadDeployTargets(jsonStr: String): MutableList<DeployTarget> {
    val list = mutableListOf<DeployTarget>()
    try {
        val array = JSONArray(jsonStr)
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            list.add(
                DeployTarget(
                    path = obj.optString("path", ""),
                    enabled = obj.optBoolean("enabled", true),
                ),
            )
        }
    } catch (_: Exception) {
    }
    return list
}

fun saveDeployTargets(targets: List<DeployTarget>): String {
    val array = JSONArray()
    targets.forEach {
        val obj = JSONObject()
        obj.put("path", it.path)
        obj.put("enabled", it.enabled)
        array.put(obj)
    }
    return array.toString()
}
