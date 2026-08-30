/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.db

import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import androidx.annotation.Keep
import androidx.room.Room
import androidx.room.withTransaction
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.data.prefs.PreferenceDelegate
import com.osfans.trime.util.WeakHashSet
import com.osfans.trime.util.matchesAny
import com.osfans.trime.util.removeRegexSet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import splitties.systemservices.clipboardManager
import timber.log.Timber

object ClipboardHelper :
    ClipboardManager.OnPrimaryClipChangedListener,
    CoroutineScope by CoroutineScope(SupervisorJob() + Dispatchers.Default) {
    internal lateinit var clbDb: Database
    private lateinit var clbDao: DatabaseDao
    val clipboardSyncDao: ClipboardSyncDao by lazy { clbDb.clipboardSyncDao() }

    fun interface OnClipboardUpdateListener {
        fun onUpdate(bean: DatabaseBean)
    }

    private val mutex = Mutex()

    var itemCount: Int = 0
        private set

    private suspend fun updateItemCount() {
        itemCount = clbDao.itemCount()
    }

    private val onUpdateListeners = WeakHashSet<OnClipboardUpdateListener>()

    fun addOnUpdateListener(listener: OnClipboardUpdateListener) {
        onUpdateListeners.add(listener)
    }

    fun removeOnUpdateListener(listener: OnClipboardUpdateListener) {
        onUpdateListeners.remove(listener)
    }

    private val clipPref = AppPrefs.defaultInstance().clipboard

    private val enabledPref = clipPref.clipboardListening

    @Keep
    private val enabledListener = PreferenceDelegate.OnChangeListener<Boolean> { _, value ->
        if (value) {
            clipboardManager.addPrimaryClipChangedListener(this)
        } else {
            clipboardManager.removePrimaryClipChangedListener(this)
        }
    }

    private val limitPref = clipPref.clipboardLimit

    @Keep
    private val limitListener = PreferenceDelegate.OnChangeListener<Int> { _, _ ->
        launch { removeOutdated() }
    }

    private val compareRules: Set<Regex> by lazy {
        val rules by clipPref.clipboardCompareRules
        rules
            .split('\n')
            .map { Regex(it.trim()) }
            .toSet()
    }

    private fun String.matchesSensitiveKeywords(): Boolean {
        val rules by clipPref.clipboardSensitiveKeywords
        return rules
            .split('\n')
            .mapNotNull { line ->
                val trimmed = line.trim()
                trimmed.takeIf { it.isNotEmpty() }?.let { Regex(it) }
            }
            .any { it.containsMatchIn(this) }
    }

    private val outputRules: Set<Regex> by lazy {
        val rules by clipPref.clipboardOutputRules
        rules
            .split('\n')
            .map { Regex(it) }
            .toSet()
    }

    @Volatile
    var lastBean: DatabaseBean? = null

    private fun updateLastBean(bean: DatabaseBean) {
        lastBean = bean
        onUpdateListeners.forEach { it.onUpdate(bean) }
    }

    fun init(context: Context) {
        clipboardManager.addPrimaryClipChangedListener(this)
        clbDb =
            Room
                .databaseBuilder(context, Database::class.java, "clipboard.db")
                .fallbackToDestructiveMigration(true)
                .build()
        clbDao = clbDb.databaseDao()
        enabledListener.onChange(enabledPref.key, enabledPref.getValue())
        enabledPref.registerOnChangeListener(enabledListener)
        limitListener.onChange(limitPref.key, limitPref.getValue())
        limitPref.registerOnChangeListener(limitListener)
        launch { updateItemCount() }
    }

    suspend fun get(id: Int) = clbDao.get(id)

    suspend fun haveUnpinned(category: ClipboardCategory? = null): Boolean = when (category) {
        ClipboardCategory.All -> clbDao.haveUnpinned()
        ClipboardCategory.Favorites -> !clbDao.favoriteEntries().let { true }
        ClipboardCategory.Local -> clbDao.haveUnpinnedTextEntriesBySource(DatabaseBean.SOURCE_LOCAL)
        ClipboardCategory.Media -> clbDao.haveUnpinnedMediaEntries()
        ClipboardCategory.Remote -> clbDao.haveUnpinnedEntriesBySource(DatabaseBean.SOURCE_REMOTE)
        null -> clbDao.haveUnpinned()
    }

    fun entriesPager(category: ClipboardCategory) = when (category) {
        ClipboardCategory.All -> clbDao.allEntries()
        ClipboardCategory.Favorites -> clbDao.favoriteEntries()
        ClipboardCategory.Local -> clbDao.textEntriesBySource(DatabaseBean.SOURCE_LOCAL)
        ClipboardCategory.Media -> clbDao.mediaEntries()
        ClipboardCategory.Remote -> clbDao.entriesBySource(DatabaseBean.SOURCE_REMOTE)
    }

    fun allBeans() = clbDao.allEntries()

    fun searchEntriesPager(query: String) = clbDao.searchEntries(query)

    suspend fun pin(id: Int) = clbDao.updatePinned(id, true)

    suspend fun unpin(id: Int) = clbDao.updatePinned(id, false)

    suspend fun addNewBean(text: String) {
        mutex.withLock {
            val bean = DatabaseBean(text = text)
            if (bean.text.isBlank()) return
            try {
                clbDao.find(text)?.let {
                    updateLastBean(it.copy(time = bean.time))
                    clbDao.updateTime(it.id, bean.time)
                    return
                }
                val insertedBean = clbDb.withTransaction {
                    val rowId = clbDao.insert(bean)
                    clbDao.get(rowId) ?: bean
                }
                updateLastBean(insertedBean)
                updateItemCount()
            } catch (e: Exception) {
                Timber.w("Failed to update clipboard database: $e")
                updateLastBean(bean)
            }
        }
    }

    suspend fun importLocalEntry(
        text: String,
        type: String,
        timestamp: Long = System.currentTimeMillis(),
        notifyListeners: Boolean = true,
    ) {
        mutex.withLock {
            val bean = DatabaseBean(text = text, type = type, time = timestamp)
            if (bean.text.isBlank()) return
            try {
                clbDao.find(text)?.let {
                    clbDao.updateTime(it.id, timestamp)
                    return
                }
                clbDb.withTransaction {
                    clbDao.insert(bean)
                    removeOutdated()
                    updateItemCount()
                }
            } catch (e: Exception) {
                Timber.w("Failed to import local entry: $e")
            }
        }
    }

    suspend fun importRemoteEntry(
        text: String,
        type: String = "text/plain",
        timestamp: Long = System.currentTimeMillis(),
    ) {
        mutex.withLock {
            val bean = DatabaseBean(
                text = text,
                type = type,
                time = timestamp,
                source = DatabaseBean.SOURCE_REMOTE,
            )
            if (bean.text.isBlank()) return
            try {
                clbDao.findRemoteText(text)?.let {
                    clbDao.updateTime(it.id, timestamp)
                    return
                }
                clbDb.withTransaction {
                    clbDao.insert(bean)
                    updateItemCount()
                }
            } catch (e: Exception) {
                Timber.w("Failed to import remote entry: $e")
            }
        }
    }

    suspend fun updateText(
        id: Int,
        text: String,
    ) {
        lastBean?.let {
            if (id == it.id) updateLastBean(it.copy(text = text))
        }
        clbDao.updateText(id, text)
    }

    suspend fun delete(id: Int) {
        markAsDeleted(id)
        updateItemCount()
    }

    suspend fun deleteAll(
        category: ClipboardCategory? = null,
        skipPinned: Boolean = true,
    ) {
        when {
            !skipPinned -> when (category) {
                ClipboardCategory.All, null -> {
                    val ids = clbDao.findAllIds()
                    clbDao.markAsDeleted(*ids)
                }

                ClipboardCategory.Favorites -> {
                    val ids = clbDao.findPinnedIds()
                    clbDao.markAsDeleted(*ids)
                }

                ClipboardCategory.Local -> {
                    val ids = clbDao.findAllTextEntryIdsBySource(DatabaseBean.SOURCE_LOCAL)
                    clbDao.markAsDeleted(*ids)
                }

                ClipboardCategory.Media -> {
                    val ids = clbDao.findAllMediaEntryIds()
                    clbDao.markAsDeleted(*ids)
                }

                ClipboardCategory.Remote -> {
                    val ids = clbDao.findAllEntryIdsBySource(DatabaseBean.SOURCE_REMOTE)
                    clbDao.markAsDeleted(*ids)
                }
            }

            category == ClipboardCategory.Favorites -> {
                // already pinned-only
                val ids = clbDao.findPinnedIds()
                clbDao.markAsDeleted(*ids)
            }

            else -> when (category) {
                ClipboardCategory.All, null -> {
                    val ids = clbDao.findUnpinnedIds()
                    clbDao.markAsDeleted(*ids)
                }

                ClipboardCategory.Local -> {
                    val ids = clbDao.findUnpinnedTextEntryIdsBySource(DatabaseBean.SOURCE_LOCAL)
                    clbDao.markAsDeleted(*ids)
                }

                ClipboardCategory.Media -> {
                    val ids = clbDao.findUnpinnedMediaEntryIds()
                    clbDao.markAsDeleted(*ids)
                }

                ClipboardCategory.Remote -> {
                    val ids = clbDao.findUnpinnedEntryIdsBySource(DatabaseBean.SOURCE_REMOTE)
                    clbDao.markAsDeleted(*ids)
                }
            }
        }
        updateItemCount()
    }

    suspend fun markAsDeleted(vararg ids: Int) {
        clbDao.markAsDeleted(*ids)
    }

    suspend fun undoDelete(vararg ids: Int) {
        clbDao.undoDelete(*ids)
        updateItemCount()
    }

    suspend fun realDelete() {
        clbDao.realDelete()
    }

    private var lastClipTimestamp = -1L
    private var lastClipHash = 0

    override fun onPrimaryClipChanged() {
        val clip = clipboardManager.primaryClip ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val timestamp = clip.description.timestamp
            if (timestamp == lastClipTimestamp) return
            lastClipTimestamp = timestamp
        } else {
            val timestamp = System.currentTimeMillis()
            val hash = clip.hashCode()
            if (timestamp - lastClipTimestamp < 100L && hash == lastClipHash) return
            lastClipTimestamp = timestamp
            lastClipHash = hash
        }
        launch {
            mutex.withLock {
                var bean = DatabaseBean.fromClipData(clip) ?: return@withLock
                if (bean.text.isBlank()) return@withLock
                if (bean.text.matchesAny(outputRules) ||
                    bean.text.removeRegexSet(compareRules).isEmpty()
                ) {
                    return@withLock
                }
                val isSensitive = bean.sensitive ||
                    (!bean.isUriEntry() && bean.text.matchesSensitiveKeywords())
                if (isSensitive != bean.sensitive) {
                    bean = bean.copy(sensitive = isSensitive)
                }
                try {
                    clbDao.find(bean.text)?.let { existing ->
                        updateLastBean(existing.copy(time = bean.time, sensitive = bean.sensitive))
                        clbDao.updateTime(existing.id, bean.time)
                        if (bean.sensitive != existing.sensitive) {
                            clbDao.updateSensitive(existing.id, bean.sensitive)
                        }
                        return@withLock
                    }
                    val insertedBean =
                        clbDb.withTransaction {
                            val rowId = clbDao.insert(bean)
                            removeOutdated()
                            updateItemCount()
                            clbDao.get(rowId) ?: bean
                        }
                    updateLastBean(insertedBean)
                    updateItemCount()
                } catch (exception: Exception) {
                    Timber.w("Failed to update clipboard database: $exception")
                    updateLastBean(bean)
                }
            }
        }
    }

    private suspend fun removeOutdated() {
        val limit = limitPref.getValue()
        val unpinned = clbDao.getAllUnpinned()
        if (unpinned.size > limit) {
            val outdated =
                unpinned
                    .sortedBy { it.id }
                    .getOrNull(unpinned.size - limit)
            clbDao.markUnpinnedAsDeletedEarlierThan(outdated?.time ?: System.currentTimeMillis())
        }
    }
}
