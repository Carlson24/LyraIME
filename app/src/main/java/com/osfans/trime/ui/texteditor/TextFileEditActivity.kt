/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package com.osfans.trime.ui.texteditor

import android.content.ComponentCallbacks2
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.lifecycleScope
import com.osfans.trime.R
import com.osfans.trime.daemon.RimeDaemon
import com.osfans.trime.util.AppUtils
import io.github.rosemoe.sora.event.PublishSearchResultEvent
import io.github.rosemoe.sora.event.ScrollEvent
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.EditorSearcher
import io.github.rosemoe.sora.widget.SymbolPairMatch
import io.github.rosemoe.sora.widget.component.EditorAutoCompletion
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import splitties.views.topPadding
import timber.log.Timber
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.channels.FileChannel
import java.nio.charset.CharsetDecoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

class TextFileEditActivity : AppCompatActivity() {

    private val editor: ArrowTabCodeEditor by lazy { findViewById(R.id.editor) }
    private val searchBar: LinearLayout by lazy { findViewById(R.id.searchBar) }
    private val findInput: EditText by lazy { findViewById(R.id.findInput) }
    private val findPrev: View by lazy { findViewById(R.id.findPrev) }
    private val findNext: View by lazy { findViewById(R.id.findNext) }
    private val searchClose: View by lazy { findViewById(R.id.searchClose) }
    private val replaceInput: EditText by lazy { findViewById(R.id.replaceInput) }
    private val replaceOne: View by lazy { findViewById(R.id.replaceOne) }
    private val replaceAll: View by lazy { findViewById(R.id.replaceAll) }
    private val matchInfo: TextView by lazy { findViewById(R.id.matchInfo) }
    private val keyBar: LinearLayout by lazy { findViewById(R.id.keyBar) }
    private val keySave: View by lazy { findViewById(R.id.keySave) }
    private val keyUndo: View by lazy { findViewById(R.id.keyUndo) }
    private val keyRedo: View by lazy { findViewById(R.id.keyRedo) }
    private val keyTab: View by lazy { findViewById(R.id.keyTab) }
    private val keyHome: View by lazy { findViewById(R.id.keyHome) }
    private val keyEnd: View by lazy { findViewById(R.id.keyEnd) }
    private val keyLeft: View by lazy { findViewById(R.id.keyLeft) }
    private val keyUp: View by lazy { findViewById(R.id.keyUp) }
    private val keyDown: View by lazy { findViewById(R.id.keyDown) }
    private val keyRight: View by lazy { findViewById(R.id.keyRight) }
    private val keysScroll: View by lazy { findViewById(R.id.keysScroll) }
    private val keysContainer: ViewGroup by lazy { findViewById(R.id.keysContainer) }

    private var isViewReady: Boolean = false
    private lateinit var docUri: Uri
    private lateinit var prefs: SharedPreferences
    private var displayName: String = ""
    private var originalText: String = ""
    private var wordWrap: Boolean = true
    private var showWhitespace: Boolean = false
    private var useTab: Boolean = true
    private var fileSizeBytes: Long = 0L
    private var isLargeFile: Boolean = false
    private var deferredSyntaxHighlightPending: Boolean = false
    private var lowMemoryMode: Boolean = false
    private var lowMemoryNoticeShown: Boolean = false
    private var largeFilePager: LargeFilePager? = null
    private var largeFileFullyLoaded: Boolean = false
    private var largeFileLoadInFlight: Boolean = false
    private var largeFileDirty: Boolean = false
    private var suppressLargeFileDirtyTracking: Boolean = false
    private val largeFilePagerMutex = Mutex()
    private val crashHandler = CoroutineExceptionHandler { _, throwable ->
        Timber.e(throwable, "Unhandled editor coroutine error")
        if (isViewReady) {
            window?.decorView?.post {
                toast(getString(R.string.text_editor_runtime_recovered))
            }
        }
    }

    private val draftFile: File by lazy {
        val key = docUri.toString()
        val hex = MessageDigest.getInstance("SHA-256")
            .digest(key.toByteArray())
            .joinToString("") { "%02x".format(it) }
        File(cacheDir, "fileedit/$hex.draft")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri = intent.data
        if (uri == null) {
            finish()
            return
        }
        docUri = uri
        displayName = intent.getStringExtra(EXTRA_DISPLAY_NAME)
            ?: DocumentFile.fromSingleUri(this, uri)?.name
            ?: uri.lastPathSegment
            ?: "?"

        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        wordWrap = prefs.getBoolean(PREF_WORD_WRAP, true)
        showWhitespace = prefs.getBoolean(PREF_SHOW_WHITESPACE, false)
        useTab = prefs.getBoolean(PREF_USE_TAB, true)
        fileSizeBytes = fileLength()
        isLargeFile = TextFileSupport.isLargeFile(fileSizeBytes)
        deferredSyntaxHighlightPending = TextFileSupport.shouldUseDeferredSyntaxHighlight(
            fileSizeBytes,
            displayName,
        )
        if (isLargeFile) wordWrap = false

        enableEdgeToEdge()
        setContentView(R.layout.activity_text_editor_edit)
        isViewReady = true

        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
        }

        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = displayName
            subtitle = sizeSubtitle()
        }
        toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            view.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                leftMargin = systemBars.left
                rightMargin = systemBars.right
            }
            findViewById<View>(R.id.editorToolbar).topPadding = systemBars.top
            keyBar.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = maxOf(systemBars.bottom, ime.bottom)
            }
            insets
        }

        editor.apply {
            val isDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
            TextMateSetup.applyTheme(isDark, assets)
            colorScheme = TextMateSetup.createColorScheme(assets)
            colorScheme.setColor(
                EditorColorScheme.NON_PRINTABLE_CHAR,
                if (isDark) 0x1ACCCCCC else 0x1A444444,
            )
            colorScheme.setColor(
                EditorColorScheme.HIGHLIGHTED_DELIMITERS_BACKGROUND,
                if (isDark) 0x40FFFFFF else 0x30000000,
            )
            colorScheme.setColor(EditorColorScheme.HIGHLIGHTED_DELIMITERS_FOREGROUND, 0)
            colorScheme.setColor(EditorColorScheme.HIGHLIGHTED_DELIMITERS_UNDERLINE, 0)
            setEditorLanguage(
                TextMateSetup.createLanguage(initialScopeName(), assets, useTab),
            )
            setTextSize(14f)
            applyUserTypeface(this)
            tabWidth = readTabWidth()
            setWordwrap(wordWrap)
            nonPrintablePaintingFlags = whitespaceFlags()
            props.disallowSuggestions = true
            props.cacheRenderNodeForLongLines = !isLargeFile
            props.deleteEmptyLineFast = false
            installDefaultSymbolPairs(props.overrideSymbolPairs)
            if (isLargeFile) {
                getComponent(EditorAutoCompletion::class.java).isEnabled = false
                setHighlightCurrentBlock(false)
                setHighlightCurrentLine(false)
                setHighlightBracketPair(false)
                setDiagnostics(null)
            }
        }

        editor.subscribeAlways(io.github.rosemoe.sora.event.ContentChangeEvent::class.java) {
            if (isLargeFile && !suppressLargeFileDirtyTracking) {
                largeFileDirty = true
            }
            editor.post { updateMenuState() }
        }
        editor.subscribeAlways(PublishSearchResultEvent::class.java) {
            updateMatchInfo()
        }
        editor.subscribeAlways(ScrollEvent::class.java) {
            if (isLargeFile) tryLoadNextLargeFilePage()
        }

        setupSearchBar()
        setupKeyBar()

        loadFile()

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (searchBar.visibility == View.VISIBLE) {
                        closeSearchBar()
                        return
                    }
                    if (isDirty()) {
                        AlertDialog.Builder(this@TextFileEditActivity)
                            .setTitle(R.string.text_editor_unsaved_changes)
                            .setMessage(R.string.text_editor_confirm_discard_changes)
                            .setPositiveButton(R.string.text_editor_discard_changes) { _, _ -> finish() }
                            .setNeutralButton(R.string.text_editor_save) { _, _ ->
                                saveFile { finish() }
                            }
                            .setNegativeButton(android.R.string.cancel, null)
                            .show()
                    } else {
                        finish()
                    }
                }
            },
        )
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    @Suppress("DEPRECATION")
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            enterLowMemoryMode(level)
        }
    }

    @Suppress("DEPRECATION")
    override fun onLowMemory() {
        super.onLowMemory()
        enterLowMemoryMode(ComponentCallbacks2.TRIM_MEMORY_COMPLETE)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(R.string.deploy).apply {
            icon = getDrawable(R.drawable.ic_baseline_refresh_reversed_24)
            setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
            setOnMenuItemClickListener {
                RimeDaemon.restartRime(fullCheck = true)
                true
            }
        }
        menu.add(R.string.text_editor_find_replace).apply {
            icon = getDrawable(R.drawable.ic_baseline_search_24)
            setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
            setOnMenuItemClickListener {
                openSearchBar()
                true
            }
        }
        menu.add(R.string.real_time_logs).apply {
            icon = getDrawable(R.drawable.ic_outline_bug_report_24)
            setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
            setOnMenuItemClickListener {
                AppUtils.launchLogActivity(this@TextFileEditActivity)
                true
            }
        }
        menu.add(R.string.text_editor_editor_options).apply {
            setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
            setOnMenuItemClickListener {
                startActivity(Intent(this@TextFileEditActivity, EditorOptionsActivity::class.java))
                true
            }
        }
        menu.add(R.string.text_editor_word_wrap).apply {
            setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
            isCheckable = true
            isChecked = wordWrap
            isEnabled = !isLargeFile
            setOnMenuItemClickListener {
                wordWrap = !wordWrap
                isChecked = wordWrap
                editor.setWordwrap(wordWrap)
                prefs.edit().putBoolean(PREF_WORD_WRAP, wordWrap).apply()
                true
            }
        }
        menu.add(R.string.text_editor_show_whitespace).apply {
            setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
            isCheckable = true
            isChecked = showWhitespace
            setOnMenuItemClickListener {
                showWhitespace = !showWhitespace
                isChecked = showWhitespace
                editor.nonPrintablePaintingFlags = whitespaceFlags()
                prefs.edit().putBoolean(PREF_SHOW_WHITESPACE, showWhitespace).apply()
                true
            }
        }
        menu.add(R.string.text_editor_tab_inserts_spaces).apply {
            setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
            isCheckable = true
            isChecked = !useTab
            setOnMenuItemClickListener {
                useTab = !useTab
                isChecked = !useTab
                editor.setEditorLanguage(
                    TextMateSetup.createLanguage(activeScopeName(), assets, useTab),
                )
                editor.installOnlineBracketsMatcher()
                prefs.edit().putBoolean(PREF_USE_TAB, useTab).apply()
                true
            }
        }
        updateMenuState()
        return true
    }

    override fun onResume() {
        super.onResume()
        if (!isViewReady) return
        val newTabWidth = readTabWidth()
        if (editor.tabWidth != newTabWidth) editor.tabWidth = newTabWidth
        applyUserTypeface(editor)
    }

    private fun readTabWidth(): Int = prefs.getInt(EditorOptionsActivity.PREF_TAB_WIDTH, EditorOptionsActivity.DEFAULT_TAB_WIDTH)
        .coerceIn(EditorOptionsActivity.MIN_TAB_WIDTH, EditorOptionsActivity.MAX_TAB_WIDTH)

    private fun applyUserTypeface(editor: io.github.rosemoe.sora.widget.CodeEditor) {
        val raw = prefs.getString(EditorOptionsActivity.PREF_FONT_FALLBACK, null).orEmpty()
        val files = EditorOptionsActivity.enabledFontFiles(raw)
        val tf = FontLoader.loadTypeface(this, files)
        if (editor.typefaceText !== tf) editor.typefaceText = tf
        if (editor.typefaceLineNumber !== tf) editor.typefaceLineNumber = tf
    }

    private fun activeScopeName(): String? = if (isLargeFile || lowMemoryMode) null else TextFileSupport.detectScopeName(displayName)

    private fun initialScopeName(): String? = if (deferredSyntaxHighlightPending) null else activeScopeName()

    private fun installDefaultSymbolPairs(target: SymbolPairMatch) {
        target.putPair('(', SymbolPairMatch.SymbolPair("(", ")"))
        target.putPair('[', SymbolPairMatch.SymbolPair("[", "]"))
        target.putPair('{', SymbolPairMatch.SymbolPair("{", "}"))
        val surroundOnSelection = object : SymbolPairMatch.SymbolPair.SymbolPairEx {
            override fun shouldDoAutoSurround(content: io.github.rosemoe.sora.text.Content): Boolean = content.cursor.isSelected
        }
        target.putPair('"', SymbolPairMatch.SymbolPair("\"", "\"", surroundOnSelection))
        target.putPair('\'', SymbolPairMatch.SymbolPair("'", "'", surroundOnSelection))
        target.putPair('`', SymbolPairMatch.SymbolPair("`", "`", surroundOnSelection))
    }

    private fun whitespaceFlags(): Int = if (showWhitespace) {
        CodeEditor.FLAG_DRAW_WHITESPACE_LEADING or
            CodeEditor.FLAG_DRAW_WHITESPACE_INNER or
            CodeEditor.FLAG_DRAW_WHITESPACE_TRAILING or
            CodeEditor.FLAG_DRAW_WHITESPACE_FOR_EMPTY_LINE or
            CodeEditor.FLAG_DRAW_LINE_SEPARATOR
    } else {
        0
    }

    private fun setupSearchBar() {
        findInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val query = s?.toString().orEmpty()
                if (query.isEmpty()) {
                    editor.searcher.stopSearch()
                } else {
                    editor.searcher.search(
                        query,
                        EditorSearcher.SearchOptions(false, false),
                    )
                }
                updateMatchInfo()
            }
        })
        findPrev.setOnClickListener {
            if (editor.searcher.hasQuery()) editor.searcher.gotoPrevious()
        }
        findNext.setOnClickListener {
            if (editor.searcher.hasQuery()) editor.searcher.gotoNext()
        }
        searchClose.setOnClickListener { closeSearchBar() }
        replaceOne.setOnClickListener {
            if (!editor.searcher.hasQuery()) return@setOnClickListener
            editor.searcher.replaceThis(replaceInput.text.toString())
        }
        replaceAll.setOnClickListener {
            if (!editor.searcher.hasQuery()) return@setOnClickListener
            editor.searcher.replaceAll(replaceInput.text.toString())
        }
    }

    @Suppress("DEPRECATION")
    private fun openSearchBar() {
        searchBar.visibility = View.VISIBLE
        findInput.requestFocus()
        val query = findInput.text?.toString().orEmpty()
        if (query.isNotEmpty()) {
            editor.searcher.search(
                query,
                EditorSearcher.SearchOptions(false, false),
            )
        }
        updateMatchInfo()
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.showSoftInput(findInput, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun closeSearchBar() {
        editor.searcher.stopSearch()
        searchBar.visibility = View.GONE
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(findInput.windowToken, 0)
        editor.requestFocus()
    }

    private fun setupKeyBar() {
        keySave.setOnClickListener {
            if (isDirty()) saveFile()
        }
        keyUndo.setOnClickListener {
            if (editor.canUndo()) editor.undo()
        }
        keyRedo.setOnClickListener {
            if (editor.canRedo()) editor.redo()
        }
        keyTab.setOnClickListener { sendKey(KeyEvent.KEYCODE_TAB) }
        keyHome.setOnClickListener { sendKey(KeyEvent.KEYCODE_MOVE_HOME) }
        keyEnd.setOnClickListener { sendKey(KeyEvent.KEYCODE_MOVE_END) }
        keyLeft.setOnClickListener { sendKey(KeyEvent.KEYCODE_DPAD_LEFT) }
        keyUp.setOnClickListener { sendKey(KeyEvent.KEYCODE_DPAD_UP) }
        keyDown.setOnClickListener { sendKey(KeyEvent.KEYCODE_DPAD_DOWN) }
        keyRight.setOnClickListener { sendKey(KeyEvent.KEYCODE_DPAD_RIGHT) }
        keysScroll.addOnLayoutChangeListener { _, l, _, r, _, ol, _, or, _ ->
            if (r - l != or - ol) applyKeyBarDistribution()
        }
        keysScroll.post { applyKeyBarDistribution() }
    }

    private fun applyKeyBarDistribution() {
        val container = keysContainer
        val viewport = keysScroll.width
        if (viewport <= 0 || container.childCount == 0) return
        val naturalTotal = keyBarKeyWidthPx * container.childCount
        val fits = naturalTotal <= viewport
        val targetContainerWidth =
            if (fits) ViewGroup.LayoutParams.MATCH_PARENT else ViewGroup.LayoutParams.WRAP_CONTENT
        if (container.layoutParams.width != targetContainerWidth) {
            container.layoutParams.width = targetContainerWidth
        }
        for (i in 0 until container.childCount) {
            val child = container.getChildAt(i)
            val lp = child.layoutParams as LinearLayout.LayoutParams
            if (fits) {
                lp.width = 0
                lp.weight = 1f
            } else {
                lp.width = keyBarKeyWidthPx
                lp.weight = 0f
            }
            child.layoutParams = lp
        }
        container.requestLayout()
    }

    private val keyBarKeyWidthPx: Int by lazy {
        (30 * resources.displayMetrics.density).toInt()
    }

    private fun sendKey(keyCode: Int) {
        val now = SystemClock.uptimeMillis()
        editor.dispatchKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0))
        editor.dispatchKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0))
    }

    private fun updateMatchInfo() {
        val searcher = editor.searcher
        val query = findInput.text?.toString().orEmpty()
        matchInfo.text = when {
            query.isEmpty() -> ""
            !searcher.hasQuery() -> ""
            searcher.matchedPositionCount == 0 -> getString(R.string.text_editor_no_matches)
            else -> "${searcher.currentMatchedPositionIndex + 1}/${searcher.matchedPositionCount}"
        }
    }

    private fun updateMenuState() {
        val dirty = isDirty()
        val canUndo = editor.canUndo()
        val canRedo = editor.canRedo()
        keySave.isEnabled = dirty
        keySave.alpha = if (dirty) 1f else 0.4f
        keyUndo.isEnabled = canUndo
        keyUndo.alpha = if (canUndo) 1f else 0.4f
        keyRedo.isEnabled = canRedo
        keyRedo.alpha = if (canRedo) 1f else 0.4f
        supportActionBar?.subtitle = if (dirty) {
            getString(R.string.text_editor_unsaved_changes)
        } else {
            sizeSubtitle()
        }
    }

    private fun sizeSubtitle(): String {
        val size = formatSize(fileLength())
        return if (isLargeFile) "$size · ${getString(R.string.text_editor_plain_mode)}" else size
    }

    private fun fileLength(): Long = DocumentFile.fromSingleUri(this, docUri)?.length() ?: 0L

    private fun fileLastModified(): Long = DocumentFile.fromSingleUri(this, docUri)?.lastModified() ?: 0L

    private fun loadFile() {
        lifecycleScope.launch(crashHandler) {
            if (isLargeFile) {
                loadLargeFilePaged()
                return@launch
            }
            val original = try {
                withContext(Dispatchers.IO) {
                    contentResolver.openInputStream(docUri)?.use {
                        it.readBytes().decodeToString()
                    } ?: error("openInputStream returned null")
                }
            } catch (e: Exception) {
                toast(getString(R.string.text_editor_error_open_file, e.message ?: displayName))
                finish()
                return@launch
            }
            originalText = original
            val draft = withContext(Dispatchers.IO) {
                runCatching {
                    val f = draftFile
                    if (f.exists() && f.lastModified() >= fileLastModified()) f.readText() else null
                }.getOrNull()
            }
            editor.setText(draft ?: original)
            editor.installOnlineBracketsMatcher()
            scheduleDeferredSyntaxHighlightIfNeeded()
            updateMenuState()
        }
    }

    private fun scheduleDeferredSyntaxHighlightIfNeeded() {
        if (!deferredSyntaxHighlightPending || lowMemoryMode || isLargeFile) return
        val scope = TextFileSupport.detectScopeName(displayName) ?: run {
            deferredSyntaxHighlightPending = false
            return
        }
        editor.postDelayed({
            if (!deferredSyntaxHighlightPending || lowMemoryMode || isLargeFile || isFinishing) {
                return@postDelayed
            }
            runCatching {
                editor.setEditorLanguage(
                    TextMateSetup.createLanguage(scope, assets, useTab),
                )
                editor.installOnlineBracketsMatcher()
                deferredSyntaxHighlightPending = false
            }.onFailure {
                Timber.e(it, "Deferred highlight activation failed")
                deferredSyntaxHighlightPending = false
            }
        }, DEFERRED_HIGHLIGHT_DELAY_MS)
    }

    private suspend fun loadLargeFilePaged() {
        val pager = try {
            withContext(Dispatchers.IO) { LargeFilePager(this@TextFileEditActivity, docUri) }
        } catch (e: Exception) {
            toast(getString(R.string.text_editor_error_open_file, e.message ?: displayName))
            finish()
            return
        }
        largeFilePager = pager
        val firstPage = try {
            withContext(Dispatchers.IO) { pager.readNextTextPage().orEmpty() }
        } catch (e: Exception) {
            pager.close()
            largeFilePager = null
            toast(getString(R.string.text_editor_error_open_file, e.message ?: displayName))
            finish()
            return
        }
        originalText = ""
        largeFileDirty = false
        withSuppressedLargeFileDirtyTracking {
            editor.setText(firstPage)
        }
        editor.installOnlineBracketsMatcher()
        largeFileFullyLoaded = pager.isFullyConsumed
        if (largeFileFullyLoaded) {
            pager.close()
            largeFilePager = null
        }
        updateMenuState()
        tryLoadNextLargeFilePage(force = true)
    }

    private fun tryLoadNextLargeFilePage(force: Boolean = false) {
        if (!isLargeFile || largeFileLoadInFlight || largeFileFullyLoaded) return
        val pager = largeFilePager ?: return
        val remainingScroll = editor.scrollMaxY - editor.offsetY
        if (!force && remainingScroll > editor.height * PREFETCH_VIEWPORT_MULTIPLIER) return

        largeFileLoadInFlight = true
        lifecycleScope.launch(crashHandler) {
            try {
                largeFilePagerMutex.withLock {
                    val nextPage = withContext(Dispatchers.IO) { pager.readNextTextPage() }
                    if (nextPage.isNullOrEmpty()) {
                        if (pager.isFullyConsumed) {
                            largeFileFullyLoaded = true
                            pager.close()
                            largeFilePager = null
                        }
                    } else {
                        withSuppressedLargeFileDirtyTracking {
                            appendTextToEditor(nextPage)
                        }
                        if (pager.isFullyConsumed) {
                            largeFileFullyLoaded = true
                            pager.close()
                            largeFilePager = null
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed paging read for $docUri")
                largeFileFullyLoaded = true
                runCatching { pager.close() }
                largeFilePager = null
                toast(getString(R.string.text_editor_error_open_file, e.message ?: displayName))
            } finally {
                largeFileLoadInFlight = false
                updateMenuState()
            }
        }
    }

    private fun appendTextToEditor(text: String) {
        if (text.isEmpty()) return
        val content = editor.text
        val lastLine = content.lineCount - 1
        val lastColumn = content.getColumnCount(lastLine)
        content.insert(lastLine, lastColumn, text)
    }

    private inline fun withSuppressedLargeFileDirtyTracking(block: () -> Unit) {
        val old = suppressLargeFileDirtyTracking
        suppressLargeFileDirtyTracking = true
        try {
            block()
        } finally {
            suppressLargeFileDirtyTracking = old
        }
    }

    private fun isDirty(): Boolean {
        if (isLargeFile) return largeFileDirty
        return editor.text.toString() != originalText
    }

    private fun saveFile(onSuccess: (() -> Unit)? = null) {
        lifecycleScope.launch(crashHandler) {
            try {
                if (isLargeFile) {
                    loadRemainingLargeFilePagesForSave()
                }
                val content = editor.text.toString()
                withContext(Dispatchers.IO) {
                    contentResolver.openOutputStream(docUri, "wt")?.use {
                        it.write(content.toByteArray())
                    } ?: error("openOutputStream returned null")
                    runCatching { draftFile.delete() }
                }
                originalText = content
                if (isLargeFile) {
                    largeFileDirty = false
                }
                toast(getString(R.string.text_editor_saved))
                updateMenuState()
                onSuccess?.invoke()
            } catch (e: Exception) {
                Timber.e(e, "Failed to save $docUri")
                toast(getString(R.string.text_editor_error_save_file, e.message ?: ""))
            }
        }
    }

    override fun onPause() {
        super.onPause()
        if (!::docUri.isInitialized || !isViewReady || isLargeFile) return
        val current = editor.text.toString()
        try {
            if (current != originalText) {
                draftFile.parentFile?.mkdirs()
                draftFile.writeText(current)
            } else if (draftFile.exists()) {
                draftFile.delete()
            }
        } catch (_: Exception) {
        }
    }

    private suspend fun loadRemainingLargeFilePagesForSave() {
        if (!isLargeFile || largeFileFullyLoaded) return
        val pager = largeFilePager ?: return
        largeFileLoadInFlight = true
        try {
            largeFilePagerMutex.withLock {
                while (true) {
                    val nextPage = withContext(Dispatchers.IO) { pager.readNextTextPage() } ?: break
                    if (nextPage.isNotEmpty()) {
                        withSuppressedLargeFileDirtyTracking {
                            appendTextToEditor(nextPage)
                        }
                    }
                }
                if (pager.isFullyConsumed) {
                    largeFileFullyLoaded = true
                    pager.close()
                    largeFilePager = null
                }
            }
        } finally {
            largeFileLoadInFlight = false
        }
    }

    private fun enterLowMemoryMode(level: Int) {
        if (!isViewReady || lowMemoryMode) return
        lowMemoryMode = true
        deferredSyntaxHighlightPending = false
        runCatching {
            editor.apply {
                setStyles(null)
                setDiagnostics(null)
                setEditorLanguage(TextMateSetup.createLanguage(null, assets, useTab))
                installOnlineBracketsMatcher()
                getComponent(EditorAutoCompletion::class.java).isEnabled = false
                props.cacheRenderNodeForLongLines = false
                props.disallowSuggestions = true
            }
            if (!lowMemoryNoticeShown) {
                lowMemoryNoticeShown = true
                toast(getString(R.string.text_editor_low_memory_features_disabled))
            }
            Timber.w("Entered low memory mode, level=$level")
        }.onFailure {
            Timber.e(it, "Failed to degrade editor on low memory")
        }
    }

    override fun onDestroy() {
        runCatching { largeFilePager?.close() }
        largeFilePager = null
        if (isViewReady) editor.release()
        super.onDestroy()
    }

    private fun formatSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return "%.1f KB".format(kb)
        val mb = kb / 1024.0
        return "%.1f MB".format(mb)
    }

    private fun toast(msg: String) {
        android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_SHORT).show()
    }

    companion object {
        const val EXTRA_DISPLAY_NAME = "display_name"
        private const val PREFS_NAME = "text_editor"
        private const val PREF_WORD_WRAP = "word_wrap"
        private const val PREF_SHOW_WHITESPACE = "show_whitespace"
        private const val PREF_USE_TAB = "use_tab"
        private const val LARGE_FILE_PAGE_BYTES = 256 * 1024
        private const val PREFETCH_VIEWPORT_MULTIPLIER = 2
        private const val DEFERRED_HIGHLIGHT_DELAY_MS = 180L
    }

    private class LargeFilePager(
        private val context: Context,
        private val uri: Uri,
    ) : AutoCloseable {

        private var pfd: ParcelFileDescriptor? = null
        private var channel: FileChannel? = null
        private var mappedSize: Long = 0L
        private var mappedOffset: Long = 0L
        private var stream: BufferedInputStream? = null
        private var sourceExhausted: Boolean = false
        private val decoder: CharsetDecoder = StandardCharsets.UTF_8.newDecoder()
        private var carry: ByteArray = ByteArray(0)

        val isFullyConsumed: Boolean
            get() = sourceExhausted && carry.isEmpty()

        init {
            if (!openMappedSource()) {
                openStreamSource()
            }
        }

        fun readNextTextPage(): String? {
            if (isFullyConsumed) return null
            val bytes = when {
                channel != null -> readMappedBytes(LARGE_FILE_PAGE_BYTES)
                stream != null -> readStreamBytes(LARGE_FILE_PAGE_BYTES)
                else -> ByteArray(0)
            }
            if (bytes.isEmpty() && isFullyConsumed) {
                return null
            }
            return decodeUtf8(bytes, sourceExhausted)
        }

        private fun openMappedSource(): Boolean {
            return try {
                val descriptor = context.contentResolver.openFileDescriptor(uri, "r") ?: return false
                val fileChannel = FileInputStream(descriptor.fileDescriptor).channel
                mappedSize = fileChannel.size()
                pfd = descriptor
                channel = fileChannel
                sourceExhausted = mappedSize == 0L
                true
            } catch (_: Exception) {
                runCatching { channel?.close() }
                runCatching { pfd?.close() }
                channel = null
                pfd = null
                false
            }
        }

        private fun openStreamSource() {
            stream = BufferedInputStream(
                context.contentResolver.openInputStream(uri)
                    ?: error("openInputStream returned null"),
            )
            sourceExhausted = false
        }

        private fun readMappedBytes(maxBytes: Int): ByteArray {
            val fileChannel = channel ?: return ByteArray(0)
            val remaining = mappedSize - mappedOffset
            if (remaining <= 0) {
                sourceExhausted = true
                return ByteArray(0)
            }
            val toRead = minOf(maxBytes.toLong(), remaining).toInt()
            val mapped = fileChannel.map(FileChannel.MapMode.READ_ONLY, mappedOffset, toRead.toLong())
            val out = ByteArray(toRead)
            mapped.get(out)
            mappedOffset += toRead
            if (mappedOffset >= mappedSize) sourceExhausted = true
            return out
        }

        private fun readStreamBytes(maxBytes: Int): ByteArray {
            val input = stream ?: return ByteArray(0)
            val buf = ByteArray(maxBytes)
            val read = input.read(buf)
            if (read < 0) {
                sourceExhausted = true
                return ByteArray(0)
            }
            return if (read == buf.size) buf else buf.copyOf(read)
        }

        private fun decodeUtf8(bytes: ByteArray, endInput: Boolean): String {
            val merged = if (carry.isEmpty()) {
                bytes
            } else {
                ByteArray(carry.size + bytes.size).also {
                    carry.copyInto(it, 0)
                    bytes.copyInto(it, carry.size)
                }
            }
            val inBuffer = ByteBuffer.wrap(merged)
            var outBuffer = CharBuffer.allocate((merged.size * decoder.maxCharsPerByte()).toInt() + 8)
            while (true) {
                val result = decoder.decode(inBuffer, outBuffer, endInput)
                if (result.isOverflow) {
                    val grown = CharBuffer.allocate(outBuffer.capacity() * 2)
                    outBuffer.flip()
                    grown.put(outBuffer)
                    outBuffer = grown
                    continue
                }
                if (result.isError) result.throwException()
                break
            }
            if (endInput) {
                while (true) {
                    val flush = decoder.flush(outBuffer)
                    if (flush.isOverflow) {
                        val grown = CharBuffer.allocate(outBuffer.capacity() * 2)
                        outBuffer.flip()
                        grown.put(outBuffer)
                        outBuffer = grown
                        continue
                    }
                    if (flush.isError) flush.throwException()
                    break
                }
                decoder.reset()
                carry = ByteArray(0)
            } else {
                val remain = inBuffer.remaining()
                carry = ByteArray(remain)
                if (remain > 0) inBuffer.get(carry)
            }
            outBuffer.flip()
            return outBuffer.toString()
        }

        override fun close() {
            runCatching { stream?.close() }
            runCatching { channel?.close() }
            runCatching { pfd?.close() }
            stream = null
            channel = null
            pfd = null
            sourceExhausted = true
            carry = ByteArray(0)
            decoder.reset()
        }
    }
}
