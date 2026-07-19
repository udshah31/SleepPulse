package com.sleeppulse.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [NightlySummaryEntity::class, SleepSessionEntity::class, SessionReadingEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class SleepPulseDatabase : RoomDatabase() {
    abstract fun nightlySummaryDao(): NightlySummaryDao
    abstract fun sleepSessionDao(): SleepSessionDao
}
