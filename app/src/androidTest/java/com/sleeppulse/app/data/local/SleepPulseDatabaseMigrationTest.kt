package com.sleeppulse.app.data.local

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs every entry of [SleepPulseDatabase.MIGRATIONS] against the exported schemas, so a new
 * migration is covered the moment it is added (not in CI: `./gradlew :app:connectedAndroidTest`).
 * Data-preservation checks for a specific migration belong in their own test next to it.
 */
@RunWith(AndroidJUnit4::class)
class SleepPulseDatabaseMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        SleepPulseDatabase::class.java,
    )

    @Test
    fun everyMigrationProducesTheExportedSchema() {
        for (m in SleepPulseDatabase.MIGRATIONS) {
            helper.createDatabase(TEST_DB, m.startVersion).close()
            helper.runMigrationsAndValidate(TEST_DB, m.endVersion, true, m).close()
            ApplicationProvider.getApplicationContext<android.content.Context>().deleteDatabase(TEST_DB)
        }
    }

    @Test
    fun currentSchemaOpensWithAllMigrations() {
        helper.createDatabase(TEST_DB, SleepPulseDatabase.VERSION).close()
        Room.databaseBuilder(
            ApplicationProvider.getApplicationContext(),
            SleepPulseDatabase::class.java,
            TEST_DB,
        ).addMigrations(*SleepPulseDatabase.MIGRATIONS).build().apply {
            openHelper.writableDatabase.close()
            close()
        }
    }

    @Test
    fun migration4to5KeepsRealHrvAndNullsThePlaceholder() {
        helper.createDatabase(TEST_DB, 4).apply {
            execSQL("INSERT INTO nightly_summary VALUES (1, 100, 80, 55, 62.25, 400, 80, 90, 'caffeine,late meal')")
            execSQL("INSERT INTO nightly_summary VALUES (2, 200, 70, 58, 50.0, 380, 70, 80, '')")
            execSQL("INSERT INTO sleep_session VALUES (1, 1000, 0)")
            execSQL("INSERT INTO session_reading VALUES (1, 1, 1000, 60, 41.5, 'LIGHT')")
            execSQL("INSERT INTO session_reading VALUES (2, 1, 2000, 60, 50.0, 'LIGHT')")
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 5, true, SleepPulseDatabase.MIGRATION_4_5)

        db.query("SELECT dateEpochDay, sleepScore, tags, avgHrvMillis FROM nightly_summary ORDER BY dateEpochDay").use {
            assertEquals(2, it.count)
            it.moveToFirst()
            assertEquals(80, it.getInt(1))
            assertEquals("caffeine,late meal", it.getString(2))
            assertEquals(62.25, it.getDouble(3), 0.0001)
            it.moveToNext()
            assertEquals(70, it.getInt(1))
            assertEquals(true, it.isNull(3)) // placeholder 50.0 is now unknown
        }
        db.query("SELECT hrvMillis, sleepStage FROM session_reading ORDER BY id").use {
            assertEquals(2, it.count)
            it.moveToFirst()
            assertEquals(41.5, it.getDouble(0), 0.0001)
            assertEquals("LIGHT", it.getString(1))
            it.moveToNext()
            assertEquals(true, it.isNull(0))
        }
        db.close()
    }

    private companion object {
        const val TEST_DB = "migration-test"
    }
}
