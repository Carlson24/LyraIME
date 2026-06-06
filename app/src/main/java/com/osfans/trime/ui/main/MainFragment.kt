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

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val preferenceView = super.onCreateView(inflater, container, savedInstanceState)
        val context = requireContext()
        val padding = context.dp(16)
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            (preferenceView.parent as? ViewGroup)?.removeView(preferenceView)
            addView(
                preferenceView,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    0,
                    1f,
                ),
            )
            addView(
                EditText(context).apply {
                    hint = context.getString(R.string.main_input_test_hint)
                    gravity = Gravity.TOP or Gravity.START
                    inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
                    minLines = 2
                    maxLines = 4
                    setPadding(padding, padding, padding, 0)
                },
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
            ViewCompat.setOnApplyWindowInsetsListener(this) { _, windowInsets ->
                val imeBottom = windowInsets.getInsets(WindowInsetsCompat.Type.ime()).bottom
                val navBarBottom = windowInsets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
                setPadding(paddingLeft, paddingTop, paddingRight, maxOf(imeBottom, navBarBottom))
                windowInsets
            }
        }
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
                R.string.schemata,
                R.drawable.ic_round_view_list_24,
                NavigationRoute.SchemaList,
            )
            addDestinationPreference(
                R.string.user_dictionary,
                R.drawable.ic_baseline_book_24,
                NavigationRoute.UserDict,
            )
            addDestinationPreference(
                R.string.profile,
                R.drawable.ic_baseline_snippet_folder_24,
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
                    R.drawable.ic_clipboard_24,
                    NavigationRoute.Clipboard,
                )
                addDestinationPreference(
                    R.string.advanced,
                    R.drawable.ic_baseline_more_horiz_24,
                    NavigationRoute.Advanced,
                )
            }
        }
    }
}
