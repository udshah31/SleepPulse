package com.sleeppulse.shared.db

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * Schemas are exported to `shared/schemas/` (commit each new N.json). To change the schema: bump
 * [VERSION], add a Migration(VERSION - 1, VERSION) to [MIGRATIONS], and commit the new JSON —
 * `SleepPulseDatabaseMigrationsTest` fails CI until all three are done.
 */
@Database(
    entities = [NightlySummaryEntity::class, SleepSessionEntity::class, SessionReadingEntity::class],
    version = SleepPulseDatabase.VERSION,
    exportSchema = true,
)
@TypeConverters(Converters::class)
@ConstructedBy(SleepPulseDatabaseConstructor::class)
abstract class SleepPulseDatabase : RoomDatabase() {
    abstract fun nightlySummaryDao(): NightlySummaryDao
    abstract fun sleepSessionDao(): SleepSessionDao

    companion object {
        const val VERSION = 5

        /** First version with an exported schema; anything older can only be reset. */
        const val FIRST_EXPORTED_VERSION = 4

        /** Pre-export dev builds (never shipped) had no schema to migrate from, so they reset. */
        val UNMIGRATABLE_VERSIONS: IntArray = (1 until FIRST_EXPORTED_VERSION).toList().toIntArray()

        /**
         * HRV became nullable. SQLite can't drop NOT NULL in place, so both tables are rebuilt. A BLE
         * strap's HRV used to be a fixed placeholder, always exactly 50.0, so that exact value is
         * stored as unknown; real measurements are non-integer-valued averages and keep their value.
         */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL(
                    "CREATE TABLE `nightly_summary_new` (`dateEpochDay` INTEGER NOT NULL, `bedtimeEpochMillis` INTEGER NOT NULL, " +
                        "`sleepScore` INTEGER NOT NULL, `avgHeartRateBpm` INTEGER NOT NULL, `avgHrvMillis` REAL, " +
                        "`totalSleepMinutes` INTEGER NOT NULL, `deepSleepMinutes` INTEGER NOT NULL, " +
                        "`remSleepMinutes` INTEGER NOT NULL, `tags` TEXT NOT NULL, PRIMARY KEY(`dateEpochDay`))",
                )
                connection.execSQL(
                    "INSERT INTO `nightly_summary_new` SELECT `dateEpochDay`, `bedtimeEpochMillis`, `sleepScore`, " +
                        "`avgHeartRateBpm`, CASE WHEN `avgHrvMillis` = 50.0 THEN NULL ELSE `avgHrvMillis` END, " +
                        "`totalSleepMinutes`, `deepSleepMinutes`, `remSleepMinutes`, `tags` FROM `nightly_summary`",
                )
                connection.execSQL("DROP TABLE `nightly_summary`")
                connection.execSQL("ALTER TABLE `nightly_summary_new` RENAME TO `nightly_summary`")

                connection.execSQL(
                    "CREATE TABLE `session_reading_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`sessionId` INTEGER NOT NULL, `timestampMillis` INTEGER NOT NULL, " +
                        "`heartRateBpm` INTEGER NOT NULL, `hrvMillis` REAL, `sleepStage` TEXT NOT NULL)",
                )
                connection.execSQL(
                    "INSERT INTO `session_reading_new` SELECT `id`, `sessionId`, `timestampMillis`, `heartRateBpm`, " +
                        "CASE WHEN `hrvMillis` = 50.0 THEN NULL ELSE `hrvMillis` END, `sleepStage` FROM `session_reading`",
                )
                connection.execSQL("DROP TABLE `session_reading`")
                connection.execSQL("ALTER TABLE `session_reading_new` RENAME TO `session_reading`")
            }
        }

        val MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_4_5)
    }
}

// Room generates the actual implementations for each platform.
@Suppress("KotlinNoActualForExpect")
expect object SleepPulseDatabaseConstructor : RoomDatabaseConstructor<SleepPulseDatabase> {
    override fun initialize(): SleepPulseDatabase
}
