/*
 * SPDX-FileCopyrightText: 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.stats

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [InputStatsDailyEntity::class, InputStatsSchemaEntity::class],
    version = 2,
)
abstract class InputStatsDatabase : RoomDatabase() {
    abstract fun inputStatsDao(): InputStatsDao

    companion object {
        private const val DATABASE_NAME = "input_stats.db"

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE stats_daily ADD COLUMN active_ms INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE stats_daily ADD COLUMN timed_chars INTEGER NOT NULL DEFAULT 0")
            }
        }

        @Volatile
        private var instance: InputStatsDatabase? = null

        fun get(context: Context): InputStatsDatabase = instance ?: synchronized(this) {
            instance ?: Room
                .databaseBuilder(context.applicationContext, InputStatsDatabase::class.java, DATABASE_NAME)
                .addMigrations(MIGRATION_1_2)
                .fallbackToDestructiveMigration(true)
                .build()
                .also { instance = it }
        }
    }
}
