/*
 * SPDX-FileCopyrightText: 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.stats

import android.os.SystemClock
import androidx.room.withTransaction
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.util.appContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import java.security.MessageDigest
import java.time.LocalDate

/**
 * In-memory aggregator for input statistics.
 *
 * Events are collected cheaply on the caller thread and periodically flushed to
 * [InputStatsDatabase] in batched transactions, so committing text never blocks on disk.
 */
object InputStatsRepository {
    private const val FLUSH_DELAY_MS = 2_000L
    private const val MAX_PENDING_ROWS = 64
    private const val MAX_ACTIVE_GAP_MS = 10_000L

    private data class DailyKey(
        val day: String,
        val origin: String,
        val dimension: String,
        val bucket: Int,
    )

    private class DailyDelta(
        var entries: Long = 0,
        var chars: Long = 0,
        var activeMs: Long = 0,
        var timedChars: Long = 0,
    )

    private class SchemaDelta(
        var name: String,
        var entries: Long = 0,
        var chars: Long = 0,
        var lastUsedAt: Long,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val flushMutex = Mutex()
    private val lock = Any()
    private val pendingDaily = HashMap<DailyKey, DailyDelta>()
    private val pendingSchemas = HashMap<String, SchemaDelta>()

    @Volatile
    private var flushJob: Job? = null

    @Volatile
    private var lastRimeCommitAt: Long = 0L

    private val database: InputStatsDatabase by lazy {
        InputStatsDatabase.get(appContext)
    }

    fun recordRimeCommit(
        text: String,
        schemaId: String,
        schemaName: String,
    ) {
        if (!isEnabled()) return
        val commitAt = SystemClock.elapsedRealtime()
        val sample = InputStatsClassifier.classify(text)
        if (sample == null) {
            lastRimeCommitAt = commitAt
            return
        }
        val previousCommitAt = lastRimeCommitAt
        lastRimeCommitAt = commitAt
        val gap = commitAt - previousCommitAt
        val activeDurationMs = if (previousCommitAt > 0L && gap in 1..MAX_ACTIVE_GAP_MS) {
            gap
        } else {
            0L
        }
        val timedHanCount = if (activeDurationMs > 0L) sample.hanCount.toLong() else 0L

        val day = LocalDate.now().toString()
        val dimension = schemaId.ifEmpty { InputStats.UNKNOWN_SCHEMA_ID }
        val displayName = schemaName.ifEmpty { dimension }
        val now = System.currentTimeMillis()

        val shouldFlush = synchronized(lock) {
            val key = DailyKey(day, InputStats.ORIGIN_RIME, dimension, sample.bucket)
            pendingDaily.getOrPut(key) { DailyDelta() }.apply {
                entries += 1
                chars += sample.hanCount
                activeMs += activeDurationMs
                timedChars += timedHanCount
            }
            if (dimension != InputStats.UNKNOWN_SCHEMA_ID) {
                val schema = pendingSchemas.getOrPut(dimension) {
                    SchemaDelta(name = displayName, lastUsedAt = now)
                }
                if (schemaName.isNotEmpty()) {
                    schema.name = schemaName
                }
                schema.entries += 1
                schema.chars += sample.hanCount
                schema.lastUsedAt = now
            }
            pendingDaily.size + pendingSchemas.size >= MAX_PENDING_ROWS
        }
        scheduleFlush()
        if (shouldFlush) flushAsync()
    }

    fun recordVoice(
        text: String,
        source: InputStatsVoiceSource,
    ) {
        if (!isEnabled()) return
        if (text.isBlank()) return
        val hanCount = InputStatsClassifier.countHan(text)
        val bucket = hanCount.coerceAtMost(InputStatsClassifier.MAX_BUCKET)
        val day = LocalDate.now().toString()

        val shouldFlush = synchronized(lock) {
            val key = DailyKey(day, source.origin, source.origin, bucket)
            pendingDaily.getOrPut(key) { DailyDelta() }.apply {
                entries += 1
                chars += hanCount
            }
            pendingDaily.size + pendingSchemas.size >= MAX_PENDING_ROWS
        }
        scheduleFlush()
        if (shouldFlush) flushAsync()
    }

    fun markSchemaUsed(
        schemaId: String,
        schemaName: String,
    ) {
        if (!isEnabled()) return
        if (schemaId.isEmpty()) return
        val now = System.currentTimeMillis()

        synchronized(lock) {
            val schema = pendingSchemas.getOrPut(schemaId) {
                SchemaDelta(name = schemaName.ifEmpty { schemaId }, lastUsedAt = now)
            }
            schema.name = schemaName.ifEmpty { schema.name }
            schema.lastUsedAt = now
        }
        scheduleFlush()
    }

    fun flushAsync() {
        scope.launch { flush() }
    }

    fun clear() {
        scope.launch {
            flushMutex.withLock {
                synchronized(lock) {
                    pendingDaily.clear()
                    pendingSchemas.clear()
                    lastRimeCommitAt = 0L
                }
                try {
                    val dao = database.inputStatsDao()
                    database.withTransaction {
                        dao.deleteAllDaily()
                        dao.deleteAllSchemas()
                    }
                } catch (t: Throwable) {
                    Timber.e(t, "Failed to clear input statistics")
                }
            }
        }
    }

    fun observeSnapshot(): Flow<InputStatsSnapshot> {
        val dao = database.inputStatsDao()
        return combine(
            dao.observeDailyStats(),
            dao.observeSchemaUsage(),
        ) { daily, schemas ->
            InputStatsSnapshot.build(
                daily = daily,
                schemas = schemas,
                today = LocalDate.now().toString(),
            )
        }.distinctUntilChanged()
    }

    suspend fun exportBackupData(): InputStatsBackupData {
        flush()
        val dao = database.inputStatsDao()
        return InputStatsBackupData(
            daily = dao.getAllDaily().map { row ->
                InputStatsBackupDaily(
                    day = row.day,
                    origin = row.origin,
                    dimension = row.dimension,
                    bucket = row.bucket,
                    entryCount = row.entryCount,
                    charCount = row.charCount,
                    activeMs = row.activeMs,
                    timedChars = row.timedChars,
                )
            },
            schemas = dao.getAllSchemas().map { row ->
                InputStatsBackupSchema(
                    schemaId = row.schemaId,
                    schemaName = row.schemaName,
                    entryCount = row.entryCount,
                    charCount = row.charCount,
                    lastUsedAt = row.lastUsedAt,
                )
            },
        )
    }

    suspend fun importBackupData(data: InputStatsBackupData) {
        flushMutex.withLock {
            synchronized(lock) {
                pendingDaily.clear()
                pendingSchemas.clear()
                lastRimeCommitAt = 0L
            }
            val dao = database.inputStatsDao()
            database.withTransaction {
                dao.deleteAllDaily()
                dao.deleteAllSchemas()
                data.daily.forEach { row ->
                    dao.insertDaily(
                        InputStatsDailyEntity(
                            day = row.day,
                            origin = row.origin,
                            dimension = row.dimension,
                            bucket = row.bucket,
                            entryCount = row.entryCount,
                            charCount = row.charCount,
                            activeMs = row.activeMs,
                            timedChars = row.timedChars,
                        ),
                    )
                }
                data.schemas.forEach { row ->
                    dao.insertSchema(
                        InputStatsSchemaEntity(
                            schemaId = row.schemaId,
                            schemaName = row.schemaName,
                            entryCount = row.entryCount,
                            charCount = row.charCount,
                            lastUsedAt = row.lastUsedAt,
                        ),
                    )
                }
            }
        }
    }

    suspend fun computeFingerprint(): String {
        val data = exportBackupData()
        val content = StringBuilder()
        data.daily
            .sortedWith(
                compareBy<InputStatsBackupDaily> { it.day }
                    .thenBy { it.origin }
                    .thenBy { it.dimension }
                    .thenBy { it.bucket },
            )
            .forEach { row ->
                content
                    .append(row.day).append('\t')
                    .append(row.origin).append('\t')
                    .append(row.dimension).append('\t')
                    .append(row.bucket).append('\t')
                    .append(row.entryCount).append('\t')
                    .append(row.charCount).append('\t')
                    .append(row.activeMs).append('\t')
                    .append(row.timedChars).append('\n')
            }
        data.schemas
            .sortedBy { it.schemaId }
            .forEach { row ->
                content
                    .append(row.schemaId).append('\t')
                    .append(row.schemaName).append('\t')
                    .append(row.entryCount).append('\t')
                    .append(row.charCount).append('\t')
                    .append(row.lastUsedAt).append('\n')
            }
        return MessageDigest
            .getInstance("SHA-256")
            .digest(content.toString().toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    private fun isEnabled(): Boolean = runCatching {
        AppPrefs.defaultInstance().general.inputStatistics.getValue()
    }.getOrDefault(true)

    private fun scheduleFlush() {
        synchronized(lock) {
            if (flushJob?.isActive == true) return
            flushJob = scope.launch {
                delay(FLUSH_DELAY_MS)
                flush()
            }
        }
    }

    private fun hasPending(): Boolean = synchronized(lock) {
        pendingDaily.isNotEmpty() || pendingSchemas.isNotEmpty()
    }

    private suspend fun flush() {
        flushMutex.withLock {
            val (daily, schemas) = synchronized(lock) {
                if (pendingDaily.isEmpty() && pendingSchemas.isEmpty()) {
                    flushJob = null
                    return@withLock
                }
                val dailySnapshot = pendingDaily.mapValues { (_, delta) ->
                    DailyDelta(
                        entries = delta.entries,
                        chars = delta.chars,
                        activeMs = delta.activeMs,
                        timedChars = delta.timedChars,
                    )
                }
                val schemaSnapshot = pendingSchemas.mapValues { (_, delta) ->
                    SchemaDelta(
                        name = delta.name,
                        entries = delta.entries,
                        chars = delta.chars,
                        lastUsedAt = delta.lastUsedAt,
                    )
                }
                pendingDaily.clear()
                pendingSchemas.clear()
                dailySnapshot to schemaSnapshot
            }

            try {
                val dao = database.inputStatsDao()
                database.withTransaction {
                    daily.forEach { (key, delta) ->
                        val updated = dao.updateDaily(
                            day = key.day,
                            origin = key.origin,
                            dimension = key.dimension,
                            bucket = key.bucket,
                            entryCount = delta.entries,
                            charCount = delta.chars,
                            activeMs = delta.activeMs,
                            timedChars = delta.timedChars,
                        )
                        if (updated == 0) {
                            dao.insertDaily(
                                InputStatsDailyEntity(
                                    day = key.day,
                                    origin = key.origin,
                                    dimension = key.dimension,
                                    bucket = key.bucket,
                                    entryCount = delta.entries,
                                    charCount = delta.chars,
                                    activeMs = delta.activeMs,
                                    timedChars = delta.timedChars,
                                ),
                            )
                        }
                    }
                    schemas.forEach { (schemaId, delta) ->
                        val updated = dao.updateSchema(
                            schemaId = schemaId,
                            schemaName = delta.name,
                            entryCount = delta.entries,
                            charCount = delta.chars,
                            lastUsedAt = delta.lastUsedAt,
                        )
                        if (updated == 0) {
                            dao.insertSchema(
                                InputStatsSchemaEntity(
                                    schemaId = schemaId,
                                    schemaName = delta.name,
                                    entryCount = delta.entries,
                                    charCount = delta.chars,
                                    lastUsedAt = delta.lastUsedAt,
                                ),
                            )
                        }
                    }
                }
            } catch (t: Throwable) {
                Timber.e(t, "Failed to flush input statistics")
                synchronized(lock) {
                    daily.forEach { (key, delta) ->
                        pendingDaily.getOrPut(key) { DailyDelta() }.apply {
                            entries += delta.entries
                            chars += delta.chars
                            activeMs += delta.activeMs
                            timedChars += delta.timedChars
                        }
                    }
                    schemas.forEach { (schemaId, delta) ->
                        pendingSchemas.getOrPut(schemaId) {
                            SchemaDelta(name = delta.name, lastUsedAt = delta.lastUsedAt)
                        }.apply {
                            name = delta.name
                            entries += delta.entries
                            chars += delta.chars
                            lastUsedAt = maxOf(lastUsedAt, delta.lastUsedAt)
                        }
                    }
                }
            } finally {
                synchronized(lock) { flushJob = null }
            }
        }

        if (hasPending()) scheduleFlush()
    }
}
