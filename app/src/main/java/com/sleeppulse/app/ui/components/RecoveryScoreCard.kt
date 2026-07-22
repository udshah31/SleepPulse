package com.sleeppulse.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sleeppulse.app.data.model.SensorReading
import com.sleeppulse.app.ui.dashboard.MetricBaselineCalculator
import com.sleeppulse.app.ui.dashboard.MetricBaselineResult
import com.sleeppulse.app.ui.dashboard.RecoveryResult
import com.sleeppulse.app.ui.theme.CalmNightTextSecondary

private const val MIN_NIGHTS_FOR_RECOVERY = 4

/**
 * Shows today's Recovery Score (last night vs. a 7-night rolling baseline) plus live
 * HR/HRV trend rows vs. a 7-day baseline, or a "building your baseline" message when
 * there isn't yet enough recorded history. Borderless — a single top divider instead of
 * a Material Card — so it reads as part of the screen rather than a boxed widget.
 */
@Composable
fun RecoveryScoreCard(
    recoveryResult: RecoveryResult?,
    recordedNightsCount: Int,
    latestReading: SensorReading? = null,
    metricBaseline: MetricBaselineResult? = null,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        HorizontalDivider(color = CalmNightTextSecondary.copy(alpha = 0.2f))
        Column(modifier = Modifier.padding(vertical = 16.dp)) {
            if (recoveryResult != null) {
                Text(
                    text = "Recovery: ${recoveryResult.score}",
                    style = MaterialTheme.typography.titleLarge,
                    color = scoreColor(recoveryResult.score / 100f),
                )
                Text(
                    text = recoveryResult.tier.name.lowercase().replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = recoveryResult.guidance,
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                Text(
                    text = "Building your baseline ($recordedNightsCount/$MIN_NIGHTS_FOR_RECOVERY nights)",
                    style = MaterialTheme.typography.bodyMedium,
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
}
