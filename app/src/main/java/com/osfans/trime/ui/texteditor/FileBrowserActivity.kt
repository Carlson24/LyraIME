package com.osfans.trime.ui.texteditor

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.webkit.MimeTypeMap
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.documentfile.provider.DocumentFile
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.osfans.trime.BuildConfig
import com.osfans.trime.R
import com.osfans.trime.data.base.DataManager
import splitties.views.topPadding
import java.io.File

class FileBrowserActivity : AppCompatActivity() {

    private sealed class BrowseRoot {
        abstract val path: String
        abstract val displayRoot: Any

        data class Native(val dir: File) : BrowseRoot() {
            override val path: String get() = dir.absolutePath
            override val displayRoot: Any get() = dir
        }

        data class Saf(val tree: DocumentFile) : BrowseRoot() {
            override val path: String get() = tree.uri.toString()
            override val displayRoot: Any get() = tree
        }
    }

    private data class Entry(
        val name: String,
        val isParent: Boolean,
        val isDirectory: Boolean,
        val doc: DocumentFile? = null,
        val file: File? = null,
    )

    private var browseRoot: BrowseRoot? = null
    private var currentDir: File? = null
    private var currentDocDir: DocumentFile? = null
    private val entries = mutableListOf<Entry>()
    private val adapter = FileAdapter()

    private lateinit var prefs: SharedPreferences

    private val isSafMode get() = browseRoot is BrowseRoot.Saf

    private val pickTree = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        try {
            contentResolver.takePersistableUriPermission(uri, flags)
        } catch (_: SecurityException) {
        }
        prefs.edit().putString(PREF_TREE_URI, uri.toString()).apply()
        val root = DocumentFile.fromTreeUri(this, uri)
        if (root == null || !root.canRead()) {
            prefs.edit().remove(PREF_TREE_URI).apply()
            return@registerForActivityResult
        }
        browseRoot = BrowseRoot.Saf(root)
        currentDir = null
        currentDocDir = root
        navigateToSaf(root)
    }

    private val pickFile = registerForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        try {
            contentResolver.takePersistableUriPermission(uri, flags)
        } catch (_: SecurityException) {
        }
        val doc = DocumentFile.fromSingleUri(this, uri)
        val displayName = doc?.name ?: uri.lastPathSegment ?: "?"
        openSafFileUri(uri, displayName, doc?.type ?: contentResolver.getType(uri), doc?.length() ?: -1L)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        enableEdgeToEdge()
        setContentView(R.layout.activity_text_editor_file_browser)

        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
        }

        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(!isTaskRoot)
            setTitle(R.string.text_editor_edit_user_files)
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

        findViewById<RecyclerView>(R.id.recyclerView).apply {
            layoutManager = LinearLayoutManager(this@FileBrowserActivity)
            adapter = this@FileBrowserActivity.adapter
        }

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (isSafMode) {
                        val cur = currentDocDir ?: run {
                            finish()
                            return
                        }
                        val root = (browseRoot as BrowseRoot.Saf).tree
                        if (cur.uri == root.uri) {
                            finish()
                            return
                        }
                        val parent = cur.parentFile
                        navigateToSaf(if (parent != null && isInsideSafRoot(parent, root)) parent else root)
                    } else {
                        val cur = currentDir ?: run {
                            finish()
                            return
                        }
                        val root = (browseRoot as BrowseRoot.Native).dir
                        if (cur.absolutePath == root.absolutePath) {
                            finish()
                            return
                        }
                        navigateToNative(cur.parentFile ?: root)
                    }
                }
            },
        )

        val saved = prefs.getString(PREF_TREE_URI, null)?.let(Uri::parse)
        val persistedOk = saved != null && contentResolver.persistedUriPermissions.any {
            it.uri == saved && it.isReadPermission && it.isWritePermission
        }
        if (saved != null && persistedOk) {
            val root = DocumentFile.fromTreeUri(this, saved)
            if (root != null && root.canRead()) {
                browseRoot = BrowseRoot.Saf(root)
                currentDocDir = root
                navigateToSaf(root)
                return
            }
        }
        // Default to native user data directory
        val userDir = DataManager.defaultDataDir
        browseRoot = BrowseRoot.Native(userDir)
        currentDir = userDir
        navigateToNative(userDir)
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(R.string.text_editor_new_file).apply {
            icon = getDrawable(R.drawable.ic_baseline_file_document_plus_outline_24)
            icon?.setTint(0xFFFFFFFF.toInt())
            setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
            setOnMenuItemClickListener {
                showNewFileDialog()
                true
            }
        }
        menu.add(R.string.text_editor_new_folder).apply {
            icon = getDrawable(R.drawable.ic_baseline_folder_plus_24)
            icon?.setTint(0xFFFFFFFF.toInt())
            setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
            setOnMenuItemClickListener {
                showNewFolderDialog()
                true
            }
        }
        menu.add(R.string.text_editor_change_root_folder).apply {
            setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
            setOnMenuItemClickListener {
                launchPicker()
                true
            }
        }
        menu.add(R.string.text_editor_open_single_file).apply {
            setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
            setOnMenuItemClickListener {
                pickFile.launch(arrayOf("*/*"))
                true
            }
        }
        return true
    }

    override fun onResume() {
        super.onResume()
        if (isSafMode) {
            currentDocDir?.let { navigateToSaf(it, preserveScroll = true) }
        } else {
            currentDir?.let { navigateToNative(it, preserveScroll = true) }
        }
    }

    private fun launchPicker() {
        try {
            pickTree.launch(null)
        } catch (e: Exception) {
            pickTree.launch(null)
        }
    }

    // --- Native file mode ---

    private fun navigateToNative(dir: File, preserveScroll: Boolean = false) {
        currentDir = dir
        findViewById<TextView>(R.id.pathBar).text = dir.absolutePath
        val children = dir.listFiles()?.toList() ?: emptyList()
        val sorted = children.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))

        entries.clear()
        entries.addAll(sorted.map { Entry(it.name, isParent = false, isDirectory = it.isDirectory, file = it) })

        adapter.notifyDataSetChanged()
        val emptyView = findViewById<TextView>(R.id.emptyView)
        emptyView.visibility = if (entries.isEmpty()) View.VISIBLE else View.GONE
        emptyView.text = getString(R.string.text_editor_empty_directory)
        if (!preserveScroll) {
            findViewById<RecyclerView>(R.id.recyclerView).scrollToPosition(0)
        }
    }

    private fun openNativeFile(file: File) {
        val mime = MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(file.extension.lowercase())
        if (!TextFileSupport.isProbablyTextFileByExtension(file.name, mime)) {
            toast(getString(R.string.text_editor_binary_file_unsupported))
            return
        }
        if (file.length() > TextFileSupport.MAX_FILE_SIZE) {
            toast(getString(R.string.text_editor_file_too_large, formatSize(file.length())))
            return
        }
        val uri = FileProvider.getUriForFile(this, "${BuildConfig.APPLICATION_ID}.fileprovider", file)
        startActivity(
            Intent(this, TextFileEditActivity::class.java).apply {
                data = uri
                putExtra(TextFileEditActivity.EXTRA_DISPLAY_NAME, file.name)
            },
        )
    }

    private fun renameNativeFile(file: File, newName: String) {
        val target = File(file.parentFile, newName)
        val ok = try {
            file.renameTo(target)
        } catch (_: Exception) {
            false
        }
        if (ok) {
            toast(getString(R.string.text_editor_renamed))
            currentDir?.let { navigateToNative(it, preserveScroll = true) }
        } else {
            toast(getString(R.string.text_editor_rename_failed, newName))
        }
    }

    private fun deleteNativeFile(file: File) {
        val ok = try {
            file.delete()
        } catch (_: Exception) {
            false
        }
        if (ok) {
            toast(getString(R.string.text_editor_deleted))
            currentDir?.let { navigateToNative(it, preserveScroll = true) }
        } else {
            toast(getString(R.string.text_editor_delete_failed, file.name))
        }
    }

    // --- SAF mode ---

    private fun navigateToSaf(dir: DocumentFile, preserveScroll: Boolean = false) {
        val root = (browseRoot as BrowseRoot.Saf).tree
        currentDocDir = dir
        findViewById<TextView>(R.id.pathBar).text = displaySafPath(dir, root)
        val children = dir.listFiles().toList()
        val (dirs, files) = children.partition { it.isDirectory }
        val sortedDirs = dirs.sortedBy { (it.name ?: "").lowercase() }
        val sortedFiles = files.sortedBy { (it.name ?: "").lowercase() }

        entries.clear()
        if (dir.uri != root.uri) {
            entries.add(Entry(getString(R.string.text_editor_parent_directory), isParent = true, isDirectory = true, doc = dir.parentFile ?: root))
        }
        sortedDirs.forEach { entries.add(Entry(it.name ?: "?", isParent = false, isDirectory = true, doc = it)) }
        sortedFiles.forEach { entries.add(Entry(it.name ?: "?", isParent = false, isDirectory = false, doc = it)) }

        adapter.notifyDataSetChanged()
        val emptyView = findViewById<TextView>(R.id.emptyView)
        emptyView.visibility = if (entries.isEmpty()) View.VISIBLE else View.GONE
        emptyView.text = getString(R.string.text_editor_empty_directory)
        if (!preserveScroll) {
            findViewById<RecyclerView>(R.id.recyclerView).scrollToPosition(0)
        }
    }

    private fun displaySafPath(dir: DocumentFile, root: DocumentFile): String {
        if (dir.uri == root.uri) return "/"
        val parts = mutableListOf<String>()
        var node: DocumentFile? = dir
        while (node != null && node.uri != root.uri) {
            parts.add(0, node.name ?: "?")
            node = node.parentFile
        }
        return "/" + parts.joinToString("/")
    }

    private fun isInsideSafRoot(file: DocumentFile, root: DocumentFile): Boolean {
        var node: DocumentFile? = file
        while (node != null) {
            if (node.uri == root.uri) return true
            node = node.parentFile
        }
        return false
    }

    private fun openSafFileUri(uri: Uri, displayName: String, mime: String?, length: Long) {
        if (!TextFileSupport.isProbablyTextFile(contentResolver, uri, displayName, mime)) {
            toast(getString(R.string.text_editor_binary_file_unsupported))
            return
        }
        if (length > TextFileSupport.MAX_FILE_SIZE) {
            toast(getString(R.string.text_editor_file_too_large, formatSize(length)))
            return
        }
        startActivity(
            Intent(this, TextFileEditActivity::class.java).apply {
                data = uri
                putExtra(TextFileEditActivity.EXTRA_DISPLAY_NAME, displayName)
            },
        )
    }

    private fun renameSafFile(doc: DocumentFile, newName: String) {
        val ok = try {
            doc.renameTo(newName)
        } catch (_: Exception) {
            false
        }
        if (ok) {
            toast(getString(R.string.text_editor_renamed))
            currentDocDir?.let { navigateToSaf(it, preserveScroll = true) }
        } else {
            toast(getString(R.string.text_editor_rename_failed, newName))
        }
    }

    private fun deleteSafFile(doc: DocumentFile) {
        val ok = try {
            doc.delete()
        } catch (_: Exception) {
            false
        }
        if (ok) {
            toast(getString(R.string.text_editor_deleted))
            currentDocDir?.let { navigateToSaf(it, preserveScroll = true) }
        } else {
            toast(getString(R.string.text_editor_delete_failed, doc.name ?: "?"))
        }
    }

    // --- Shared entry handling ---

    private fun onEntryClick(entry: Entry) {
        if (entry.isDirectory) {
            if (isSafMode) {
                val dir = entry.doc ?: return
                navigateToSaf(dir)
            } else {
                val dir = entry.file ?: return
                navigateToNative(dir)
            }
            return
        }
        if (isSafMode) {
            val doc = entry.doc ?: return
            openSafFileUri(doc.uri, entry.name, doc.type, doc.length())
        } else {
            val file = entry.file ?: return
            openNativeFile(file)
        }
    }

    private fun onEntryLongClick(entry: Entry): Boolean {
        val items = arrayOf(
            getString(R.string.text_editor_rename),
            getString(R.string.text_editor_delete),
        )
        AlertDialog.Builder(this)
            .setTitle(entry.name)
            .setItems(items) { _, which ->
                when (which) {
                    0 -> {
                        if (isSafMode) {
                            entry.doc?.let { showRenameDialogNative(entry.name, entry.isDirectory) { newName -> renameSafFile(it, newName) } }
                        } else {
                            entry.file?.let { showRenameDialogNative(entry.name, entry.isDirectory) { newName -> renameNativeFile(it, newName) } }
                        }
                    }
                    1 -> {
                        if (isSafMode) {
                            entry.doc?.let { showDeleteConfirmNative(entry.name) { deleteSafFile(it) } }
                        } else {
                            entry.file?.let { showDeleteConfirmNative(entry.name) { deleteNativeFile(it) } }
                        }
                    }
                }
            }
            .show()
        return true
    }

    private fun showRenameDialogNative(currentName: String, isDirectory: Boolean, onRename: (String) -> Unit) {
        val editText = EditText(this).apply {
            setText(currentName)
            val dot = currentName.lastIndexOf('.')
            val selectionEnd = if (!isDirectory && dot > 0) dot else currentName.length
            setSelection(0, selectionEnd)
        }
        val pad = (16 * resources.displayMetrics.density).toInt()
        val container = FrameLayout(this).apply {
            setPadding(pad, pad / 2, pad, 0)
            addView(editText)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.text_editor_rename)
            .setView(container)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val newName = editText.text.toString().trim()
                if (newName.isNotEmpty() && newName != currentName) onRename(newName)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .create()
            .show()
        editText.requestFocus()
    }

    private fun showDeleteConfirmNative(name: String, onConfirm: () -> Unit) {
        AlertDialog.Builder(this)
            .setTitle(R.string.text_editor_delete)
            .setMessage(getString(R.string.text_editor_confirm_delete, name))
            .setPositiveButton(R.string.text_editor_delete) { _, _ -> onConfirm() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showNewFileDialog() {
        val editText = EditText(this).apply {
            hint = getString(R.string.text_editor_new_file_hint)
        }
        val pad = (16 * resources.displayMetrics.density).toInt()
        val container = FrameLayout(this).apply {
            setPadding(pad, pad / 2, pad, 0)
            addView(editText)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.text_editor_new_file)
            .setView(container)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val name = editText.text.toString().trim()
                if (name.isEmpty()) return@setPositiveButton
                if (isSafMode) {
                    val dir = currentDocDir ?: return@setPositiveButton
                    createSafFile(dir, name)
                } else {
                    val dir = currentDir ?: return@setPositiveButton
                    createNativeFile(dir, name)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .create()
            .show()
        editText.requestFocus()
    }

    private fun createNativeFile(dir: File, name: String) {
        val file = File(dir, name)
        if (file.exists()) {
            toast(getString(R.string.text_editor_file_exists, name))
            return
        }
        val ok = try {
            file.createNewFile()
        } catch (_: Exception) {
            false
        }
        if (ok) {
            toast(getString(R.string.text_editor_file_created, name))
            navigateToNative(dir, preserveScroll = true)
        } else {
            toast(getString(R.string.text_editor_error_save_file, name))
        }
    }

    private fun createSafFile(dir: DocumentFile, name: String) {
        val existing = dir.listFiles().find { it.name == name }
        if (existing != null) {
            toast(getString(R.string.text_editor_file_exists, name))
            return
        }
        val doc = try {
            dir.createFile("application/octet-stream", name)
        } catch (_: Exception) {
            null
        }
        if (doc != null) {
            toast(getString(R.string.text_editor_file_created, name))
            navigateToSaf(dir, preserveScroll = true)
        } else {
            toast(getString(R.string.text_editor_error_save_file, name))
        }
    }

    private fun showNewFolderDialog() {
        val editText = EditText(this).apply {
            hint = getString(R.string.text_editor_new_folder_hint)
        }
        val pad = (16 * resources.displayMetrics.density).toInt()
        val container = FrameLayout(this).apply {
            setPadding(pad, pad / 2, pad, 0)
            addView(editText)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.text_editor_new_folder)
            .setView(container)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val name = editText.text.toString().trim()
                if (name.isEmpty()) return@setPositiveButton
                if (isSafMode) {
                    val dir = currentDocDir ?: return@setPositiveButton
                    createSafDir(dir, name)
                } else {
                    val dir = currentDir ?: return@setPositiveButton
                    createNativeDir(dir, name)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .create()
            .show()
        editText.requestFocus()
    }

    private fun createNativeDir(dir: File, name: String) {
        val file = File(dir, name)
        if (file.exists()) {
            toast(getString(R.string.text_editor_dir_exists, name))
            return
        }
        val ok = try {
            file.mkdir()
        } catch (_: Exception) {
            false
        }
        if (ok) {
            toast(getString(R.string.text_editor_file_created, name))
            navigateToNative(dir, preserveScroll = true)
        } else {
            toast(getString(R.string.text_editor_error_save_file, name))
        }
    }

    private fun createSafDir(dir: DocumentFile, name: String) {
        val existing = dir.listFiles().find { it.name == name }
        if (existing != null) {
            toast(getString(R.string.text_editor_dir_exists, name))
            return
        }
        val doc = try {
            dir.createDirectory(name)
        } catch (_: Exception) {
            null
        }
        if (doc != null) {
            toast(getString(R.string.text_editor_file_created, name))
            navigateToSaf(dir, preserveScroll = true)
        } else {
            toast(getString(R.string.text_editor_error_save_file, name))
        }
    }

    private inner class FileAdapter : RecyclerView.Adapter<FileVH>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileVH {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_text_editor_file_entry, parent, false)
            return FileVH(view)
        }

        override fun getItemCount(): Int = entries.size

        override fun onBindViewHolder(holder: FileVH, position: Int) {
            val entry = entries[position]
            holder.bind(entry)
            holder.itemView.setOnClickListener { onEntryClick(entry) }
            holder.itemView.setOnLongClickListener { onEntryLongClick(entry) }
        }
    }

    private inner class FileVH(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.icon)
        val name: TextView = view.findViewById(R.id.name)
        val info: TextView = view.findViewById(R.id.info)

        fun bind(entry: Entry) {
            when {
                entry.isDirectory -> {
                    icon.setImageResource(R.drawable.ic_baseline_folder_24)
                    name.text = entry.name
                    info.text = ""
                    info.visibility = View.GONE
                }
                else -> {
                    val ext = entry.name.substringAfterLast('.', "").lowercase()
                    icon.setImageResource(
                        when (ext) {
                            "yaml", "yml", "txt", "lua", "md", "json" -> R.drawable.ic_baseline_file_document_outline_24
                            "ldb" -> R.drawable.ic_baseline_database_outline_24
                            else -> R.drawable.ic_baseline_file_outline_24
                        },
                    )
                    name.text = entry.name
                    val len = if (isSafMode) {
                        entry.doc?.length() ?: 0L
                    } else {
                        entry.file?.length() ?: 0L
                    }
                    info.text = formatSize(len)
                    info.visibility = View.VISIBLE
                }
            }
        }
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
        private const val PREFS_NAME = "text_editor"
        private const val PREF_TREE_URI = "tree_uri"
    }
}
