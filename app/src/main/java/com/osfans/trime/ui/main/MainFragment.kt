/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ui.main

import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceGroup
import com.osfans.trime.R
import com.osfans.trime.ui.common.PaddingPreferenceFragment
import com.osfans.trime.ui.texteditor.FileBrowserActivity
import com.osfans.trime.util.addCategory
import com.osfans.trime.util.addPreference
import com.osfans.trime.util.navigateWithAnim
import splitties.dimensions.dp

class MainFragment : PaddingPreferenceFragment() {
    private val viewModel: MainViewModel by activityViewModels()

    override fun onStart() {
        super.onStart()
        viewModel.enableTopOptionsMenu()
    }

    override fun onStop() {
        viewModel.disableTopOptionsMenu()
        super.onStop()
    }
    private fun PreferenceGroup.addDestinationPreference(
        @StringRes title: Int,
        @DrawableRes icon: Int,
        route: NavigationRoute,
    ) {
        addPreference(title, icon = icon) {
            findNavController().navigateWithAnim(route)
        }
    }

    override fun onCreatePreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        preferenceScreen = preferenceManager.createPreferenceScreen(requireContext()).apply {
            addDestinationPreference(
                R.string.packages_title,
                R.drawable.ic_baseline_folder_24,
                NavigationRoute.PackageList,
            )
            addDestinationPreference(
                R.string.user_dictionary,
                R.drawable.ic_baseline_book_24,
                NavigationRoute.UserDict,
            )
            addDestinationPreference(
                R.string.profile_and_synchronization,
                R.drawable.ic_baseline_folder_sync_24,
                NavigationRoute.Profile,
            )
            addCategory("") {
                isIconSpaceReserved = false
                addDestinationPreference(
                    R.string.general,
                    R.drawable.ic_baseline_tune_24,
                    NavigationRoute.General,
                )
                addDestinationPreference(
                    R.string.virtual_keyboard,
                    R.drawable.ic_baseline_keyboard_24,
                    NavigationRoute.VirtualKeyboard,
                )
                addDestinationPreference(
                    R.string.voice_input,
                    R.drawable.ic_baseline_mic_24,
                    NavigationRoute.VoiceInput,
                )
                addDestinationPreference(
                    R.string.candidates_window,
                    R.drawable.ic_baseline_list_alt_24,
                    NavigationRoute.CandidatesWindow,
                )
                addDestinationPreference(
                    R.string.theme,
                    R.drawable.ic_baseline_color_lens_24,
                    NavigationRoute.Theme,
                )
                addDestinationPreference(
                    R.string.clipboard,
                    R.drawable.ic_baseline_clipboard_24,
                    NavigationRoute.Clipboard,
                )
                addDestinationPreference(
                    R.string.advanced,
                    R.drawable.ic_baseline_more_horiz_24,
                    NavigationRoute.Advanced,
                )
            }
            addCategory("") {
                isIconSpaceReserved = false
                addPreference(
                    title = R.string.text_editor_title,
                    icon = R.drawable.ic_baseline_file_document_edit_outline_24,
                ) {
                    requireContext().startActivity(android.content.Intent(requireContext(), FileBrowserActivity::class.java))
                }
                addPreference(
                    title = R.string.wanxiang_updater,
                    icon = R.drawable.ic_baseline_download_24,
                ) {
                    findNavController().navigateWithAnim(NavigationRoute.Wanxiang)
                }
                addPreference(
                    title = R.string.wanxiang_custom_title,
                    icon = R.drawable.ic_baseline_edit_24,
                ) {
                    findNavController().navigateWithAnim(NavigationRoute.WanxiangCustom)
                }
            }
        }
    }
}
