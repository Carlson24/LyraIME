/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ui.main.settings

import android.os.Bundle
import androidx.annotation.Keep
import androidx.navigation.fragment.findNavController
import androidx.preference.Preference
import androidx.preference.PreferenceScreen
import com.osfans.trime.R
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.data.prefs.PreferenceDelegateFragment
import com.osfans.trime.data.prefs.PreferenceDelegateProvider
import com.osfans.trime.ui.main.NavigationRoute
import com.osfans.trime.util.InputMethodUtils
import com.osfans.trime.util.navigateWithAnim

class VoiceInputSettingsFragment : PreferenceDelegateFragment(AppPrefs.defaultInstance().voiceInput) {

    private val voiceInputPrefs = AppPrefs.defaultInstance().voiceInput
    private var localVoicePref: Preference? = null

    @Keep
    private val onVoiceInputChangeListener = PreferenceDelegateProvider.OnChangeListener { key ->
        if (key == AppPrefs.VoiceInput.PREFERRED_VOICE_INPUT ||
            key == AppPrefs.VoiceInput.ASRKB_AIDL_VOICE_INPUT
        ) {
            updateLocalVoiceEnabled()
        }
    }

    private fun updateLocalVoiceEnabled() {
        val isBuiltin = voiceInputPrefs.preferredVoiceInput.getValue() == InputMethodUtils.BUILTIN_VOICE_INPUT
        val isAidlEnabled = voiceInputPrefs.asrkbAidlVoiceInputEnabled.getValue()
        localVoicePref?.isEnabled = isBuiltin && !isAidlEnabled
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        voiceInputPrefs.registerOnChangeListener(onVoiceInputChangeListener)
    }

    override fun onDestroy() {
        voiceInputPrefs.unregisterOnChangeListener(onVoiceInputChangeListener)
        super.onDestroy()
    }

    override fun onPreferenceUiCreated(screen: PreferenceScreen) {
        localVoicePref =
            Preference(requireContext()).apply {
                key = "local_voice_settings"
                isIconSpaceReserved = false
                title = getString(R.string.local_voice_settings)
                setOnPreferenceClickListener {
                    findNavController().navigateWithAnim(NavigationRoute.LocalVoice)
                    true
                }
            }.also { screen.addPreference(it) }

        updateLocalVoiceEnabled()
    }
}
