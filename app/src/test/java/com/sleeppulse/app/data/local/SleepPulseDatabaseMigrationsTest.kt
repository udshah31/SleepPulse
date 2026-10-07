package com.sleeppulse.app.data.local

import com.sleeppulse.shared.db.SleepPulseDatabase
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards against the silent data loss this app used to have (fallbackToDestructiveMigration):
 * a version bump must come with a migration from every exported version. (Room regenerates the
 * schema JSON on every build, so this can't prove N.json was committed — only that export is on.)
 */
class SleepPulseDatabaseMigrationsTest {

    // Gradle runs unit tests with the module directory as the working directory; the schemas
    // live with the database in :shared.
    private val schemaDir = File("../shared/schemas/${SleepPulseDatabase::class.java.name}")

    @Test
    fun `schema export is on and has caught up with the current version`() {
        val versions = schemaDir.listFiles()?.mapNotNull { it.nameWithoutExtension.toIntOrNull() }.orEmpty()
        assertTrue("no schema JSON in ${schemaDir.absolutePath}", versions.isNotEmpty())
        assertEquals(SleepPulseDatabase.VERSION, versions.max())
    }

    @Test
    fun `every exported version has a migration to the next`() {
        val covered = SleepPulseDatabase.MIGRATIONS.map { it.startVersion to it.endVersion }.toSet()
        for (v in SleepPulseDatabase.FIRST_EXPORTED_VERSION until SleepPulseDatabase.VERSION) {
            assertTrue("missing Migration($v, ${v + 1})", (v to v + 1) in covered)
        }
    }

    @Test
    fun `only versions older than the first exported schema may be wiped`() {
        assertTrue(SleepPulseDatabase.UNMIGRATABLE_VERSIONS.all { it < SleepPulseDatabase.FIRST_EXPORTED_VERSION })
    }
}
