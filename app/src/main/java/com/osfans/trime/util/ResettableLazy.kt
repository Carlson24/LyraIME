// SPDX-FileCopyrightText: 2015 - 2026 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.util

import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

class ResettableLazy<T>(private val initializer: () -> T) : ReadWriteProperty<Any?, T> {
    @Volatile
    private var cached: Any? = UNINITIALIZED

    override fun getValue(thisRef: Any?, property: KProperty<*>): T {
        val v = cached
        if (v !== UNINITIALIZED) {
            @Suppress("UNCHECKED_CAST")
            return v as T
        }
        val newValue = initializer()
        cached = newValue
        return newValue
    }

    override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        cached = value
    }

    fun invalidate() {
        cached = UNINITIALIZED
    }

    companion object {
        private val UNINITIALIZED = Any()
    }
}
