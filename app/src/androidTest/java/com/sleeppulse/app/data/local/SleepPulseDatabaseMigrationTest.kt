package com.sleeppulse.app.data.local

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
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

    private companion object {
        const val TEST_DB = "migration-test"
    }
}
