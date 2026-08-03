/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.keyboard

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.view.KeyEvent
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.osfans.trime.R
import com.osfans.trime.core.KeyModifier
import com.osfans.trime.core.KeyModifiers
import com.osfans.trime.core.RimeApi
import com.osfans.trime.core.RimeKeyEvent
import com.osfans.trime.core.RimeKeyMapping
import com.osfans.trime.daemon.RimeDaemon
import com.osfans.trime.daemon.RimeSession
import com.osfans.trime.daemon.launchOnReady
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.data.theme.ColorManager
import com.osfans.trime.data.theme.KeyActionManager
import com.osfans.trime.data.theme.ThemeManager
import com.osfans.trime.ime.bar.InputBarDelegate
import com.osfans.trime.ime.clipboard.ClipboardWindow
import com.osfans.trime.ime.core.TrimeInputMethodService
import com.osfans.trime.ime.dependency.InputDependencyManager
import com.osfans.trime.ime.dialog.EnabledSchemaPickerDialog
import com.osfans.trime.ime.switches.SwitchOptionWindow
import com.osfans.trime.ime.symbol.LiquidData
import com.osfans.trime.ime.symbol.LiquidWindow
import com.osfans.trime.ime.window.BoardWindowManager
import com.osfans.trime.ui.main.settings.ColorPickerDialog
import com.osfans.trime.ui.main.settings.SoundEffectPickerDialog
import com.osfans.trime.ui.main.settings.ThemePickerDialog
import com.osfans.trime.util.AppUtils
import com.osfans.trime.util.buildIntentFromAction
import com.osfans.trime.util.buildIntentFromArgument
import com.osfans.trime.util.customFormatDateTime
import com.osfans.trime.util.isAsciiPrintable
import com.osfans.trime.util.toast
import kotlinx.coroutines.launch
import org.kodein.di.instance
import splitties.systemservices.clipboardManager
import splitties.systemservices.inputMethodManager
import timber.log.Timber

class CommonKeyboardActionListener {
    private val di = InputDependencyManager.getInstance().di

    private val context: Context by di.instance()
    private val service: TrimeInputMethodService by di.instance()
    private val rime: RimeSession by di.instance()
    private val windowManager: BoardWindowManager by di.instance()
    private val keyboardWindow: KeyboardWindow by di.instance()
    private val liquidWindow: LiquidWindow by di.instance()
    private val inputBarDelegate: InputBarDelegate by di.instance()

    private val prefs = AppPrefs.defaultInstance()

    private var shouldReleaseKey: Boolean = false

    private fun showDialog(dialog: suspend (RimeApi) -> Dialog) {
        rime.launchOnReady { api ->
            service.lifecycleScope.launch {
                service.showDialog(dialog(api))
            }
        }
    }

    private fun showThemePicker() {
        showDialog { api ->
            ThemePickerDialog.build(service.lifecycleScope, context) {
                api.commitComposition()
            }
        }
    }

    private fun showColorPicker() {
        showDialog { api ->
            ColorPickerDialog.build(service.lifecycleScope, context) {
                api.commitComposition()
            }
        }
    }

    private fun showSoundEffectPicker() {
        showDialog {
            SoundEffectPickerDialog.build(service.lifecycleScope, context)
        }
    }

    private fun showEnabledSchemaPicker() {
        showDialog { api ->
            EnabledSchemaPickerDialog.build(api, service.lifecycleScope, context)
        }
    }

    private fun expandActiveText(input: String): String = if (input.matches(PLACEHOLDER_PATTERN)) {
        input.format(
            service.getActiveText(1),
            service.getActiveText(2),
            service.getActiveText(3),
            service.getActiveText(4),
        )
    } else {
        input
    }

    val listener by lazy {
        object : KeyboardActionListener {
            override fun onPress(keyEventCode: Int) {
                InputFeedbackManager.run {
                    keyPressSound(keyEventCode)
                    keyPressSpeak(keyEventCode)
                }
            }

            override fun onRelease(keyEventCode: Int) {
                if (shouldReleaseKey) {
                    // FIXME: 释放按键可能不对
                    val value = RimeKeyMapping
                        .keyCodeToVal(keyEventCode)
                        .takeIf { it != RimeKeyMapping.RimeKey_VoidSymbol }
                        ?: RimeKeyEvent.getKeycodeByName(KeyCode.codeToKeyName(keyEventCode) ?: "VoidSymbol")
                    if (value != RimeKeyMapping.RimeKey_VoidSymbol) {
                        service.postRimeJob {
                            processKey(value, KeyModifier.Release.modifier)
                        }
                    }
                }
            }

            override fun onAction(action: KeyAction, key: Key?, behavior: KeyBehavior) {
                val shouldHandle = when {
                    action.commit.isNotEmpty() -> {
                        service.commitText(action.commit)
                        false
                    }
                    KeyboardWindow.currentKeyboard.let { keyboard ->
                        action.getText(keyboard).isNotEmpty()
                    } -> {
                        onText(action.getText(KeyboardWindow.currentKeyboard))
                        false
                    }
                    else -> true
                }

                if (shouldHandle) {
                    when (action.code) {
                        KeyEvent.KEYCODE_SWITCH_CHARSET -> handleSwitchCharset(action)
                        KeyEvent.KEYCODE_EISU -> {
                            if (KeyboardWindow.currentKeyboard.isDynamicMode) {
                                KeyboardWindow.dynamicController?.reset()
                            }
                            keyboardWindow.switchKeyboard(action.select)
                        }
                        KeyEvent.KEYCODE_LANGUAGE_SWITCH -> handleLanguageSwitch(action)
                        KeyEvent.KEYCODE_FUNCTION -> handleFunctionCommand(action)
                        KeyEvent.KEYCODE_SETTINGS -> handleSettings(action)
                        KeyEvent.KEYCODE_PROG_RED -> showColorPicker()
                        KeyEvent.KEYCODE_MENU -> showEnabledSchemaPicker()
                        KeyEvent.KEYCODE_VOICE_ASSIST -> switchToVoiceInputMethod()
                        else -> handleDefaultKeyAction(action)
                    }
                }

                val sc = KeyboardWindow.dynamicController
                if (sc != null) {
                    val isSwipe = behavior == KeyBehavior.SWIPE_UP ||
                        behavior == KeyBehavior.SWIPE_DOWN ||
                        behavior == KeyBehavior.SWIPE_LEFT ||
                        behavior == KeyBehavior.SWIPE_RIGHT
                    val skip = action.code in setOf(
                        KeyEvent.KEYCODE_SWITCH_CHARSET,
                        KeyEvent.KEYCODE_EISU,
                        KeyEvent.KEYCODE_LANGUAGE_SWITCH,
                        KeyEvent.KEYCODE_FUNCTION,
                        KeyEvent.KEYCODE_SETTINGS,
                        KeyEvent.KEYCODE_PROG_RED,
                        KeyEvent.KEYCODE_MENU,
                        KeyEvent.KEYCODE_VOICE_ASSIST,
                    ) || action.code == KeyEvent.KEYCODE_DEL ||
                        action.code == KeyEvent.KEYCODE_SPACE ||
                        action.code == KeyEvent.KEYCODE_ENTER ||
                        action.code == KeyEvent.KEYCODE_TAB ||
                        action.code == KeyEvent.KEYCODE_DPAD_LEFT ||
                        action.code == KeyEvent.KEYCODE_DPAD_RIGHT ||
                        action.code == KeyEvent.KEYCODE_MOVE_HOME ||
                        action.code == KeyEvent.KEYCODE_MOVE_END ||
                        isSwipe
                    if (!skip) {
                        key?.dynamicTarget?.let { sc.onInput(it) }
                    }
                    if (!sc.isEmpty && KeyboardWindow.currentKeyboard.isDynamicMode &&
                        action.code in setOf(KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_ENTER)
                    ) {
                        sc.reset()
                        keyboardWindow.switchKeyboard(sc.originalKeyboard)
                    }
                }
            }

            private fun handleSwitchCharset(action: KeyAction) {
                val option = action.toggle.ifEmpty { return }

                rime.launchOnReady { api ->
                    service.lifecycleScope.launch {
                        val isEnabled = api.getRuntimeOption(option)
                        val isComposing = api.statusCached.isComposing
                        api.setRuntimeOption(option, !isEnabled)
                        if (option == "ascii_mode" && isComposing) {
                            api.getRawInput().takeIf { it.isNotEmpty() }?.let {
                                service.commitText(it)
                                api.clearComposition()
                            }
                        }
                    }
                }
            }

            private fun handleLanguageSwitch(action: KeyAction) {
                when {
                    action.select == ".next" -> service.switchToNextIme()
                    action.select.isNotEmpty() -> service.switchToPrevIme()
                    else -> inputMethodManager.showInputMethodPicker()
                }
            }

            private fun handleFunctionCommand(action: KeyAction) {
                val arg = expandActiveText(action.option)

                when (action.command) {
                    "liquid_keyboard" -> handleLiquidKeyboard(arg)
                    "menu_keyboard" -> windowManager.attachWindow(SwitchOptionWindow())
                    "clipboard_window" -> handleClipboardWindow(arg)
                    "set_color_scheme" -> handleColorScheme(arg)
                    "set_theme" -> handleTheme(arg)
                    "set_schema" -> handleSetSchema(arg)
                    "broadcast" -> service.sendBroadcast(Intent(arg))
                    "clipboard" -> handleClipboard()
                    "commit" -> service.commitText(arg)
                    "date" -> service.commitText(customFormatDateTime(arg))
                    "run" -> handleRunCommand(arg)
                    "apply" -> handleApplyCommand(arg)
                    "share_text" -> service.shareText()
                    "select_candidate" -> handleSelectCandidate(arg)
                    "t9_clear" -> {
                        KeyboardWindow.t9Controller?.clear()
                        rime.launchOnReady { api ->
                            service.lifecycleScope.launch {
                                api.clearComposition()
                            }
                        }
                    }
                    "dynamic_clear" -> {
                        KeyboardWindow.dynamicController?.clear()
                        rime.launchOnReady { api ->
                            service.lifecycleScope.launch {
                                api.clearComposition()
                            }
                        }
                    }
                    else -> handleIntentAction(action.command, arg)
                }
            }

            private fun handleLiquidKeyboard(arg: String) {
                // for compatibility
                if (arg == "剪贴" || arg == "clipboard") {
                    windowManager.attachWindow(ClipboardWindow())
                    return
                }
                val liquidTagList = LiquidData.getTagList()
                val index = liquidTagList.indexOfFirst { tag ->
                    tag.id == arg || runCatching {
                        LiquidData.Type.valueOf(arg.uppercase())
                    }.getOrNull() == tag.type
                }

                if (index >= 0) {
                    windowManager.attachWindow(LiquidWindow)
                    liquidWindow.setDataByIndex(index)
                } else {
                    windowManager.attachWindow(KeyboardWindow)
                }
            }

            private fun handleClipboardWindow(arg: String) {
                val category = when (arg.toIntOrNull()) {
                    0 -> com.osfans.trime.data.db.ClipboardCategory.All
                    1 -> com.osfans.trime.data.db.ClipboardCategory.Favorites
                    else -> com.osfans.trime.data.db.ClipboardCategory.All
                }
                windowManager.attachWindow(ClipboardWindow(category))
            }

            private fun handleColorScheme(arg: String) {
                ThemeManager.activeTheme.colorSchemes
                    .find { it.id == arg }
                    ?.let { ColorManager.setColorScheme(it) }
            }

            private fun handleSetSchema(arg: String) {
                rime.launchOnReady { api ->
                    api.selectSchemaCrossPackage(arg)
                }
            }

            private fun handleTheme(arg: String) {
                if (arg == $$"$reload") {
                    ThemeManager.selectTheme(ThemeManager.prefs.selectedTheme.getValue(), forceReload = true)
                } else {
                    ThemeManager.getAllThemes()
                        .find { it.name.equals(arg, ignoreCase = true) }?.let {
                            ThemeManager.selectTheme(it.configId)
                        }
                }
            }

            private fun handleClipboard() {
                clipboardManager.primaryClip
                    ?.getItemAt(0)
                    ?.coerceToText(service)
                    ?.let { service.commitText(it.toString()) }
            }

            private fun handleRunCommand(arg: String) {
                buildIntentFromArgument(arg)?.let { intent ->
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_HISTORY
                    service.startActivity(intent)
                }
            }

            private fun handleApplyCommand(arg: String) {
                when (arg) {
                    "DEPLOY" -> {
                        Timber.i("try to start maintenance via command ...")
                        rime.launchOnReady { api -> api.deploy() }
                    }
                    "RESTART_RIME" -> {
                        Timber.i("try to restart rime via command ...")
                        RimeDaemon.restartRime()
                    }
                    "SYNC_USER_DATA" -> {
                        Timber.i("try to sync rime user data via command ...")
                        rime.launchOnReady { api -> api.syncUserData() }
                    }
                    "UPDATE_CONFIG" -> {
                        Timber.i("try to update rime config via command ...")
                        rime.launchOnReady { api ->
                            api.updateConfig()
                            service.lifecycleScope.launch {
                                Toast.makeText(service, R.string.done, Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    else -> Timber.w("Unknown apply method: $arg")
                }
            }

            private fun handleIntentAction(command: String, arg: String) {
                buildIntentFromAction(command, arg)?.let { intent ->
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_HISTORY
                    service.startActivity(intent)
                }
            }

            private fun handleSelectCandidate(arg: String) {
                val index = arg.toIntOrNull() ?: return
                rime.launchOnReady { api ->
                    service.lifecycleScope.launch {
                        api.selectCandidate(index, false)
                    }
                }
            }

            private fun handleSettings(action: KeyAction) {
                when (action.option) {
                    "theme" -> showThemePicker()
                    "color" -> showColorPicker()
                    "schema" -> AppUtils.launchMainToSchemaList(context)
                    "sound" -> showSoundEffectPicker()
                    else -> AppUtils.launchMainActivity(service)
                }
            }

            private fun switchToVoiceInputMethod() {
                inputBarDelegate.startVoiceHoldSession()
            }

            private fun handleDefaultKeyAction(action: KeyAction) {
                val shouldHookShiftKey = when {
                    prefs.keyboard.hookShiftSpace.getValue() && action.code == KeyEvent.KEYCODE_SPACE -> true
                    prefs.keyboard.hookShiftNum.getValue() && action.code in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> true
                    prefs.keyboard.hookShiftSymbol.getValue() && action.code in KeyEvent.KEYCODE_GRAVE..KeyEvent.KEYCODE_SLASH -> true
                    prefs.keyboard.hookShiftSymbol.getValue() && action.code in setOf(KeyEvent.KEYCODE_COMMA, KeyEvent.KEYCODE_PERIOD) -> true
                    else -> false
                }

                if (action.modifier == 0 && KeyboardWindow.currentKeyboard.isOnlyShiftOn && shouldHookShiftKey) {
                    onKey(action.code, 0)
                    return
                }

                val modifier = when {
                    action.modifier == 0 -> KeyboardWindow.currentKeyboard.modifier
                    (action.modifier and KeyEvent.META_CTRL_ON) != 0 && isNavigationKey(action.code) ->
                        action.modifier or KeyboardWindow.currentKeyboard.modifier
                    else -> action.modifier
                }

                onKey(action.code, modifier)
            }

            private fun isNavigationKey(keyCode: Int): Boolean = keyCode in KeyEvent.KEYCODE_DPAD_UP..KeyEvent.KEYCODE_DPAD_RIGHT ||
                keyCode == KeyEvent.KEYCODE_MOVE_HOME ||
                keyCode == KeyEvent.KEYCODE_MOVE_END

            override fun onKey(
                keyEventCode: Int,
                metaState: Int,
            ) {
                shouldReleaseKey = false

                val t9c = KeyboardWindow.t9Controller
                if (t9c != null && KeyboardWindow.currentKeyboard.isT9Mode) {
                    when {
                        keyEventCode in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> {
                            val digit = (keyEventCode - KeyEvent.KEYCODE_0).toString()
                            t9c.onDigitKey(digit)
                        }
                        keyEventCode == KeyEvent.KEYCODE_DEL -> {
                            if (t9c.onBackspace()) {
                                t9c.updateRimeInput()
                                return
                            }
                        }
                        t9c.isSegmentKeyCode(keyEventCode) -> {
                            if (t9c.onSegmentKey()) {
                                return
                            }
                        }
                    }
                }

                val dc = KeyboardWindow.dynamicController

                val name = KeyCode.codeToKeyName(keyEventCode) ?: "VoidSymbol"
                val value = RimeKeyEvent.getKeycodeByName(name)
                val m = if (keyEventCode in KeyEvent.KEYCODE_NUMPAD_0..KeyEvent.KEYCODE_NUMPAD_EQUALS) {
                    metaState or KeyEvent.META_NUM_LOCK_ON
                } else {
                    metaState
                }
                val modifiers = KeyModifiers.fromMetaState(m).modifiers
                service.postRimeJob {
                    if (service.hookKeyboard(keyEventCode, m)) {
                        Timber.d("handleKey: hook")
                        return@postRimeJob
                    }

                    val isDel = keyEventCode == KeyEvent.KEYCODE_DEL
                    val isNavKey = keyEventCode in setOf(
                        KeyEvent.KEYCODE_TAB,
                        KeyEvent.KEYCODE_DPAD_LEFT,
                        KeyEvent.KEYCODE_DPAD_RIGHT,
                        KeyEvent.KEYCODE_MOVE_HOME,
                        KeyEvent.KEYCODE_MOVE_END,
                    )
                    val lenBefore = getRawInput().length
                    dc?.isKeyProcessing = true

                    val handled = processKey(value, modifiers)
                    val lenAfter = getRawInput().length

                    dc?.isKeyProcessing = false

                    if (dc != null) {
                        if (isDel) {
                            if (lenAfter < lenBefore) {
                                val deleted = lenBefore - lenAfter
                                ContextCompat.getMainExecutor(service).execute { dc.onDelete(deleted) }
                            }
                        } else if (lenAfter < lenBefore) {
                            val committed = lenBefore - lenAfter
                            ContextCompat.getMainExecutor(service).execute { dc.trimCommitted(committed) }
                        } else if (isNavKey) {
                            val caretPos = getCaretPos()
                            val keyIdx = dc.keyIndexFromCaret(caretPos)
                            Timber.d("handleKey: nav key, caretPos=$caretPos -> keyIdx=$keyIdx")
                            ContextCompat.getMainExecutor(service).execute { dc.onCursorMoved(keyIdx) }
                        } else if (lenAfter > lenBefore) {
                            val charDiff = lenAfter - lenBefore
                            ContextCompat.getMainExecutor(service).execute { dc.recordCharCount(charDiff) }
                        }
                    }
                    Timber.d("handleKey: lenBefore=$lenBefore, lenAfter=$lenAfter, isDel=$isDel")

                    if (handled) {
                        shouldReleaseKey = true
                        Timber.d("handleKey: processKey")
                        return@postRimeJob
                    }
                    if (AppUtils.launchKeyCategory(service, keyEventCode)) {
                        Timber.d("handleKey: openCategory")
                        return@postRimeJob
                    }
                    // other special cases
                    if (keyEventCode == KeyEvent.KEYCODE_BACK) {
                        service.requestHideSelf(0)
                    }
                    shouldReleaseKey = false
                }
            }

            override fun onText(input: String) {
                if (input.isEmpty()) return
                Timber.d("onText: $input")
                val status = rime.run { statusCached }
                if (!input[0].isAsciiPrintable() && status.isComposing) {
                    service.postRimeJob { commitComposition() }
                }

                val escaped = input.replace("{}", "{braceleft}{braceright}")
                var i = 0
                while (i < escaped.length) {
                    val value = when (val match = TEXT_INPUT_PATTERN.matchEntire(escaped.substring(i))) {
                        match if (match != null) -> match.groupValues[1]
                        else -> escaped[i].toString()
                    }

                    service.postRimeJob {
                        if (value.run { startsWith('{') && endsWith('}') }) {
                            val token = value.removeSurrounding("{", "}")
                            onAction(KeyActionManager.getAction(token))
                        } else if (!value[0].isAsciiPrintable()) {
                            service.commitText(value)
                        } else {
                            simulateKeySequence(value)
                        }
                    }

                    i += value.length
                }
                shouldReleaseKey = false
            }
        }
    }

    companion object {
        /**
         * Regex for combined key events.
         * group(1) captures either:
         *   - a plain prefix (optionally preceded by {Escape}) from the left branch,
         *   - or a standalone {xxx} block from the right branch.
         * The trailing .* consumes the rest of the input without affecting group(1).
         */
        private val TEXT_INPUT_PATTERN = """^((?:\{Escape\})?[^{}]+|\{[^{}]+\}).*$""".toRegex()

        private val PLACEHOLDER_PATTERN = Regex(".*(%([1-4]\\$)?s).*")
    }
}
