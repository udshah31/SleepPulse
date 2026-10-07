package com.sleeppulse.app.data.local

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sleeppulse.shared.db.SleepPulseDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs every entry of [SleepPulseDatabase.MIGRATIONS] against the exported schemas (in
 * `shared/schemas`, packaged as test assets), so a new migration is covered the moment it is
 * added (not in CI: `./gradlew :app:connectedAndroidTest`). Uses the bundled driver, like the
 * app, so the shared `SQLiteConnection` migrations are what run. Data-preservation checks for a
 * specific migration are separate tests in this class (see the 4 to 5 one).
 */
@RunWith(AndroidJUnit4::class)
class SleepPulseDatabaseMigrationTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val dbFile = context.getDatabasePath(TEST_DB)

    @get:Rule
    val helper = MigrationTestHelper(
        instrumentation = InstrumentationRegistry.getInstrumentation(),
        file = dbFile,
        driver = BundledSQLiteDriver(),
        databaseClass = SleepPulseDatabase::class,
    )

    // The helper refuses to create over an existing file, so clear leftovers from any earlier run too.
    @Before
    @After
    fun deleteTestDatabase() {
        context.deleteDatabase(TEST_DB)
    }

    @Test
    fun everyMigrationProducesTheExportedSchema() {
        for (m in SleepPulseDatabase.MIGRATIONS) {
            helper.createDatabase(m.startVersion).close()
            helper.runMigrationsAndValidate(m.endVersion, listOf(m)).close()
            context.deleteDatabase(TEST_DB)
        }
    }

    @Test
    fun currentSchemaOpensWithAllMigrations() {
        helper.createDatabase(SleepPulseDatabase.VERSION).close()
        val db = Room.databaseBuilder(context, SleepPulseDatabase::class.java, dbFile.absolutePath)
            .setDriver(BundledSQLiteDriver())
            .addMigrations(*SleepPulseDatabase.MIGRATIONS)
            .build()
        runBlocking { db.nightlySummaryDao().getByDate(0) }
        db.close()
    }

    @Test
    fun migration4to5KeepsRealHrvAndNullsThePlaceholder() {
        helper.createDatabase(4).apply {
            execSQL("INSERT INTO nightly_summary VALUES (1, 100, 80, 55, 62.25, 400, 80, 90, 'caffeine,late meal')")
            execSQL("INSERT INTO nightly_summary VALUES (2, 200, 70, 58, 50.0, 380, 70, 80, '')")
            execSQL("INSERT INTO sleep_session VALUES (1, 1000, 0)")
            execSQL("INSERT INTO session_reading VALUES (1, 1, 1000, 60, 41.5, 'LIGHT')")
            execSQL("INSERT INTO session_reading VALUES (2, 1, 2000, 60, 50.0, 'LIGHT')")
            close()
        }

        val db = helper.runMigrationsAndValidate(5, listOf(SleepPulseDatabase.MIGRATION_4_5))

        val nights = db.rows("SELECT dateEpochDay, sleepScore, tags, avgHrvMillis FROM nightly_summary ORDER BY dateEpochDay")
        assertEquals(2, nights.size)
        assertEquals(80L, nights[0][1])
        assertEquals("caffeine,late meal", nights[0][2])
        assertEquals(62.25, nights[0][3] as Double, 0.0001)
        assertEquals(70L, nights[1][1])
        assertTrue(nights[1][3] == null) // placeholder 50.0 is now unknown

        val readings = db.rows("SELECT hrvMillis, sleepStage FROM session_reading ORDER BY id")
        assertEquals(2, readings.size)
        assertEquals(41.5, readings[0][0] as Double, 0.0001)
        assertEquals("LIGHT", readings[0][1])
        assertTrue(readings[1][0] == null)
        db.close()
    }

    /** Every row of [sql] as column values: Long, Double, String, or null. */
    private fun SQLiteConnection.rows(sql: String): List<List<Any?>> = prepare(sql).use { stmt ->
        buildList {
            while (stmt.step()) {
                add(
                    (0 until stmt.getColumnCount()).map { i ->
                        when {
                            stmt.isNull(i) -> null
                            stmt.getColumnType(i) == androidx.sqlite.SQLITE_DATA_INTEGER -> stmt.getLong(i)
                            stmt.getColumnType(i) == androidx.sqlite.SQLITE_DATA_FLOAT -> stmt.getDouble(i)
                            else -> stmt.getText(i)
                        }
                    },
                )
            }
        }
    }

    private companion object {
        const val TEST_DB = "migration-test"
    }
}
