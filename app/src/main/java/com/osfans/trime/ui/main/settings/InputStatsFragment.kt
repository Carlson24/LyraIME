/*
 * SPDX-FileCopyrightText: 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ui.main.settings

import android.os.Bundle
import android.view.View
import androidx.annotation.StringRes
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceGroup
import com.osfans.trime.R
import com.osfans.trime.data.stats.InputStatsRepository
import com.osfans.trime.data.stats.InputStatsSnapshot
import com.osfans.trime.data.stats.InputStatsTotals
import com.osfans.trime.ui.common.PaddingPreferenceFragment
import com.osfans.trime.ui.common.confirmDialog
import com.osfans.trime.util.addCategory
import com.osfans.trime.util.formatDateTime
import com.osfans.trime.util.toast
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

class InputStatsFragment : PaddingPreferenceFragment() {
    private val numberFormat = NumberFormat.getIntegerInstance()

    private var keyboardCharsPref: Preference? = null
    private var keyboardEntriesPref: Preference? = null
    private var keyboardTodayCharsPref: Preference? = null
    private var keyboardTodayEntriesPref: Preference? = null
    private var activeDaysPref: Preference? = null
    private val bucketPrefs = mutableMapOf<Int, Preference>()
    private var voiceBuiltinPref: Preference? = null
    private var voiceAidlPref: Preference? = null
    private lateinit var schemaCategory: PreferenceCategory

    override fun onCreatePreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        preferenceScreen = preferenceManager.createPreferenceScreen(requireContext()).apply {
            addCategory(R.string.stats_keyboard_category) {
                isIconSpaceReserved = false
                keyboardCharsPref = addStatPreference(R.string.stats_total_chars)
                keyboardEntriesPref = addStatPreference(R.string.stats_total_entries)
                keyboardTodayCharsPref = addStatPreference(R.string.stats_today_chars)
                keyboardTodayEntriesPref = addStatPreference(R.string.stats_today_entries)
                activeDaysPref = addStatPreference(R.string.stats_active_days)
            }

            addCategory(R.string.stats_length_category) {
                isIconSpaceReserved = false
                bucketPrefs[1] = addStatPreference(R.string.stats_bucket_single)
                bucketPrefs[2] = addStatPreference(R.string.stats_bucket_two)
                bucketPrefs[3] = addStatPreference(R.string.stats_bucket_three)
                bucketPrefs[4] = addStatPreference(R.string.stats_bucket_four)
                bucketPrefs[5] = addStatPreference(R.string.stats_bucket_more)
            }

            addCategory(R.string.stats_voice_category) {
                isIconSpaceReserved = false
                voiceBuiltinPref = addStatPreference(R.string.stats_voice_builtin)
                voiceAidlPref = addStatPreference(R.string.stats_voice_aidl)
            }

            schemaCategory = PreferenceCategory(requireContext()).apply {
                setTitle(R.string.stats_schema_category)
                isIconSpaceReserved = false
            }
            addPreference(schemaCategory)

            addCategory(R.string.stats_data_category) {
                isIconSpaceReserved = false
                addPreference(
                    Preference(requireContext()).apply {
                        isIconSpaceReserved = false
                        isSingleLineTitle = false
                        setTitle(R.string.stats_clear)
                        setSummary(R.string.stats_clear_summary)
                        setOnPreferenceClickListener {
                            requireContext().confirmDialog(
                                title = R.string.stats_clear,
                                message = R.string.stats_clear_confirm,
                                onConfirm = {
                                    InputStatsRepository.clear()
                                    requireContext().toast(R.string.stats_cleared)
                                },
                            )
                            true
                        }
                    },
                )
            }
        }
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        InputStatsRepository.flushAsync()
        viewLifecycleOwner.lifecycleScope.launch {
            InputStatsRepository.observeSnapshot().collect { snapshot ->
                render(snapshot)
            }
        }
    }

    private fun PreferenceGroup.addStatPreference(
        @StringRes title: Int,
    ): Preference = Preference(context).apply {
        isIconSpaceReserved = false
        isSelectable = false
        isEnabled = false
        setShouldDisableView(false)
        isSingleLineTitle = false
        setTitle(title)
        setSummary(R.string.stats_empty_value)
    }.also { addPreference(it) }

    private fun render(snapshot: InputStatsSnapshot) {
        keyboardCharsPref?.summary = getString(
            R.string.stats_chars_value,
            formatNumber(snapshot.keyboard.chars),
        )
        keyboardEntriesPref?.summary = getString(
            R.string.stats_times_value,
            formatNumber(snapshot.keyboard.entries),
        )
        keyboardTodayCharsPref?.summary = getString(
            R.string.stats_chars_value,
            formatNumber(snapshot.keyboardToday.chars),
        )
        keyboardTodayEntriesPref?.summary = getString(
            R.string.stats_times_value,
            formatNumber(snapshot.keyboardToday.entries),
        )
        activeDaysPref?.summary = getString(
            R.string.stats_days_value,
            formatNumber(snapshot.activeDays.toLong()),
        )

        for (bucket in 1..5) {
            val totals = snapshot.buckets[bucket] ?: InputStatsTotals()
            bucketPrefs[bucket]?.summary = getString(
                R.string.stats_bucket_summary,
                formatNumber(totals.entries),
                formatNumber(totals.chars),
                formatPercent(totals.chars, snapshot.keyboard.chars),
            )
        }

        voiceBuiltinPref?.summary = formatVoiceSummary(
            totals = snapshot.voiceBuiltin,
            today = snapshot.voiceBuiltinToday,
        )
        voiceAidlPref?.summary = formatVoiceSummary(
            totals = snapshot.voiceAidl,
            today = snapshot.voiceAidlToday,
        )

        renderSchemas(snapshot)
    }

    private fun formatVoiceSummary(
        totals: InputStatsTotals,
        today: InputStatsTotals,
    ): String = getString(
        R.string.stats_voice_summary,
        formatNumber(totals.entries),
        formatNumber(totals.chars),
        formatNumber(today.entries),
        formatNumber(today.chars),
    )

    private fun renderSchemas(snapshot: InputStatsSnapshot) {
        schemaCategory.removeAll()
        if (snapshot.schemas.isEmpty()) {
            schemaCategory.addPreference(
                Preference(requireContext()).apply {
                    isIconSpaceReserved = false
                    isSelectable = false
                    isEnabled = false
                    setShouldDisableView(false)
                    isSingleLineTitle = false
                    setTitle(R.string.stats_no_schema_data)
                },
            )
            return
        }
        snapshot.schemas.forEach { schema ->
            schemaCategory.addPreference(
                Preference(requireContext()).apply {
                    isIconSpaceReserved = false
                    isSelectable = false
                    isEnabled = false
                    setShouldDisableView(false)
                    isSingleLineTitle = false
                    title = schema.schemaName.ifBlank { schema.schemaId }
                    summary = getString(
                        R.string.stats_schema_summary,
                        formatNumber(schema.entryCount),
                        formatNumber(schema.charCount),
                        formatDateTime(schema.lastUsedAt),
                    )
                },
            )
        }
    }

    private fun formatNumber(value: Long): String = numberFormat.format(value)

    private fun formatPercent(
        part: Long,
        total: Long,
    ): String {
        if (total <= 0L) return "0.0"
        return String.format(Locale.getDefault(), "%.1f", part * 100.0 / total)
    }
}
