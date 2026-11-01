/*
 * SPDX-FileCopyrightText: 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.stats

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [InputStatsDailyEntity::class, InputStatsSchemaEntity::class],
    version = 1,
)
abstract class InputStatsDatabase : RoomDatabase() {
    abstract fun inputStatsDao(): InputStatsDao

    companion object {
        private const val DATABASE_NAME = "input_stats.db"

        @Volatile
        private var instance: InputStatsDatabase? = null

        fun get(context: Context): InputStatsDatabase = instance ?: synchronized(this) {
            instance ?: Room
                .databaseBuilder(context.applicationContext, InputStatsDatabase::class.java, DATABASE_NAME)
                .fallbackToDestructiveMigration(true)
                .build()
                .also { instance = it }
        }
    }
}
