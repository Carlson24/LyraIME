// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [DatabaseBean::class, ClipboardSyncEntity::class], version = 6)
abstract class Database : RoomDatabase() {
    abstract fun databaseDao(): DatabaseDao
    abstract fun clipboardSyncDao(): ClipboardSyncDao
}
