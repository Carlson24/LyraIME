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
import com.osfans.trime.data.clipboard.SyncClipboardPrefs
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
            const val FLOATING_KEYBOARD_WIDTH = "floating_keyboard_width"
            const val FLOATING_KEYBOARD_HEIGHT = "floating_keyboard_height"
            const val FLOATING_KEYBOARD_X_PORTRAIT = "floating_keyboard_x_portrait"
            const val FLOATING_KEYBOARD_Y_PORTRAIT = "floating_keyboard_y_portrait"
            const val FLOATING_KEYBOARD_X_LANDSCAPE = "floating_keyboard_x_landscape"
            const val FLOATING_KEYBOARD_Y_LANDSCAPE = "floating_keyboard_y_landscape"
            const val ONE_HAND_ON_RIGHT_PORTRAIT = "one_hand_on_right_portrait"
            const val ONE_HAND_ON_RIGHT_LANDSCAPE = "one_hand_on_right_landscape"
            const val ONE_HAND_WIDTH_PX = "one_hand_width_px"
        }

        val pid = int(PID, 0)
        val previousKeyboardIds = string(PREVIOUS_KEYBOARD_IDS, "")
        val initializeKeyboardId = string(INITIALIZE_KEYBOARD_ID, "")
        val floatingKeyboardWidth = int(FLOATING_KEYBOARD_WIDTH, 0)
        val floatingKeyboardHeight = int(FLOATING_KEYBOARD_HEIGHT, 0)
        val floatingKeyboardXPortrait = int(FLOATING_KEYBOARD_X_PORTRAIT, -1)
        val floatingKeyboardYPortrait = int(FLOATING_KEYBOARD_Y_PORTRAIT, -1)
        val floatingKeyboardXLandscape = int(FLOATING_KEYBOARD_X_LANDSCAPE, -1)
        val floatingKeyboardYLandscape = int(FLOATING_KEYBOARD_Y_LANDSCAPE, -1)
        val oneHandOnRightPortrait = bool(ONE_HAND_ON_RIGHT_PORTRAIT, true)
        val oneHandOnRightLandscape = bool(ONE_HAND_ON_RIGHT_LANDSCAPE, true)
        val oneHandWidthPx = int(ONE_HAND_WIDTH_PX, 0)
    }

    class General(
        shared: SharedPreferences,
    ) : PreferenceDelegateOwner(shared, R.string.general) {
        companion object {
            const val INLINE_PREEDIT_MODE = "inline_preedit_mode"
            const val ASCII_SWITCH_TIPS = "ascii_switch_tips"
            const val INLINE_SUGGESTIONS = "inline_suggestions"
            const val PREFERRED_VOICE_INPUT = "preferred_voice_input"
            const val TEST_INPUT_VISIBLE = "test_input_visible"
            const val TEST_INPUT_EXPANDED = "test_input_expanded"
            const val HIDE_STATIC_SWITCHER = "hide_static_switcher"
        }

        val inlinePreeditMode = enum(R.string.inline_preedit_mode, INLINE_PREEDIT_MODE, InlinePreeditMode.DISABLE)
        val asciiSwitchTips = switch(R.string.ascii_switch_tips, ASCII_SWITCH_TIPS, true)
        val inlineSuggestions = switch(R.string.inline_suggestions, INLINE_SUGGESTIONS, true)
        val testInputVisible = bool(TEST_INPUT_VISIBLE, true)
        val testInputExpanded = bool(TEST_INPUT_EXPANDED, true)
        val hideStaticSwitcher = bool(HIDE_STATIC_SWITCHER, false)
    }

    class VoiceInput(
        shared: SharedPreferences,
    ) : PreferenceDelegateOwner(shared, R.string.voice_input) {
        companion object {
            const val ASRKB_AIDL_VOICE_INPUT = "asrkb_aidl_voice_input"
            const val PREFERRED_VOICE_INPUT = "preferred_voice_input"
            const val VOICE_ANIMATION_STYLE = "voice_animation_style"
            const val VOICE_SENSITIVITY = "voice_sensitivity"
            const val VOICE_CHUNK_SIZE = "voice_chunk_size"
        }

        enum class VoiceAnimationStyle(override val stringRes: Int) : PreferenceDelegateEnum {
            PARTICLE(R.string.voice_animation_particle),
            SPHERE(R.string.voice_animation_sphere),
        }

        enum class VoiceChunkSize(override val stringRes: Int, val ms: Int) : PreferenceDelegateEnum {
            CHUNK_160(R.string.voice_chunk_size_160, 160),
            CHUNK_480(R.string.voice_chunk_size_480, 480),
            CHUNK_960(R.string.voice_chunk_size_960, 960),
            CHUNK_1920(R.string.voice_chunk_size_1920, 1920),
        }

        val asrkbAidlVoiceInputEnabled = switch(
            R.string.asrkb_aidl_voice_input,
            ASRKB_AIDL_VOICE_INPUT,
            false,
            R.string.asrkb_aidl_voice_input_summary,
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

        val voiceSensitivity = int(
            R.string.voice_sensitivity,
            VOICE_SENSITIVITY,
            5,
            1,
            10,
            enableUiOn = { !asrkbAidlVoiceInputEnabled.getValue() },
        )
        val voiceChunkSize = enum(
            R.string.voice_chunk_size,
            VOICE_CHUNK_SIZE,
            VoiceChunkSize.CHUNK_480,
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
            const val HORIZONTAL_CANDIDATE_MODE = "horizontal_candidate_mode"
            const val AUTO_FLOAT_LANDSCAPE = "auto_float_landscape"
        }

        enum class LandscapeMode(override val stringRes: Int) : PreferenceDelegateEnum {
            NEVER(R.string.never),
            LANDSCAPE(R.string.landscape_only),
            WIDE(R.string.wide_or_landscape),
            ALWAYS(R.string.always),
        }

        val landscapeMode = enum(R.string.enable_landscape_mode, LANDSCAPE_MODE, LandscapeMode.NEVER)
        val autoFloatLandscape = switch(R.string.auto_float_landscape, AUTO_FLOAT_LANDSCAPE, false)
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
            const val REVERSE_VERTICAL = "reverse_vertical_candidates"
        }

        val mode = enum(R.string.show_candidates_window, MODE, PopupCandidatesMode.SYSTEM_DEFAULT)
        val layout = enum(R.string.candidates_layout, LAYOUT, PopupCandidatesLayout.AUTOMATIC)
        val position = enum(R.string.candidates_window_position, POSITION, PopupPosition.BOTTOM_LEFT)
        val disableWindowOnLandscape = switch(R.string.disable_window_on_landscape, DISABLE_WINDOW_ON_LANDSCAPE, false)
        val reverseVertical = switch(R.string.vertical_reverse, REVERSE_VERTICAL, false)
    }

    /**
     *  Wrapper class of profile settings.
     */
    class Profile(
        shared: SharedPreferences,
    ) : PreferenceDelegateOwner(shared) {
        companion object {
            const val ACTIVE_PACKAGE_ID = "profile_active_package_id"
            const val USER_DATA_DIR = "profile_user_data_dir"
            const val PERIODIC_BACKGROUND_SYNC = "periodic_background_sync"
            const val PERIODIC_BACKGROUND_SYNC_TIME = "periodic_background_sync_time"
            const val LAST_BACKGROUND_SYNC_STATUS = "last_background_sync_status"
            const val LAST_BACKGROUND_SYNC_TIME = "last_background_sync_time"
            const val LAST_SYNC_SETTINGS_HASH = "last_sync_settings_hash"
            const val WEBDAV_ENABLED = "webdav_enabled"
            const val WEBDAV_URL = "webdav_url"
            const val WEBDAV_USERNAME = "webdav_username"
            const val WEBDAV_PASSWORD = "webdav_password"
            const val WEBDAV_REMOTE_PATH = "webdav_remote_path"
        }

        val userDataDir = string(USER_DATA_DIR, DataManager.defaultDataDir.path)
        val activePackageId = string(ACTIVE_PACKAGE_ID, "")
        val periodicBackgroundSync = bool(PERIODIC_BACKGROUND_SYNC, false)
        val periodicBackgroundSyncTime = string(PERIODIC_BACKGROUND_SYNC_TIME, "02:00")
        val lastBackgroundSyncStatus = bool(LAST_BACKGROUND_SYNC_STATUS, false)
        val lastBackgroundSyncTime = long(LAST_BACKGROUND_SYNC_TIME, 0L)
        val lastSyncSettingsHash = string(LAST_SYNC_SETTINGS_HASH, "")
        val webdavEnabled = bool(WEBDAV_ENABLED, false)
        val webdavUrl = string(WEBDAV_URL, "")
        val webdavUsername = string(WEBDAV_USERNAME, "")
        val webdavPassword = string(WEBDAV_PASSWORD, "")
        val webdavRemotePath = string(WEBDAV_REMOTE_PATH, "Rime")
    }

    class Clipboard(
        shared: SharedPreferences,
    ) : PreferenceDelegateOwner(shared, R.string.clipboard),
        SyncClipboardPrefs {
        enum class SyncServerType(override val stringRes: Int) : PreferenceDelegateEnum {
            SYNC_CLIPBOARD(R.string.sync_clipboard_server_type_syncclipboard),
            WEBDAV(R.string.sync_clipboard_server_type_webdav),
            S3(R.string.sync_clipboard_server_type_s3),
        }

        companion object {
            const val CLIPBOARD_LISTENING = "clipboard_listening"
            const val CLIPBOARD_LIMIT = "clipboard_clipboard_limit"
            const val CLIPBOARD_COMPARE_RULES = "clipboard_clipboard_compare"
            const val CLIPBOARD_OUTPUT_RULES = "clipboard_clipboard_output"
            const val CLIPBOARD_EXTRACT_RULES = "clipboard_extract_rules"
            const val CLIPBOARD_SUGGESTION = "clipboard_suggestion"
            const val CLIPBOARD_SUGGESTION_TIMEOUT = "clipboard_suggestion_timeout"
            const val CLIPBOARD_RETURN_AFTER_PASTE = "clipboard_return_after_paste"
            const val CLIPBOARD_SCREENSHOT_WATCH = "clipboard_screenshot_watch"
            const val CLIPBOARD_MASK_SENSITIVE = "clipboard_mask_sensitive"
            const val CLIPBOARD_SENSITIVE_KEYWORDS = "clipboard_sensitive_keywords"
            const val SYNC_CLIPBOARD_ENABLED = "sync_clipboard_enabled"
            const val SYNC_CLIPBOARD_SERVER_BASE = "sync_clipboard_server_base"
            const val SYNC_CLIPBOARD_USERNAME = "sync_clipboard_username"
            const val SYNC_CLIPBOARD_PASSWORD = "sync_clipboard_password"
            const val SYNC_CLIPBOARD_AUTO_PULL = "sync_clipboard_auto_pull"
            const val SYNC_CLIPBOARD_PULL_INTERVAL_SEC = "sync_clipboard_pull_interval_sec"
            const val SYNC_CLIPBOARD_LAST_UPLOADED_HASH = "sync_clipboard_last_uploaded_hash"
            const val SYNC_CLIPBOARD_LAST_FILE_NAME = "sync_clipboard_last_file_name"
            const val SYNC_CLIPBOARD_SERVER_TYPE = "sync_clipboard_server_type"
            const val SYNC_CLIPBOARD_S3_REGION = "sync_clipboard_s3_region"
            const val SYNC_CLIPBOARD_S3_BUCKET = "sync_clipboard_s3_bucket"
            const val SYNC_CLIPBOARD_S3_OBJECT_PREFIX = "sync_clipboard_s3_object_prefix"
            const val SYNC_CLIPBOARD_S3_FORCE_PATH_STYLE = "sync_clipboard_s3_force_path_style"
            const val SYNC_CLIPBOARD_SIGNALR_ENABLED = "sync_clipboard_signalr_enabled"
            const val SYNC_CLIPBOARD_AUTO_DOWNLOAD_MAX_SIZE = "sync_clipboard_auto_download_max_size"
            const val SYNC_CLIPBOARD_WEBDAV_REMOTE_PATH = "sync_clipboard_webdav_remote_path"
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
        val clipboardScreenshotWatch = switch(
            R.string.clipboard_screenshot_watch,
            CLIPBOARD_SCREENSHOT_WATCH,
            false,
        ) { clipboardListening.getValue() }
        val clipboardMaskSensitive = switch(
            R.string.clipboard_mask_sensitive,
            CLIPBOARD_MASK_SENSITIVE,
            true,
        ) { clipboardListening.getValue() }
        val clipboardSensitiveKeywords = editText(
            R.string.clipboard_sensitive_keywords,
            CLIPBOARD_SENSITIVE_KEYWORDS,
            "",
            R.string.a_regular_expression_per_line,
            onBindEditText = {
                it.typeface = Typeface.MONOSPACE
                it.textSize = 15f
            },
            enableUiOn = { clipboardMaskSensitive.getValue() },
            summaryCountFormat = R.string.clipboard_rules_count,
        )

        val syncEnabled = switch(
            R.string.sync_clipboard_enabled,
            SYNC_CLIPBOARD_ENABLED,
            false,
            R.string.sync_clipboard_enabled_summary,
        )
        val syncServerType = enum(
            R.string.sync_clipboard_server_type,
            SYNC_CLIPBOARD_SERVER_TYPE,
            SyncServerType.SYNC_CLIPBOARD,
            enableUiOn = { syncEnabled.getValue() },
        )
        val syncServerBase = editText(
            R.string.sync_clipboard_server_base,
            SYNC_CLIPBOARD_SERVER_BASE,
            "",
            enableUiOn = { syncEnabled.getValue() },
        )
        val syncUsername = editText(
            R.string.sync_clipboard_username,
            SYNC_CLIPBOARD_USERNAME,
            "",
            enableUiOn = { syncEnabled.getValue() },
        )
        val syncPassword = editText(
            R.string.sync_clipboard_password,
            SYNC_CLIPBOARD_PASSWORD,
            "",
            enableUiOn = { syncEnabled.getValue() },
            password = true,
        )
        val syncWebdavRemotePath = editText(
            R.string.sync_clipboard_webdav_remote_path,
            SYNC_CLIPBOARD_WEBDAV_REMOTE_PATH,
            "",
            enableUiOn = {
                syncEnabled.getValue() && syncServerType.getValue() == SyncServerType.WEBDAV
            },
        )
        val syncAutoPullEnabled = switch(
            R.string.sync_clipboard_auto_pull,
            SYNC_CLIPBOARD_AUTO_PULL,
            true,
            R.string.sync_clipboard_auto_pull_summary,
            enableUiOn = { syncEnabled.getValue() },
        )
        val syncPullIntervalSec = int(
            R.string.sync_clipboard_pull_interval,
            SYNC_CLIPBOARD_PULL_INTERVAL_SEC,
            60,
            1,
            600,
            "s",
            enableUiOn = { syncEnabled.getValue() && syncAutoPullEnabled.getValue() },
        )
        val syncLastUploadedHash = string(SYNC_CLIPBOARD_LAST_UPLOADED_HASH, "")
        val syncLastFileName = string(SYNC_CLIPBOARD_LAST_FILE_NAME, "")
        val syncS3Region = string(SYNC_CLIPBOARD_S3_REGION, "")
        val syncS3BucketName = string(SYNC_CLIPBOARD_S3_BUCKET, "")
        val syncS3ObjectPrefix = string(SYNC_CLIPBOARD_S3_OBJECT_PREFIX, "")
        val syncS3ForcePathStyle = bool(SYNC_CLIPBOARD_S3_FORCE_PATH_STYLE, false)
        val syncSignalREnabled = bool(SYNC_CLIPBOARD_SIGNALR_ENABLED, true)
        val syncAutoDownloadMaxSize = int(SYNC_CLIPBOARD_AUTO_DOWNLOAD_MAX_SIZE, 5)

        override val syncClipboardEnabled: Boolean get() = syncEnabled.getValue()
        override val syncClipboardServerBase: String get() = syncServerBase.getValue()
        override val syncClipboardUsername: String get() = syncUsername.getValue()
        override val syncClipboardPassword: String get() = syncPassword.getValue()
        override val syncClipboardAutoPullEnabled: Boolean get() = syncAutoPullEnabled.getValue()
        override val syncClipboardPullIntervalSec: Int get() = syncPullIntervalSec.getValue()
        override var syncClipboardLastUploadedHash: String
            get() = syncLastUploadedHash.getValue()
            set(value) = syncLastUploadedHash.setValue(value)
        override var syncClipboardLastFileName: String
            get() = syncLastFileName.getValue()
            set(value) = syncLastFileName.setValue(value)
        override val syncClipboardServerType: String get() = syncServerType.getValue().name.lowercase()
        override val syncClipboardS3Region: String get() = syncS3Region.getValue()
        override val syncClipboardS3BucketName: String get() = syncS3BucketName.getValue()
        override val syncClipboardS3ObjectPrefix: String get() = syncS3ObjectPrefix.getValue()
        override val syncClipboardS3ForcePathStyle: Boolean get() = syncS3ForcePathStyle.getValue()
        override val syncClipboardSignalREnabled: Boolean get() = syncSignalREnabled.getValue()
        override val syncClipboardAutoDownloadMaxSize: Long get() = syncAutoDownloadMaxSize.getValue().toLong()
        override val syncClipboardWebdavRemotePath: String get() = syncWebdavRemotePath.getValue()
    }

    class Advanced(
        shared: SharedPreferences,
    ) : PreferenceDelegateOwner(shared, R.string.advanced) {
        companion object {
            const val UI_MODE = "ui_mode"
            const val SHOW_APP_ICON = "show_app_icon"
            const val IGNORE_SYSTEM_GESTURE_INSETS = "ignore_system_gesture_insets"
            const val CUSTOM_GESTURE_INSET_HEIGHT = "custom_gesture_inset_height"
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
        val ignoreSystemGestureInsets = switch(
            R.string.ignore_system_gesture_insets,
            IGNORE_SYSTEM_GESTURE_INSETS,
            false,
        )
        val customGestureInsetHeight = int(
            R.string.custom_gesture_inset_height,
            CUSTOM_GESTURE_INSET_HEIGHT,
            0,
            0,
            50,
            "dp",
            defaultLabel = R.string.custom_gesture_inset_height_summary,
            enableUiOn = { !ignoreSystemGestureInsets.getValue() },
        )
    }
}
