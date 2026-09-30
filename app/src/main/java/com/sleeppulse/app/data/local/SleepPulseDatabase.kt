package com.sleeppulse.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration

/**
 * Schemas are exported to `app/schemas/` (commit each new N.json). To change the schema: bump
 * [VERSION], add a Migration(VERSION - 1, VERSION) to [MIGRATIONS], and commit the new JSON —
 * `SleepPulseDatabaseMigrationsTest` fails CI until all three are done.
 */
@Database(
    entities = [NightlySummaryEntity::class, SleepSessionEntity::class, SessionReadingEntity::class],
    version = SleepPulseDatabase.VERSION,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class SleepPulseDatabase : RoomDatabase() {
    abstract fun nightlySummaryDao(): NightlySummaryDao
    abstract fun sleepSessionDao(): SleepSessionDao

    companion object {
        const val VERSION = 4

        /** First version with an exported schema; anything older can only be reset. */
        const val FIRST_EXPORTED_VERSION = 4

        /** Pre-export dev builds (never shipped) had no schema to migrate from, so they reset. */
        val UNMIGRATABLE_VERSIONS: IntArray = (1 until FIRST_EXPORTED_VERSION).toList().toIntArray()

        val MIGRATIONS: Array<Migration> = arrayOf()
    }
}
