/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ui.main

import android.app.Activity
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import androidx.paging.LoadState
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.osfans.trime.R
import com.osfans.trime.data.db.ClipboardHelper
import com.osfans.trime.data.db.DatabaseBean
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.data.theme.ThemeManager
import com.osfans.trime.databinding.ActivityClipSearchBinding
import com.osfans.trime.ime.clipboard.ClipboardAdapter
import com.osfans.trime.ime.clipboard.ClipboardEntryUi
import com.osfans.trime.ime.clipboard.SpacesItemDecoration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import splitties.dimensions.dp
import splitties.systemservices.inputMethodManager
import timber.log.Timber
import kotlin.time.Duration.Companion.milliseconds

class ClipSearchActivity : Activity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var editText: EditText
    private lateinit var progressBar: ProgressBar
    private lateinit var emptyText: TextView
    private val theme by lazy {
        runCatching { ThemeManager.activeTheme }.getOrElse {
            Timber.w("ClipSearchActivity: activeTheme unavailable")
            throw it
        }
    }
    private var searchJob: Job? = null

    private val pagingAdapter by lazy {
        SearchAdapter(theme) { entry -> onEntryClicked(entry) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.attributes.gravity = Gravity.TOP
        val binding = ActivityClipSearchBinding.inflate(layoutInflater).apply {
            editText = clipSearchText
            progressBar = clipSearchLoading
            emptyText = clipSearchEmpty
            clipSearchRecycler.apply {
                layoutManager = StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL)
                itemAnimator = null
                addItemDecoration(SpacesItemDecoration(dp(4)))
                adapter = this@ClipSearchActivity.pagingAdapter
            }
            clipSearchCancel.setOnClickListener { finish() }
        }
        setContentView(binding.root)
        inputMethodManager.showSoftInput(editText, 0)

        pagingAdapter.addLoadStateListener { loadState ->
            when (loadState.source.refresh) {
                is LoadState.Loading -> {
                    progressBar.visibility = View.VISIBLE
                }

                is LoadState.NotLoading -> {
                    progressBar.visibility = View.GONE
                }

                is LoadState.Error -> {
                    progressBar.visibility = View.GONE
                }
            }
        }

        pagingAdapter.registerAdapterDataObserver(
            object : RecyclerView.AdapterDataObserver() {
                override fun onChanged() { updateEmptyState() }
                override fun onItemRangeInserted(positionStart: Int, itemCount: Int) { updateEmptyState() }
                override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) { updateEmptyState() }
            },
        )

        editText.addTextChangedListener(
            object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    val query = s?.toString().orEmpty().trim()
                    searchJob?.cancel()
                    searchJob = scope.launch {
                        delay(300L.milliseconds)
                        performSearch(query)
                    }
                }
            },
        )
    }

    private fun updateEmptyState() {
        val currentQuery = pagingAdapter.query
        emptyText.visibility =
            if (currentQuery.isNotEmpty() && pagingAdapter.itemCount == 0) {
                View.VISIBLE
            } else {
                View.GONE
            }
    }

    private suspend fun performSearch(query: String) {
        if (query.isEmpty()) {
            pagingAdapter.query = ""
            return
        }
        pagingAdapter.query = query
        Pager(PagingConfig(pageSize = 16, enablePlaceholders = false)) {
            ClipboardHelper.searchEntriesPager(query)
        }.flow.collect { pagingData ->
            pagingAdapter.submitData(pagingData)
        }
    }

    override fun onStop() {
        super.onStop()
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    private fun onEntryClicked(entry: DatabaseBean) {
        ClipboardHelper.onSearchResultPaste?.invoke(entry.text)
        finish()
    }
}

private val diffCallback = object : DiffUtil.ItemCallback<DatabaseBean>() {
    override fun areItemsTheSame(oldItem: DatabaseBean, newItem: DatabaseBean): Boolean = oldItem.id == newItem.id

    override fun areContentsTheSame(oldItem: DatabaseBean, newItem: DatabaseBean): Boolean = oldItem == newItem
}

private class SearchAdapter(
    private val theme: Theme,
    private val onEntryClick: (DatabaseBean) -> Unit,
) : PagingDataAdapter<DatabaseBean, SearchAdapter.ViewHolder>(diffCallback) {

    var query: String = ""

    class ViewHolder(val entryUi: ClipboardEntryUi) : RecyclerView.ViewHolder(entryUi.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(ClipboardEntryUi(parent.context, theme))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val entry = getItem(position) ?: return
        val displayText = if (entry.isUriEntry()) {
            val context = holder.entryUi.ctx
            val uri = runCatching { android.net.Uri.parse(entry.text) }.getOrNull()
            val fileName = uri?.lastPathSegment?.let {
                android.net.Uri.decode(it).substringAfterLast('/').substringAfterLast(':')
            }?.trim()?.takeIf { it.isNotEmpty() }
            if (fileName.isNullOrBlank()) {
                context.getString(R.string.clipboard_entry_file)
            } else {
                context.getString(R.string.clipboard_entry_file_named, fileName)
            }
        } else {
            ClipboardAdapter.excerptText(entry.text)
        }
        holder.entryUi.setHighlightedEntry(displayText, query, entry.pinned)
        holder.entryUi.root.setOnClickListener {
            onEntryClick(entry)
        }
    }
}
