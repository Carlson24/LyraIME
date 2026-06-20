// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [DatabaseBean::class, ClipboardSyncEntity::class], version = 5)
@TypeConverters(DatabaseBean.Converters::class, ClipboardSyncConverters::class)
abstract class Database : RoomDatabase() {
    abstract fun databaseDao(): DatabaseDao
    abstract fun clipboardSyncDao(): ClipboardSyncDao

    companion object {
        val MIGRATION_3_4 =
            object : Migration(3, 4) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    if (db.needUpgrade(4)) {
                        db.execSQL("ALTER TABLE ${DatabaseBean.TABLE_NAME} RENAME TO _t_data")
                        db.execSQL("ALTER TABLE _t_data ADD COLUMN pinned INTEGER NOT NULL DEFAULT 0")
                        db.execSQL(
                            """
                            CREATE TABLE IF NOT EXISTS ${DatabaseBean.TABLE_NAME} (
                                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                                text TEXT,
                                html TEXT,
                                type INTEGER NOT NULL,
                                time INTEGER NOT NULL,
                                pinned INTEGER NOT NULL
                            )
                            """.trimIndent(),
                        )
                        db.execSQL(
                            """
                            INSERT INTO ${DatabaseBean.TABLE_NAME} (id, text, html, type, time, pinned)
                            SELECT id, text, html, type, time, pinned FROM _t_data
                            """.trimIndent(),
                        )
                        db.execSQL("DROP TABLE _t_data")
                    }
                }
            }

        val MIGRATION_4_5 =
            object : Migration(4, 5) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS ${ClipboardSyncEntity.TABLE_NAME} (
                            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            text TEXT NOT NULL DEFAULT '',
                            ts INTEGER NOT NULL,
                            pinned INTEGER NOT NULL,
                            type INTEGER NOT NULL,
                            fileName TEXT,
                            fileSize INTEGER,
                            mimeType TEXT,
                            localFilePath TEXT,
                            downloadStatus INTEGER NOT NULL,
                            serverFileName TEXT,
                            uuid TEXT NOT NULL
                        )
                        """.trimIndent(),
                    )
                }
            }
    }
}
