/*
 * SPDX-FileCopyrightText: 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.osfans.trime.data.wanxiang

import android.content.SharedPreferences
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class TaskState(
    val title: String,
    val url: String,
    val expectedSha256: String? = null,
    var progress: Float = 0f,
    var status: String = "",
    var isFinished: Boolean = false,
    var isError: Boolean = false,
)

data class CustomTask(
    val id: String,
    val name: String = "",
    val url: String = "",
    val boundPath: String = "DEFAULT",
    val isSelected: Boolean = false,
    val isExpanded: Boolean = true,
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
                    boundPath = obj.optString("boundPath", "DEFAULT"),
                    isSelected = obj.optBoolean("isSelected", false),
                    isExpanded = obj.optBoolean("isExpanded", true),
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
        obj.put("boundPath", it.boundPath)
        obj.put("isSelected", it.isSelected)
        obj.put("isExpanded", it.isExpanded)
        array.put(obj)
    }
    sharedPref.edit { putString("custom_tasks_data", array.toString()) }
}

val DefaultExcludeRules = listOf(
    """^custom_phrase\.txt$""",
    """.*userdb$""",
    """.*userdb\.txt""",
    """sequence.*txt""",
    """^(?!custom/).*\.custom\.yaml$""",
    """^user\.yaml$""",
    """^installation\.yaml$""",
    """^sync/.*""",
).joinToString("\n")
