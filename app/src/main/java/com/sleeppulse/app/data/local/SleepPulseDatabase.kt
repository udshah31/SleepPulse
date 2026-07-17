package com.sleeppulse.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [NightlySummaryEntity::class], version = 1, exportSchema = false)
abstract class SleepPulseDatabase : RoomDatabase() {
    abstract fun nightlySummaryDao(): NightlySummaryDao
}
