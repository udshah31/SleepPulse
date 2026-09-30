package com.sleeppulse.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.sleeppulse.app.data.model.SensorReading
import com.sleeppulse.app.ui.dashboard.MetricBaselineCalculator
import com.sleeppulse.app.ui.dashboard.MetricBaselineResult
import com.sleeppulse.app.ui.dashboard.RecoveryResult
import com.sleeppulse.app.ui.theme.CalmNightSurfaceDim
import com.sleeppulse.app.ui.theme.CalmNightTextSecondary
import com.sleeppulse.app.ui.theme.SleepIndigo

private const val MIN_NIGHTS_FOR_RECOVERY = 4

@Composable
fun RecoveryScoreCard(
    recoveryResult: RecoveryResult?,
    recordedNightsCount: Int,
    latestReading: SensorReading? = null,
    metricBaseline: MetricBaselineResult? = null,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(28.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.linearGradient(
                    colors = listOf(SleepIndigo.copy(alpha = 0.38f), CalmNightSurfaceDim),
                ),
                shape,
            )
            .border(1.dp, SleepIndigo.copy(alpha = 0.28f), shape)
            .padding(20.dp),
    ) {
        CalmNightSectionLabel(text = "Recovery score")
        if (recoveryResult != null) {
            Row(modifier = Modifier.padding(top = 8.dp)) {
                Text(
                    text = "${recoveryResult.score}",
                    style = MaterialTheme.typography.displaySmall.copy(fontFeatureSettings = "tnum"),
                    color = scoreColor(recoveryResult.score / 100f),
                )
                Column(modifier = Modifier.padding(start = 14.dp, top = 5.dp)) {
                    Text(
                        text = recoveryResult.tier.name.lowercase().replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        text = recoveryResult.guidance,
                        style = MaterialTheme.typography.bodySmall,
                        color = CalmNightTextSecondary,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                }
            }
        } else {
            Text(
                text = "Building your baseline ($recordedNightsCount/$MIN_NIGHTS_FOR_RECOVERY nights)",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        if (latestReading != null && metricBaseline != null) {
            MetricRow(
                label = "Heart rate",
                value = "${latestReading.heartRateBpm} bpm",
                deltaPercent = MetricBaselineCalculator.percentDelta(
                    actual = latestReading.heartRateBpm.toDouble(),
                    baseline = metricBaseline.avgHeartRateBpm,
                ),
                favorableWhenPositive = false,
            )
            MetricRow(
                label = "HRV",
                value = "${latestReading.hrvMillis.toInt()} ms",
                deltaPercent = MetricBaselineCalculator.percentDelta(
                    actual = latestReading.hrvMillis,
                    baseline = metricBaseline.avgHrvMillis,
                ),
                favorableWhenPositive = true,
            )
        }
    }
}
