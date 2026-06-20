// SPDX-FileCopyrightText: 2025 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ui.main.settings

import android.content.Context
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.LifecycleCoroutineScope
import com.osfans.trime.R
import com.osfans.trime.data.soundeffect.SoundEffectManager
import com.osfans.trime.ui.common.pickSingle

object SoundEffectPickerDialog {
    fun build(
        scope: LifecycleCoroutineScope,
        context: Context,
    ): AlertDialog {
        val all = SoundEffectManager.getAllSoundEffects().map { it.name }
        val current = SoundEffectManager.activeSoundEffect?.name ?: ""
        val currentIndex = all.indexOfFirst { it == current }
        return context.pickSingle(
            scope = scope,
            title = R.string.custom_sound_effect_name,
            items = all.toTypedArray(),
            selectedIndex = currentIndex,
            emptyMessage = R.string.no_effect_to_select,
            onSelect = { index ->
                if (index != currentIndex) {
                    SoundEffectManager.switchEffect(all[index])
                }
            },
        )
    }
}
