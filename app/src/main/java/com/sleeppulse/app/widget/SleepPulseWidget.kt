package com.sleeppulse.app.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.sleeppulse.app.data.repository.SettingsRepository
import com.sleeppulse.app.data.repository.SleepRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first

class SleepPulseWidget : GlanceAppWidget() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WidgetEntryPoint {
        fun sleepRepository(): SleepRepository
        fun settingsRepository(): SettingsRepository
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            WidgetEntryPoint::class.java,
        )
        val latestNight = entryPoint.sleepRepository().recentNights().first().firstOrNull()
        val targetHour = entryPoint.settingsRepository().targetBedtimeHour.first()
        val targetMinute = entryPoint.settingsRepository().targetBedtimeMinute.first()

        provideContent {
            WidgetContent(
                score = latestNight?.sleepScore,
                targetTime = formatTargetTime(targetHour, targetMinute),
            )
        }
    }

    private fun formatTargetTime(hour: Int, minute: Int): String {
        val period = if (hour < 12) "AM" else "PM"
        val displayHour = when (val h = hour % 12) { 0 -> 12; else -> h }
        return "%d:%02d %s".format(displayHour, minute, period)
    }

    @Composable
    private fun WidgetContent(score: Int?, targetTime: String) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .padding(16.dp)
                .background(Color(0xFF1E1E1E)), // Dark gray background
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "SleepPulse",
                style = TextStyle(
                    color = ColorProvider(Color.White),
                    fontWeight = FontWeight.Bold
                ),
                modifier = GlanceModifier.padding(bottom = 8.dp)
            )
            Text(
                text = if (score != null) "Score: $score" else "No nights recorded yet",
                style = TextStyle(
                    color = ColorProvider(Color.White)
                ),
                modifier = GlanceModifier.padding(bottom = 4.dp)
            )
            Text(
                text = "Target: $targetTime",
                style = TextStyle(
                    color = ColorProvider(Color.LightGray)
                )
            )
        }
    }

    companion object {
        suspend fun refresh(context: Context) {
            runCatching { SleepPulseWidget().updateAll(context) }
        }
    }
}
