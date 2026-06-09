/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.prefs

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Typeface
import androidx.annotation.Keep
import com.osfans.trime.R
import com.osfans.trime.data.base.DataManager
import com.osfans.trime.data.wanxiang.DefaultExcludeRules
import com.osfans.trime.ime.candidates.compact.CompactCandidateMode
import com.osfans.trime.ime.candidates.popup.PopupCandidatesLayout
import com.osfans.trime.ime.candidates.popup.PopupCandidatesMode
import com.osfans.trime.ime.composition.PopupPosition
import com.osfans.trime.ime.core.InlinePreeditMode
import com.osfans.trime.util.InputMethodUtils
import com.osfans.trime.util.appContext
import java.lang.ref.WeakReference

/**
 * Helper class for an organized access to the shared preferences.
 */
class AppPrefs(
    private val shared: SharedPreferences,
) {
    private val applicationContext: WeakReference<Context> = WeakReference(appContext)

    private val providers = mutableListOf<PreferenceDelegateProvider>()

    fun <T : PreferenceDelegateProvider> registerProvider(providerF: (SharedPreferences) -> T): T {
        val provider = providerF(shared)
        providers.add(provider)
        return provider
    }

    private fun <T : PreferenceDelegateProvider> T.register() = this.apply {
        registerProvider { this }
    }

    /**
     * 验证偏好键和类型是否有效
     * @param key 偏好键
     * @param backupTypeName 备份中的类型名称
     * @return 验证结果，包含是否有效和期望的类型
     */
    data class PreferenceValidationResult(
        val isValid: Boolean,
        val expectedTypeName: String? = null,
        val message: String = "",
    )

    fun validatePreference(key: String, backupTypeName: String): PreferenceValidationResult {
        // 查找包含该键的 provider
        for (provider in providers) {
            val delegate = provider.getPreferenceDelegate(key)
            if (delegate != null) {
                // 找到该键，检查类型是否匹配
                val expectedTypeName = provider.inferStorageTypeName(delegate)
                return if (expectedTypeName.equals(backupTypeName, ignoreCase = true)) {
                    PreferenceValidationResult(
                        isValid = true,
                        expectedTypeName = expectedTypeName,
                        message = "Key and type are valid",
                    )
                } else {
                    PreferenceValidationResult(
                        isValid = false,
                        expectedTypeName = expectedTypeName,
                        message = "Type mismatch: expected $expectedTypeName, but got $backupTypeName",
                    )
                }
            }
        }

        // 未找到该键
        return PreferenceValidationResult(
            isValid = false,
            expectedTypeName = null,
            message = "Unknown preference key: $key",
        )
    }

    val internal = Internal(shared)
    val general = General(shared).register()
    val voiceInput = VoiceInput(shared).register()
    val profile = Profile(shared).register()
    val keyboard = Keyboard(shared).register()
    val candidates = Candidates(shared).register()
    val clipboard = Clipboard(shared).register()
    val advanced = Advanced(shared).register()
    val wanxiang = Wanxiang(shared).register()

    @Keep
    private val onSharedPreferenceChangeListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == null) return@OnSharedPreferenceChangeListener
            providers.forEach {
                it.notifyChange(key)
            }
        }

    companion object {
        private var defaultInstance: AppPrefs? = null

        fun initDefault(sharedPreferences: SharedPreferences): AppPrefs {
            val instance = AppPrefs(sharedPreferences)
            defaultInstance = instance
            sharedPreferences.registerOnSharedPreferenceChangeListener(
                defaultInstance().onSharedPreferenceChangeListener,
            )
            return instance
        }

        fun defaultInstance(): AppPrefs = defaultInstance
            ?: throw UninitializedPropertyAccessException(
                """
                    Default preferences not initialized! Make sure to call initDefault()
                    before accessing the default preferences.
                """.trimIndent(),
            )
    }

    class Internal(
        shared: SharedPreferences,
    ) : PreferenceDelegateOwner(shared) {
        companion object {
            const val PID = "general__pid"
            const val PREVIOUS_KEYBOARD_IDS = "internal__previous_keyboard_ids"
            const val INITIALIZE_KEYBOARD_ID = "internal__initialize_keyboard_id"
        }

        val pid = int(PID, 0)
        val previousKeyboardIds = string(PREVIOUS_KEYBOARD_IDS, "")
        val initializeKeyboardId = string(INITIALIZE_KEYBOARD_ID, "")
    }

    class General(
        shared: SharedPreferences,
    ) : PreferenceDelegateOwner(shared, R.string.general) {
        companion object {
            const val INLINE_PREEDIT_MODE = "inline_preedit_mode"
            const val ASCII_SWITCH_TIPS = "ascii_switch_tips"
            const val INLINE_SUGGESTIONS = "inline_suggestions"
            const val PREFERRED_VOICE_INPUT = "preferred_voice_input"
            const val HIDE_STATIC_SWITCHER = "hide_static_switcher"
        }

        val inlinePreeditMode = enum(R.string.inline_preedit_mode, INLINE_PREEDIT_MODE, InlinePreeditMode.DISABLE)
        val asciiSwitchTips = switch(R.string.ascii_switch_tips, ASCII_SWITCH_TIPS, true)
        val inlineSuggestions = switch(R.string.inline_suggestions, INLINE_SUGGESTIONS, true)
        val hideStaticSwitcher = bool(HIDE_STATIC_SWITCHER, false)
    }

    class VoiceInput(
        shared: SharedPreferences,
    ) : PreferenceDelegateOwner(shared, R.string.voice_input) {
        companion object {
            const val ASRKB_AIDL_VOICE_INPUT = "asrkb_aidl_voice_input"
            const val ASRKB_AIDL_VOICE_TOOLBAR_BUTTON = "asrkb_aidl_voice_toolbar_button"
            const val PREFERRED_VOICE_INPUT = "preferred_voice_input"
            const val VOICE_ANIMATION_STYLE = "voice_animation_style"
            const val VOICE_NUM_THREADS = "voice_num_threads"
            const val VOICE_SENSITIVITY = "voice_sensitivity"
        }

        enum class VoiceAnimationStyle(override val stringRes: Int) : PreferenceDelegateEnum {
            PARTICLE(R.string.voice_animation_particle),
            SPHERE(R.string.voice_animation_sphere),
        }

        val asrkbAidlVoiceInputEnabled = switch(
            R.string.asrkb_aidl_voice_input,
            ASRKB_AIDL_VOICE_INPUT,
            false,
            R.string.asrkb_aidl_voice_input_summary,
        )
        val asrkbAidlVoiceToolbarButtonEnabled = switch(
            R.string.asrkb_aidl_voice_toolbar_button,
            ASRKB_AIDL_VOICE_TOOLBAR_BUTTON,
            true,
            R.string.asrkb_aidl_voice_toolbar_button_summary,
        )

        val voiceAnimationStyle = enum(R.string.voice_animation_style, VOICE_ANIMATION_STYLE, VoiceAnimationStyle.PARTICLE)

        val preferredVoiceInput = list(
            R.string.preferred_voice_input,
            PREFERRED_VOICE_INPUT,
            InputMethodUtils.BUILTIN_VOICE_INPUT,
            { listOf(InputMethodUtils.BUILTIN_VOICE_INPUT) + InputMethodUtils.voiceInputMethods().map { it.first.packageName } },
            { ctx ->
                listOf(ctx.getString(R.string.builtin_voice_input)) + InputMethodUtils.voiceInputMethods().map { it.first.loadLabel(ctx.packageManager) }
            },
            enableUiOn = { !asrkbAidlVoiceInputEnabled.getValue() },
        )

        val voiceNumThreads = int(
            R.string.voice_num_threads,
            VOICE_NUM_THREADS,
            4,
            1,
            Runtime.getRuntime().availableProcessors().coerceAtLeast(4),
            enableUiOn = { !asrkbAidlVoiceInputEnabled.getValue() },
        )
        val voiceSensitivity = int(
            R.string.voice_sensitivity,
            VOICE_SENSITIVITY,
            5,
            1,
            10,
            enableUiOn = { !asrkbAidlVoiceInputEnabled.getValue() },
        )
    }

    /**
     *  Wrapper class of keyboard settings.
     */
    class Keyboard(
        shared: SharedPreferences,
    ) : PreferenceDelegateOwner(shared, R.string.virtual_keyboard) {
        companion object {
            const val LANDSCAPE_MODE = "keyboard_landscape_mode"
            const val SPLIT_SPACE_PERCENT = "keyboard_split_space"

            const val USE_SOFT_CURSOR = "use_soft_cursor"
            const val HIDE_INPUT_BAR = "hide_input_bar"

            const val SOUND_ON_KEYPRESS = "sound_on_keypress"
            const val KEY_SOUND_VOLUME = "sound_volume"
            const val USE_CUSTOM_SOUND_EFFECT = "custom_sound_effect_enabled"
            const val CUSTOM_SOUND_EFFECT = "custom_sound_effect_name"
            const val VIBRATE_ON_KEY_PRESS = "vibrate_on_key_press"
            const val VIBRATE_ON_KEY_RELEASE = "vibrate_on_key_release"
            const val VIBRATE_ON_KEY_REPEAT = "vibrate_on_key_repeat"
            const val VIBRATION_DURATION = "vibration_duration"
            const val VIBRATION_AMPLITUDE = "vibration_amplitude"
            const val SPEAK_ON_KEYPRESS = "speak_on_keypress"
            const val SPEAK_ON_COMMIT = "speak_on_commit"
            const val POPUP_ON_KEY_PRESS = "show_key_popup"
            const val EXPAND_KEYPRESS_AREA = "expand_keypress_area"
            const val SWIPE_TRAVEL = "key_swipe_travel"
            const val SWIPE_VELOCITY = "key_swipe_velocity"
            const val LONG_PRESS_TIMEOUT = "key_long_press_timeout"
            const val REPEAT_INTERVAL = "key_repeat_interval"
            const val DOUBLE_TAP_TIMEOUT = "key_double_tap_timeout"
            const val SLIDE_STEP_SIZE = "key_slide_step_size"

            const val HOOK_CTRL_A = "hook_ctrl_a"
            const val HOOK_CTRL_CV = "hook_ctrl_cv"
            const val HOOK_CTRL_LR = "hook_ctrl_lr"
            const val HOOK_CTRL_ZY = "hook_ctrl_zy"
            const val HOOK_SHIFT_SPACE = "hook_shift_space"
            const val HOOK_SHIFT_NUM = "hook_shift_num"
            const val HOOK_SHIFT_SYMBOL = "hook_shift_symbol"
            const val HOOK_SHIFT_ARROW = "hook_shift_arrow"

            const val MAX_SPAN_COUNT = "max_span_count"
            const val MAX_SPAN_COUNT_LANDSCAPE = "max_span_count_landscape"
            const val HORIZONTAL_CANDIDATE_MODE = "horizontal_candidate_mode"
        }

        enum class LandscapeMode(override val stringRes: Int) : PreferenceDelegateEnum {
            NEVER(R.string.never),
            LANDSCAPE(R.string.landscape_only),
            WIDE(R.string.wide_or_landscape),
            ALWAYS(R.string.always),
        }

        val landscapeMode = enum(R.string.enable_landscape_mode, LANDSCAPE_MODE, LandscapeMode.NEVER)
        val splitSpacePercent = int(
            R.string.split_space_percent,
            SPLIT_SPACE_PERCENT,
            100,
            0,
            200,
            "%",
        )

        val useSoftCursor = switch(R.string.use_soft_cursor, USE_SOFT_CURSOR, true)

        val hideInputBar = switch(R.string.hide_input_bar, HIDE_INPUT_BAR, false)

        val soundOnKeyPress = switch(R.string.sound_on_keypress, SOUND_ON_KEYPRESS, true)
        val soundVolume = int(
            R.string.sound_volume,
            KEY_SOUND_VOLUME,
            10,
            0,
            100,
            "%",
            defaultLabel = R.string.system_default,
        ) { soundOnKeyPress.getValue() }

        val useCustomSoundEffect = switch(
            R.string.custom_sound_effect_enabled,
            USE_CUSTOM_SOUND_EFFECT,
            false,
        ) { soundOnKeyPress.getValue() }
        val customSoundEffect = string(
            R.string.custom_sound_effect_name,
            CUSTOM_SOUND_EFFECT,
            "",
        ) { soundOnKeyPress.getValue() && useCustomSoundEffect.getValue() }

        val vibrateOnKeyPress = switch(R.string.vibrate_on_key_press, VIBRATE_ON_KEY_PRESS, true)
        val vibrateOnKeyRelease = switch(
            R.string.vibrate_on_key_release,
            VIBRATE_ON_KEY_RELEASE,
            false,
        ) { vibrateOnKeyPress.getValue() }

        val vibrateOnKeyRepeat = switch(
            R.string.vibrate_on_key_repeat,
            VIBRATE_ON_KEY_REPEAT,
            true,
        ) { vibrateOnKeyPress.getValue() }

        val vibrationDuration = int(
            R.string.vibration_duration,
            VIBRATION_DURATION,
            0,
            0,
            100,
            "ms",
            defaultLabel = R.string.system_default,
        ) { vibrateOnKeyPress.getValue() }

        val vibrationAmplitude = int(
            R.string.vibration_amplitude,
            VIBRATION_AMPLITUDE,
            0,
            0,
            255,
            defaultLabel = R.string.system_default,
        ) { vibrateOnKeyPress.getValue() }

        val speakOnKeyPress = switch(R.string.speak_on_keypress, SPEAK_ON_KEYPRESS, false)
        val speakOnCommit = switch(R.string.speak_on_commit, SPEAK_ON_COMMIT, false)
        val popupOnKeyPress = switch(R.string.popup_on_key_press, POPUP_ON_KEY_PRESS, false)
        val expandKeypressArea = switch(R.string.expand_keypress_area_to_edge, EXPAND_KEYPRESS_AREA, false)
        val swipeTravel = int(
            R.string.key_swipe_travel,
            SWIPE_TRAVEL,
            60,
            0,
            400,
            "dp",
            10,
            R.string.disable,
            useMinAsDefault = true,
        )

        val swipeVelocity = int(
            R.string.key_swipe_velocity,
            SWIPE_VELOCITY,
            0,
            0,
            10000,
            "dp/s",
            100,
            R.string.disable,
        )

        val longPressTimeout = int(
            R.string.key_long_press_timeout,
            LONG_PRESS_TIMEOUT,
            300,
            100,
            1000,
            "ms",
            10,
        )

        val repeatInterval = int(
            R.string.key_repeat_interval,
            REPEAT_INTERVAL,
            30,
            10,
            100,
            "ms",
            10,
        )

        val doubleTapTimeout = int(
            R.string.key_double_tap_timeout,
            DOUBLE_TAP_TIMEOUT,
            300,
            100,
            1000,
            "ms",
            10,
        )

        val slideStepSize = int(
            R.string.key_slide_step_size,
            SLIDE_STEP_SIZE,
            24,
            1,
            100,
            "dp",
        )

        val horizontalCandidateMode = enum(R.string.horizontal_candidate_style, HORIZONTAL_CANDIDATE_MODE, CompactCandidateMode.AUTO_FILL)

        val maxSpanCount = int(
            R.string.max_span_count,
            MAX_SPAN_COUNT,
            5,
            1,
            10,
            enableUiOn = {
                shared.getString(HORIZONTAL_CANDIDATE_MODE, null) ==
                    CompactCandidateMode.AUTO_FILL.name
            },
        )

        val maxSpanCountLandscape = int(
            R.string.max_span_count_landscape,
            MAX_SPAN_COUNT_LANDSCAPE,
            5,
            4,
            12,
            enableUiOn = {
                shared.getString(HORIZONTAL_CANDIDATE_MODE, null) ==
                    CompactCandidateMode.AUTO_FILL.name
            },
        )

        val hookCtrlA = switch(R.string.hook_ctrl_a, HOOK_CTRL_A, false)
        val hookCtrlCV = switch(R.string.hook_ctrl_cv, HOOK_CTRL_CV, false)
        val hookCtrlLR = switch(R.string.hook_ctrl_lr, HOOK_CTRL_LR, false)
        val hookCtrlZY = switch(R.string.hook_ctrl_zy, HOOK_CTRL_ZY, false)
        val hookShiftSpace = switch(R.string.hook_shift_space, HOOK_SHIFT_SPACE, false)
        val hookShiftNum = switch(R.string.hook_shift_num, HOOK_SHIFT_NUM, true)
        val hookShiftSymbol = switch(R.string.hook_shift_symbol, HOOK_SHIFT_SYMBOL, true)
        val hookShiftArrow = switch(R.string.hook_shift_arrow, HOOK_SHIFT_ARROW, true)
    }

    class Candidates(
        shared: SharedPreferences,
    ) : PreferenceDelegateOwner(shared, R.string.candidates_window) {
        companion object {
            const val MODE = "show_candidates_window"
            const val LAYOUT = "candidates_layout"
            const val POSITION = "candidates_window_position"
            const val DISABLE_WINDOW_ON_LANDSCAPE = "disable_window_on_landscape"
        }

        val mode = enum(R.string.show_candidates_window, MODE, PopupCandidatesMode.SYSTEM_DEFAULT)
        val layout = enum(R.string.candidates_layout, LAYOUT, PopupCandidatesLayout.AUTOMATIC)
        val position = enum(R.string.candidates_window_position, POSITION, PopupPosition.BOTTOM_LEFT)
        val disableWindowOnLandscape = switch(R.string.disable_window_on_landscape, DISABLE_WINDOW_ON_LANDSCAPE, false)
    }

    /**
     *  Wrapper class of profile settings.
     */
    class Profile(
        shared: SharedPreferences,
    ) : PreferenceDelegateOwner(shared) {
        companion object {
            const val USER_DATA_DIR = "profile_user_data_dir"
            const val PERIODIC_BACKGROUND_SYNC = "periodic_background_sync"
            const val PERIODIC_BACKGROUND_SYNC_INTERVAL = "periodic_background_sync_interval"
            const val LAST_BACKGROUND_SYNC_STATUS = "last_background_sync_status"
            const val LAST_BACKGROUND_SYNC_TIME = "last_background_sync_time"
        }

        val userDataDir = string(USER_DATA_DIR, DataManager.defaultDataDir.path)
        val periodicBackgroundSync = bool(PERIODIC_BACKGROUND_SYNC, false)
        val periodicBackgroundSyncInterval = int(PERIODIC_BACKGROUND_SYNC_INTERVAL, 720)
        val lastBackgroundSyncStatus = bool(LAST_BACKGROUND_SYNC_STATUS, false)
        val lastBackgroundSyncTime = long(LAST_BACKGROUND_SYNC_TIME, 0L)
    }

    class Clipboard(
        shared: SharedPreferences,
    ) : PreferenceDelegateOwner(shared, R.string.clipboard) {
        companion object {
            const val CLIPBOARD_LISTENING = "clipboard_listening"
            const val CLIPBOARD_LIMIT = "clipboard_clipboard_limit"
            const val CLIPBOARD_COMPARE_RULES = "clipboard_clipboard_compare"
            const val CLIPBOARD_OUTPUT_RULES = "clipboard_clipboard_output"
            const val CLIPBOARD_EXTRACT_RULES = "clipboard_extract_rules"
            const val CLIPBOARD_SUGGESTION = "clipboard_suggestion"
            const val CLIPBOARD_SUGGESTION_TIMEOUT = "clipboard_suggestion_timeout"
            const val CLIPBOARD_RETURN_AFTER_PASTE = "clipboard_return_after_paste"
        }
        val clipboardListening = switch(R.string.clipboard_listening, CLIPBOARD_LISTENING, true)
        val clipboardLimit = int(
            R.string.clipboard_limit,
            CLIPBOARD_LIMIT,
            1024,
        ) { clipboardListening.getValue() }
        val clipboardCompareRules = editText(
            R.string.clipboard_compare_rules,
            CLIPBOARD_COMPARE_RULES,
            "",
            R.string.a_regular_expression_per_line,
            onBindEditText = {
                it.typeface = Typeface.MONOSPACE
                it.textSize = 15f
            },
            enableUiOn = { clipboardListening.getValue() },
            summaryCountFormat = R.string.clipboard_rules_count,
        )
        val clipboardOutputRules = editText(
            R.string.clipboard_output_rules,
            CLIPBOARD_OUTPUT_RULES,
            "",
            R.string.a_regular_expression_per_line,
            onBindEditText = {
                it.typeface = Typeface.MONOSPACE
                it.textSize = 15f
            },
            enableUiOn = { clipboardListening.getValue() },
            summaryCountFormat = R.string.clipboard_rules_count,
        )
        val clipboardExtractRules = editText(
            R.string.clipboard_extract_rules,
            CLIPBOARD_EXTRACT_RULES,
            "",
            R.string.a_regular_expression_per_line,
            onBindEditText = {
                it.typeface = Typeface.MONOSPACE
                it.textSize = 15f
            },
            enableUiOn = { clipboardListening.getValue() },
            summaryCountFormat = R.string.clipboard_rules_count,
        )
        val clipboardSuggestion = switch(
            R.string.clipboard_suggestion,
            CLIPBOARD_SUGGESTION,
            true,
        ) { clipboardListening.getValue() }
        val clipboardSuggestionTimeout = int(
            R.string.clipboard_suggestion_timeout,
            CLIPBOARD_SUGGESTION_TIMEOUT,
            60,
            0,
            100,
            "s",
        ) { clipboardListening.getValue() && clipboardSuggestion.getValue() }
        val clipboardReturnAfterPaste = switch(
            R.string.clipboard_return_after_paste,
            CLIPBOARD_RETURN_AFTER_PASTE,
            true,
        ) { clipboardListening.getValue() }
    }

    class Advanced(
        shared: SharedPreferences,
    ) : PreferenceDelegateOwner(shared, R.string.advanced) {
        companion object {
            const val UI_MODE = "ui_mode"
            const val SHOW_APP_ICON = "show_app_icon"
        }

        enum class UiMode(override val stringRes: Int) : PreferenceDelegateEnum {
            AUTO(R.string.automatic),
            LIGHT(R.string.light),
            DARK(R.string.dark),
        }

        val uiMode = enum(R.string.ui_mode, UI_MODE, UiMode.AUTO)
        val showAppIcon = switch(
            R.string.show_app_icon,
            SHOW_APP_ICON,
            true,
            R.string.only_available_on_some_roms,
        )
    }

    class Wanxiang(
        shared: SharedPreferences,
    ) : PreferenceDelegateOwner(shared, R.string.wanxiang_updater) {
        companion object {
            const val UPDATE_CHANNEL = "wanxiang_update_channel"
            const val IS_PRO = "wanxiang_is_pro"
            const val AUX_SCHEME = "wanxiang_aux_scheme"
            const val DOWNLOAD_SOURCE = "wanxiang_download_source"
            const val GH_TOKEN = "wanxiang_gh_token"
            const val EXCLUDE_RULES = "wanxiang_exclude_rules"
            const val AUTO_CHECK = "wanxiang_auto_check"
            const val CHECK_INTERVAL = "wanxiang_check_interval"
        }

        val updateChannel = list(
            R.string.wanxiang_update_channel,
            UPDATE_CHANNEL,
            "Stable",
            { listOf("Stable", "Preview") },
            { ctx ->
                listOf(
                    ctx.getString(R.string.wanxiang_stable),
                    ctx.getString(R.string.wanxiang_preview),
                )
            },
        )
        val isPro = list(
            R.string.wanxiang_scheme_version,
            IS_PRO,
            "pro",
            { listOf("pro", "base") },
            { listOf("Pro", "Base") },
        )
        val auxScheme = list(
            R.string.wanxiang_aux_scheme,
            AUX_SCHEME,
            "zrm",
            { listOf("zrm", "wx", "flypy", "moqi", "hanxin", "shouyou", "shyplus", "tiger", "wubi") },
            {
                listOf(
                    "自然码", "万象", "小鹤", "墨奇",
                    "汉心", "首右", "首右+",
                    "虎码", "五笔",
                )
            },
            enableUiOn = { isPro.getValue() == "pro" },
        )
        val downloadSource = list(
            R.string.wanxiang_download_source,
            DOWNLOAD_SOURCE,
            "CNB",
            { listOf("CNB", "GitHub") },
            { listOf("CNB", "GitHub") },
        )
        val ghToken = editText(
            R.string.wanxiang_github_token,
            GH_TOKEN,
            "",
            enableUiOn = { downloadSource.getValue() == "GitHub" },
        )
        val autoCheck = switch(R.string.wanxiang_auto_check, AUTO_CHECK, false)
        val checkInterval = int(
            R.string.wanxiang_check_interval,
            CHECK_INTERVAL,
            12,
            3,
            24,
            "h",
        ) { autoCheck.getValue() }
        val excludeRules = editText(
            R.string.wanxiang_exclude_rules,
            EXCLUDE_RULES,
            DefaultExcludeRules,
            R.string.wanxiang_exclude_hint,
            onBindEditText = {
                it.typeface = Typeface.MONOSPACE
                it.textSize = 15f
            },
            summaryCountFormat = R.string.wanxiang_exclude_rules_count,
        )
    }
}
