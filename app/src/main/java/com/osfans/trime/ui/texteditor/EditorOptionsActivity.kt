/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package com.osfans.trime.ui.texteditor

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.osfans.trime.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import splitties.views.topPadding
import timber.log.Timber
import java.io.File

class EditorOptionsActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var adapter: FontFallbackAdapter
    private lateinit var touchHelper: androidx.recyclerview.widget.ItemTouchHelper

    private val pickFont = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) importFromUri(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_text_editor_options)

        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
        }

        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = getString(R.string.text_editor_editor_options)
        }
        toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                leftMargin = bars.left
                rightMargin = bars.right
            }
            findViewById<View>(R.id.editorToolbar).topPadding = bars.top
            WindowInsetsCompat.CONSUMED
        }

        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        setupTabWidth()
        setupFontList()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    private fun setupTabWidth() {
        val tabWidthInput = findViewById<EditText>(R.id.tabWidthInput)
        tabWidthInput.setText(
            prefs.getInt(PREF_TAB_WIDTH, DEFAULT_TAB_WIDTH).toString(),
        )
        tabWidthInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val raw = s?.toString()?.toIntOrNull() ?: return
                val clamped = raw.coerceIn(MIN_TAB_WIDTH, MAX_TAB_WIDTH)
                prefs.edit().putInt(PREF_TAB_WIDTH, clamped).apply()
            }
        })
        tabWidthInput.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val raw = tabWidthInput.text?.toString()?.toIntOrNull() ?: DEFAULT_TAB_WIDTH
                val clamped = raw.coerceIn(MIN_TAB_WIDTH, MAX_TAB_WIDTH)
                tabWidthInput.setText(clamped.toString())
                prefs.edit().putInt(PREF_TAB_WIDTH, clamped).apply()
            }
        }
    }

    private fun setupFontList() {
        val entries = scanFontEntries(this, prefs.getString(PREF_FONT_FALLBACK, null).orEmpty())
            .toMutableList()
        adapter = FontFallbackAdapter(
            entries = entries,
            onChanged = { persistFontOrder() },
            onDelete = { confirmDelete(it) },
            onStartDrag = { holder -> touchHelper.startDrag(holder) },
        )
        findViewById<RecyclerView>(R.id.fontList).apply {
            layoutManager = LinearLayoutManager(this@EditorOptionsActivity)
            adapter = this@EditorOptionsActivity.adapter
        }
        touchHelper = FontFallbackAdapter.makeTouchHelper(adapter) { persistFontOrder() }
        touchHelper.attachToRecyclerView(findViewById(R.id.fontList))
        updateEmptyHint()

        findViewById<View>(R.id.importFont).setOnClickListener {
            pickFont.launch(arrayOf("*/*"))
        }
    }

    private fun updateEmptyHint() {
        findViewById<TextView>(R.id.emptyHint).visibility = if (adapter.itemCount == 0) View.VISIBLE else View.GONE
    }

    private fun persistFontOrder() {
        val encoded = encodeFontOrder(adapter.snapshot())
        prefs.edit().putString(PREF_FONT_FALLBACK, encoded).apply()
    }

    private fun confirmDelete(name: String) {
        AlertDialog.Builder(this)
            .setTitle(R.string.text_editor_remove_font)
            .setMessage(getString(R.string.text_editor_confirm_remove_font, name))
            .setPositiveButton(R.string.text_editor_remove_font) { _, _ ->
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        runCatching { File(FontLoader.fontsDir(this@EditorOptionsActivity), name).delete() }
                    }
                    if (adapter.removeByName(name)) {
                        persistFontOrder()
                        updateEmptyHint()
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun importFromUri(uri: Uri) {
        val displayName = resolveDisplayName(uri)
        val ext = displayName.substringAfterLast('.', "").lowercase()
        if (ext !in ALLOWED_FONT_EXTS) {
            toast(R.string.text_editor_invalid_font_file)
            return
        }
        lifecycleScope.launch {
            val saved = withContext(Dispatchers.IO) {
                runCatching {
                    val dir = FontLoader.fontsDir(this@EditorOptionsActivity)
                    val target = uniqueFile(dir, sanitizeName(displayName))
                    contentResolver.openInputStream(uri)?.use { input ->
                        target.outputStream().use { output -> input.copyTo(output) }
                    } ?: error("openInputStream returned null")
                    target.name
                }.onFailure { Timber.e(it, "Failed to import font from $uri") }
            }
            val name = saved.getOrNull()
            if (name == null) {
                toast(R.string.text_editor_invalid_font_file)
                return@launch
            }
            adapter.add(FontEntry(name, enabled = true))
            persistFontOrder()
            updateEmptyHint()
            android.widget.Toast.makeText(
                this@EditorOptionsActivity,
                getString(R.string.text_editor_font_imported, name),
                android.widget.Toast.LENGTH_SHORT,
            ).show()
        }
    }

    private fun resolveDisplayName(uri: Uri): String {
        contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) {
                val idx = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) return it.getString(idx) ?: ""
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "font"
    }

    private fun toast(resId: Int) {
        android.widget.Toast.makeText(this, getString(resId), android.widget.Toast.LENGTH_SHORT).show()
    }

    companion object {
        const val PREFS_NAME = "text_editor"
        const val PREF_TAB_WIDTH = "tab_width"
        const val PREF_FONT_FALLBACK = "font_fallback_files"

        const val DEFAULT_TAB_WIDTH = 4
        const val MIN_TAB_WIDTH = 1
        const val MAX_TAB_WIDTH = 16

        private val ALLOWED_FONT_EXTS = setOf("ttf", "otf", "ttc", "otc")
        private const val DISABLED_PREFIX = '!'

        fun scanFontEntries(context: Context, raw: String): List<FontEntry> {
            val onDisk = FontLoader.fontsDir(context)
                .listFiles { f -> f.isFile && f.extension.lowercase() in ALLOWED_FONT_EXTS }
                ?.map { it.name }
                ?.toMutableSet()
                ?: mutableSetOf()
            val ordered = mutableListOf<FontEntry>()
            raw.split(',').forEach { token ->
                val t = token.trim()
                if (t.isEmpty()) return@forEach
                val disabled = t.startsWith(DISABLED_PREFIX)
                val name = if (disabled) t.substring(1) else t
                if (name in onDisk) {
                    ordered.add(FontEntry(name, enabled = !disabled))
                    onDisk.remove(name)
                }
            }
            onDisk.sorted().forEach { ordered.add(FontEntry(it, enabled = false)) }
            return ordered
        }

        fun encodeFontOrder(entries: List<FontEntry>): String = entries.joinToString(",") { if (it.enabled) it.name else "$DISABLED_PREFIX${it.name}" }

        fun enabledFontFiles(raw: String): List<String> = raw.split(',').mapNotNull { token ->
            val t = token.trim()
            when {
                t.isEmpty() -> null
                t.startsWith(DISABLED_PREFIX) -> null
                else -> t
            }
        }

        private fun sanitizeName(name: String): String {
            val safe = name.replace(Regex("[/\\\\,!\\s]"), "_")
            return if (safe.isEmpty()) "font" else safe
        }

        private fun uniqueFile(dir: File, name: String): File {
            val base = name.substringBeforeLast('.', name)
            val ext = name.substringAfterLast('.', "")
            var candidate = File(dir, name)
            var i = 1
            while (candidate.exists()) {
                val suffix = if (ext.isEmpty()) "_$i" else "_$i.$ext"
                candidate = File(dir, "$base$suffix")
                i++
            }
            return candidate
        }
    }
}
