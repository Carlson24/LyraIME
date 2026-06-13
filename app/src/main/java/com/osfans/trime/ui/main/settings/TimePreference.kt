/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.osfans.trime.ui.main.settings

import android.app.TimePickerDialog
import android.content.Context
import androidx.preference.Preference
import androidx.preference.Preference.SummaryProvider

class TimePreference(context: Context) : Preference(context) {
    private var defaultTime = "02:00"

    fun setDefaultTime(time: String) {
        defaultTime = time
    }

    val time: String
        get() = getPersistedString(defaultTime)

    fun setTimeAndPersist(newTime: String) {
        persistString(newTime)
        notifyChanged()
    }

    init {
        summaryProvider = TimeSummaryProvider
    }

    object TimeSummaryProvider : SummaryProvider<TimePreference> {
        override fun provideSummary(preference: TimePreference): CharSequence = preference.time
    }
}
