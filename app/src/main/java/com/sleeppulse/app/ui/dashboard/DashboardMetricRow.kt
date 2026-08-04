package com.sleeppulse.app.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.sleeppulse.app.ui.components.LiveMetricChart
import com.sleeppulse.app.ui.theme.CalmNightSurfaceDim
import com.sleeppulse.app.ui.theme.CalmNightTextSecondary

/**
 * One live metric on the Dashboard: an icon chip, label + tabular value, and a compact
 * trailing sparkline of recent readings. Distinct from the shared [com.sleeppulse.app.ui.components.MetricRow]
 * (used by [com.sleeppulse.app.ui.components.RecoveryScoreCard] on both Dashboard and Recovery) so
 * restyling this one can't ripple into the Recovery tab.
 */
@Composable
fun DashboardMetricRow(
    icon: String,
    label: String,
    value: String,
    sparklineValues: List<Float>,
    sparklineColor: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(CalmNightSurfaceDim, RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .background(CalmNightTextSecondary.copy(alpha = 0.12f), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = icon, style = MaterialTheme.typography.bodyMedium)
        }

        Row(modifier = Modifier.weight(1f)) {
            androidx.compose.foundation.layout.Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = CalmNightTextSecondary,
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                )
            }
        }

        if (sparklineValues.size >= 2) {
            LiveMetricChart(
                values = sparklineValues,
                color = sparklineColor,
                modifier = Modifier.width(64.dp),
                height = 28.dp,
            )
        }
    }
}
