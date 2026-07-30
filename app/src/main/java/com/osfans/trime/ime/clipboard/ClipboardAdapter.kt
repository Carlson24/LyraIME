/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.clipboard

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.util.LruCache
import android.util.Patterns
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.core.net.toUri
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.osfans.trime.R
import com.osfans.trime.data.db.DatabaseBean
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.util.DeviceUtils
import com.osfans.trime.util.item
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import splitties.resources.styledColor
import kotlin.math.min

abstract class ClipboardAdapter(
    private val theme: Theme,
    private val maskSensitive: Boolean = false,
) : PagingDataAdapter<DatabaseBean, ClipboardAdapter.ViewHolder>(diffCallback) {

    var multiSelectMode: Boolean = false
    val selectedIds: MutableSet<Int> = mutableSetOf()
    var onSelectionChanged: (() -> Unit)? = null
    var onLongPressEnterSelectMode: ((Int) -> Unit)? = null

    companion object {
        private val thumbnailCache = object : LruCache<String, Bitmap>(24) {}
        private val cnMainlandMobilePattern = Regex("^1[3-9]\\d{9}$")

        private val diffCallback = object : DiffUtil.ItemCallback<DatabaseBean>() {
            override fun areItemsTheSame(
                oldItem: DatabaseBean,
                newItem: DatabaseBean,
            ): Boolean = oldItem.id == newItem.id

            override fun areContentsTheSame(
                oldItem: DatabaseBean,
                newItem: DatabaseBean,
            ): Boolean = oldItem == newItem
        }

        fun excerptText(
            str: String,
            mask: Boolean = false,
            lines: Int = 4,
            chars: Int = 128,
        ): String = buildString {
            val length = str.length
            var lineBreak = -1
            for (i in 1..lines) {
                val start = lineBreak + 1
                val excerptEnd = min(start + chars, length)
                lineBreak = str.indexOf('\n', start)
                if (lineBreak < 0) {
                    if (mask) {
                        append(DatabaseBean.BULLET.repeat(excerptEnd - start))
                    } else {
                        append(str.substring(start, excerptEnd))
                    }
                    break
                } else {
                    val end = min(excerptEnd, lineBreak)
                    if (mask) {
                        appendLine(DatabaseBean.BULLET.repeat(end - start))
                    } else {
                        appendLine(str.substring(start, end))
                    }
                }
            }
        }
    }

    private var popupMenu: PopupMenu? = null

    class ViewHolder(val entryUi: ClipboardEntryUi) : RecyclerView.ViewHolder(entryUi.root) {
        var thumbnailJob: Job? = null
        var boundThumbnailKey: String? = null
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder = ViewHolder(ClipboardEntryUi(parent.context, theme))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val entry = getItem(position) ?: return
        with(holder.entryUi) {
            val displayText = if (entry.isUriEntry()) {
                compactUriLabel(ctx, entry)
            } else {
                excerptText(entry.text, entry.sensitive && maskSensitive)
            }
            val linkUri = entry.openableLinkUri()
            val searchQuery = entry.searchableQuery()
            val dialNumber = entry.dialableCnMobileNumber()
            val splittableText = entry.splittableText()
            val thumbnailKey = entry.imagePreviewKey()
            val cachedThumbnail = thumbnailKey?.let { thumbnailCache.get(it) }
            holder.thumbnailJob?.cancel()
            holder.boundThumbnailKey = thumbnailKey
            setEntry(displayText, entry.pinned, cachedThumbnail)
            setMultiSelectMode(multiSelectMode, selectedIds.contains(entry.id))
            if (thumbnailKey != null && cachedThumbnail == null) {
                holder.thumbnailJob = scope.launch {
                    val bitmap = entry.loadThumbnailBitmap(ctx)
                    if (bitmap != null) {
                        thumbnailCache.put(thumbnailKey, bitmap)
                    }
                    if (holder.boundThumbnailKey == thumbnailKey) {
                        holder.entryUi.setEntry(displayText, entry.pinned, bitmap)
                    }
                }
            }
            root.setOnClickListener {
                if (multiSelectMode) {
                    val toggled = !selectedIds.contains(entry.id)
                    if (toggled) {
                        selectedIds.add(entry.id)
                    } else {
                        selectedIds.remove(entry.id)
                    }
                    setMultiSelectMode(true, toggled)
                    onSelectionChanged?.invoke()
                } else {
                    onPaste(entry)
                }
            }
            root.setOnLongClickListener {
                if (multiSelectMode) {
                    true
                } else {
                    showEntryMenu(
                        anchor = root,
                        entry = entry,
                        linkUri = linkUri,
                        searchQuery = searchQuery,
                        dialNumber = dialNumber,
                        splittableText = splittableText,
                    )
                    true
                }
            }
        }
    }

    private fun showEntryMenu(
        anchor: android.view.View,
        entry: DatabaseBean,
        linkUri: Uri?,
        searchQuery: String?,
        dialNumber: String?,
        splittableText: String?,
    ) {
        val popup = PopupMenu(anchor.context, anchor)
        val menu = popup.menu
        val iconTint = anchor.context.styledColor(android.R.attr.colorControlNormal)
        val isUriEntry = entry.isUriEntry()
        val imageUri = entry.viewableImageUri()

        if (!isUriEntry || imageUri != null) {
            menu.item(android.R.string.paste, R.drawable.ic_baseline_content_paste_24, iconTint) {
                onPaste(entry)
            }
        }
        if (entry.pinned) {
            menu.item(R.string.remove_from_favorites, R.drawable.ic_baseline_outline_push_pin_24, iconTint) {
                onUnpin(entry.id)
            }
        } else {
            menu.item(R.string.add_to_favorites, R.drawable.ic_baseline_push_pin_24, iconTint) {
                onPin(entry.id)
            }
        }
        if (!isUriEntry) {
            menu.item(R.string.edit, R.drawable.ic_baseline_edit_24, iconTint) {
                onEdit(entry.id)
            }
        }
        menu.item(R.string.share, R.drawable.ic_baseline_share_24, iconTint) {
            onShare(entry)
        }
        if (splittableText != null) {
            menu.item(R.string.split_words, R.drawable.ic_baseline_spellcheck_24, iconTint) {
                onSplitText(splittableText)
            }
        }
        if (imageUri != null) {
            menu.item(R.string.view_image, R.drawable.ic_baseline_image_24, iconTint) {
                onViewImage(imageUri)
            }
        }
        if (linkUri != null) {
            menu.item(R.string.open_link, R.drawable.ic_baseline_language_24, iconTint) {
                onOpenLink(linkUri)
            }
        }
        if (searchQuery != null) {
            menu.item(R.string.search, R.drawable.ic_baseline_search_24, iconTint) {
                onSearch(searchQuery)
            }
        }
        if (dialNumber != null) {
            menu.item(android.R.string.copy, R.drawable.ic_baseline_call_24, iconTint) {
                onDial(dialNumber)
            }
        }
        menu.item(R.string.delete, R.drawable.ic_baseline_delete_24, iconTint) {
            onDelete(entry.id)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            popup.setForceShowIcon(true)
        }
        popup.setOnDismissListener {
            if (it === popupMenu) popupMenu = null
        }
        popupMenu?.dismiss()
        popupMenu = popup
        popup.show()
    }

    override fun onViewRecycled(holder: ViewHolder) {
        holder.thumbnailJob?.cancel()
        holder.thumbnailJob = null
        holder.boundThumbnailKey = null
        super.onViewRecycled(holder)
    }

    fun getEntryAt(position: Int) = getItem(position)

    fun onDetached() {
        scope.cancel()
        popupMenu?.dismiss()
        popupMenu = null
    }

    abstract fun onPaste(entry: DatabaseBean)

    abstract fun onPin(id: Int)

    abstract fun onUnpin(id: Int)

    abstract fun onEdit(id: Int)

    abstract fun onShare(entry: DatabaseBean)

    abstract fun onSplitText(text: String)

    abstract fun onSearch(query: String)

    abstract fun onDial(number: String)

    abstract fun onOpenLink(uri: Uri)

    abstract fun onViewImage(uri: Uri)

    abstract fun onDelete(id: Int)

    private fun compactUriLabel(context: Context, entry: DatabaseBean): String {
        val uri = runCatching { Uri.parse(entry.text) }.getOrNull()
        val fileName = uri?.let { resolveUriFileName(context, it) }
        return if (entry.type.startsWith("image/")) {
            ""
        } else {
            if (fileName.isNullOrBlank()) {
                context.getString(R.string.clipboard_entry_file)
            } else {
                context.getString(R.string.clipboard_entry_file_named, fileName)
            }
        }
    }

    private fun resolveUriFileName(context: Context, uri: Uri): String? {
        val lastSegment = uri.lastPathSegment ?: uri.path
        return lastSegment?.let { Uri.decode(it).substringAfterLast('/').substringAfterLast(':') }
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }

    private fun DatabaseBean.openableLinkUri(): Uri? {
        if (isUriEntry()) return null
        val raw = text.trim()
        if (raw.isEmpty()) return null
        val direct = runCatching { raw.toUri() }.getOrNull()
        if (direct != null &&
            direct.scheme?.lowercase() in setOf("http", "https") &&
            !direct.host.isNullOrBlank()
        ) {
            return direct
        }
        return if (!raw.contains('\n') && Patterns.WEB_URL.matcher(raw).matches()) {
            "https://$raw".toUri()
        } else {
            null
        }
    }

    private fun DatabaseBean.searchableQuery(): String? {
        if (isUriEntry()) return null
        val raw = text.trim()
        return raw.takeIf { it.isNotEmpty() }
    }

    private fun DatabaseBean.splittableText(): String? {
        if (isUriEntry()) return null
        val raw = text.trim()
        return raw.takeIf { it.length > 10 }
    }

    private fun DatabaseBean.dialableCnMobileNumber(): String? {
        if (isUriEntry()) return null
        val raw = text.trim()
        if (raw.isEmpty() || raw.contains('\n')) return null
        val compact = raw
            .replace(" ", "")
            .replace("-", "")
            .replace("(", "")
            .replace(")", "")
            .replace("\uff08", "")
            .replace("\uff09", "")
        val normalized = when {
            compact.startsWith("+86") -> compact.removePrefix("+86")
            compact.startsWith("86") && compact.length > 11 -> compact.removePrefix("86")
            else -> compact
        }
        if (!normalized.all { it.isDigit() }) return null
        return normalized.takeIf { cnMainlandMobilePattern.matches(it) }
    }

    private fun DatabaseBean.imagePreviewKey(): String? = if (isUriEntry() && type.startsWith("image/")) text else null

    private fun DatabaseBean.viewableImageUri(): Uri? {
        if (!isUriEntry() || !type.startsWith("image/")) return null
        return runCatching { Uri.parse(text) }.getOrNull()
    }
}
