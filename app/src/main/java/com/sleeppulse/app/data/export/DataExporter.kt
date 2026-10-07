package com.sleeppulse.app.data.export

import android.content.Context
import android.os.Environment
import com.sleeppulse.shared.model.NightlySummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileWriter
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import dagger.hilt.android.qualifiers.ApplicationContext

class DataExporter @Inject constructor(
    @ApplicationContext private val context: Context
) {

    suspend fun exportToCsv(nights: List<NightlySummary>): File? = withContext(Dispatchers.IO) {
        try {
            val downloadsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            if (downloadsDir != null && !downloadsDir.exists()) downloadsDir.mkdirs()

            val file = File(downloadsDir, "SleepPulse_Export_${System.currentTimeMillis()}.csv")
            FileWriter(file).use { writer ->
                // Write Header
                writer.append("Date,Bedtime (Epoch Ms),Sleep Score,Avg HR,Avg HRV,Total Sleep (m),Deep Sleep (m),REM Sleep (m),Tags\n")
                
                // Write Data
                nights.forEach { night ->
                    val tags = night.tags.joinToString("|")
                    writer.append(
                        "${night.date},${night.bedtimeEpochMillis},${night.sleepScore}," +
                        "${night.avgHeartRateBpm},${night.avgHrvMillis ?: ""},${night.totalSleepMinutes}," +
                        "${night.deepSleepMinutes},${night.remSleepMinutes},$tags\n"
                    )
                }
            }
            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
