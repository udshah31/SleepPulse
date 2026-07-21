package com.sleeppulse.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

import androidx.room.TypeConverters

@Database(
    entities = [NightlySummaryEntity::class, SleepSessionEntity::class, SessionReadingEntity::class],
    version = 4,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class SleepPulseDatabase : RoomDatabase() {
    abstract fun nightlySummaryDao(): NightlySummaryDao
    abstract fun sleepSessionDao(): SleepSessionDao
}
