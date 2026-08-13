/*
 * SPDX-FileCopyrightText: 2015 - 2024 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.osfans.trime.data.prefs

import androidx.preference.PreferenceScreen
import com.osfans.trime.util.WeakHashSet

abstract class PreferenceDelegateProvider {
    private val _preferenceDelegates: MutableMap<String, PreferenceDelegate<*>> = mutableMapOf()

    private val _preferenceDelegatesUi: MutableList<PreferenceDelegateUi<*>> = mutableListOf()

    val preferenceDelegates: Map<String, PreferenceDelegate<*>>
        get() = _preferenceDelegates

    val preferenceDelegatesUi: List<PreferenceDelegateUi<*>>
        get() = _preferenceDelegatesUi

    /**
     * 获取指定键的偏好代理
     * @param key 偏好键
     * @return 对应的偏好代理，如果不存在则返回 null
     */
    fun getPreferenceDelegate(key: String): PreferenceDelegate<*>? = _preferenceDelegates[key]

    /**
     * 根据 defaultValue 推断 SharedPreferences 的存储类型名称
     * @param delegate 偏好代理
     * @return 存储类型名称（BOOLEAN, INT, LONG, FLOAT, STRING, STRING_SET）
     */
    fun inferStorageTypeName(delegate: PreferenceDelegate<*>): String = when (delegate) {
        is PreferenceDelegate.SerializableDelegate<*> -> {
            // SerializableDelegate 都存储为 String
            "STRING"
        }

        else -> {
            // 根据 defaultValue 的类型判断
            when (delegate.defaultValue) {
                is Boolean -> "BOOLEAN"
                is Int -> "INT"
                is Long -> "LONG"
                is Float, is Double -> "FLOAT"
                is String -> "STRING"
                is Set<*> -> "STRING_SET"
                else -> "STRING"
            }
        }
    }

    open fun createUi(screen: PreferenceScreen) {
    }

    fun interface OnChangeListener {
        fun onChange(key: String)
    }

    private val onChangeListeners = WeakHashSet<OnChangeListener>()

    fun registerOnChangeListener(listener: OnChangeListener) {
        onChangeListeners.add(listener)
    }

    fun unregisterOnChangeListener(listener: OnChangeListener) {
        onChangeListeners.remove(listener)
    }

    fun notifyChange(key: String) {
        val preference = _preferenceDelegates[key] ?: return
        onChangeListeners.forEach { it.onChange(key) }
        preference.notifyChange()
    }

    fun PreferenceDelegateUi<*>.registerUi() {
        _preferenceDelegatesUi.add(this)
    }

    fun PreferenceDelegate<*>.register() {
        _preferenceDelegates[key] = this
    }
}
