package com.sleeppulse.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sleeppulse.app.ui.theme.AlertCoral
import com.sleeppulse.app.ui.theme.CalmNightTextSecondary
import com.sleeppulse.app.ui.theme.RecoveryGreen
import kotlin.math.abs

/**
 * One metric line: label, current value, and — once a baseline exists — a small
 * colored trend arrow with the percent delta versus that baseline. Deliberately has no
 * card/background/border, sitting directly on the screen so several rows read as one
 * continuous list (divided by [CalmNightDivider], not boxed).
 */
@Composable
fun MetricRow(
    label: String,
    value: String,
    deltaPercent: Double?,
    favorableWhenPositive: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = CalmNightTextSecondary,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
            )
            if (deltaPercent != null && abs(deltaPercent) >= 1.0) {
                val isFavorable = (deltaPercent >= 0) == favorableWhenPositive
                val arrow = if (deltaPercent >= 0) "▲" else "▼"
                Text(
                    text = " $arrow ${abs(deltaPercent).toInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isFavorable) RecoveryGreen else AlertCoral,
                )
            }
        }
    }
}
