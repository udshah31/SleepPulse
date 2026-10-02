package com.sleeppulse.app.data.export

import android.content.Context
import com.sleeppulse.app.data.model.NightlySummary
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.time.LocalDate

class DataExporterTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun exporterFor(context: Context) = DataExporter(context)

    private fun sample() = NightlySummary(
        date = LocalDate.of(2026, 7, 21),
        bedtimeEpochMillis = 1000L,
        sleepScore = 87,
        avgHeartRateBpm = 58,
        avgHrvMillis = 42.5,
        totalSleepMinutes = 420,
        deepSleepMinutes = 90,
        remSleepMinutes = 60,
        tags = listOf("caffeine", "late-workout"),
    )

    @Test
    fun `exportToCsv writes header and one row per night`() = runTest {
        val dir = tempFolder.newFolder("downloads")
        val context = mock<Context> { on { getExternalFilesDir(anyOrNull()) } doReturn dir }
        val exporter = exporterFor(context)

        val file = exporter.exportToCsv(listOf(sample()))

        assertNotNull(file)
        val lines = file!!.readLines()
        assertEquals("Date,Bedtime (Epoch Ms),Sleep Score,Avg HR,Avg HRV,Total Sleep (m),Deep Sleep (m),REM Sleep (m),Tags", lines[0])
        assertEquals("2026-07-21,1000,87,58,42.5,420,90,60,caffeine|late-workout", lines[1])
    }

    @Test
    fun `exportToCsv creates the downloads directory if missing`() = runTest {
        val dir = tempFolder.newFolder("downloads2").also { it.delete() }
        val context = mock<Context> { on { getExternalFilesDir(anyOrNull()) } doReturn dir }
        val exporter = exporterFor(context)

        exporter.exportToCsv(listOf(sample()))

        assertTrue(dir.exists())
    }

    @Test
    fun `exportToCsv returns null when the target path is unwritable`() = runTest {
        // A plain file (not a directory) as the "downloads dir" makes the child File path
        // invalid, so FileWriter throws and exportToCsv should swallow it and return null.
        val notADirectory = tempFolder.newFile("not-a-directory")
        val context = mock<Context> { on { getExternalFilesDir(anyOrNull()) } doReturn notADirectory }
        val exporter = exporterFor(context)

        val file = exporter.exportToCsv(listOf(sample()))

        assertNull(file)
    }

    @Test
    fun `exportToCsv leaves the hrv cell empty when unknown`() = runTest {
        val dir = tempFolder.newFolder("downloads3")
        val context = mock<Context> { on { getExternalFilesDir(anyOrNull()) } doReturn dir }

        val file = exporterFor(context).exportToCsv(listOf(sample().copy(avgHrvMillis = null)))

        assertEquals("2026-07-21,1000,87,58,,420,90,60,caffeine|late-workout", file!!.readLines()[1])
    }
}
