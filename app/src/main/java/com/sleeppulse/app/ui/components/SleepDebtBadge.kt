package com.sleeppulse.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.sleeppulse.app.ui.history.DebtLevel
import com.sleeppulse.app.ui.history.SleepDebt
import com.sleeppulse.app.ui.theme.AlertCoral
import com.sleeppulse.app.ui.theme.CalmNightTextSecondary
import com.sleeppulse.app.ui.theme.CautionAmber
import com.sleeppulse.app.ui.theme.ClinicalTeal

/**
 * Compact banner shown at the top of the History screen. Displays total sleep deficit
 * over the last 7 nights with a colour-coded tier indicator, or a "no data yet" message
 * when [sleepDebt] is null. Soft-tinted like the Dashboard guidance banner rather than a
 * solid saturated card, so it reads as calm rather than alarming.
 */
@Composable
fun SleepDebtBadge(
    sleepDebt: SleepDebt?,
    modifier: Modifier = Modifier,
) {
    val tint = if (sleepDebt != null) debtColor(sleepDebt.level) else CalmNightTextSecondary

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(tint.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
            .border(1.dp, tint.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                text = "SLEEP DEBT · 7 NIGHTS",
                style = MaterialTheme.typography.labelSmall,
                color = CalmNightTextSecondary,
            )
            Text(
                text = if (sleepDebt != null) formatDeficit(sleepDebt.deficitMinutes) else "No history yet",
                style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"),
                color = tint,
            )
        }
    }
}

private fun formatDeficit(minutes: Int): String {
    if (minutes == 0) return "Caught up"
    val h = minutes / 60
    val m = minutes % 60
    return buildString {
        if (h > 0) append("${h}h ")
        if (m > 0) append("${m}m")
        append(" short")
    }.trim()
}

private fun debtColor(level: DebtLevel): Color = when (level) {
    DebtLevel.CAUGHT_UP -> ClinicalTeal
    DebtLevel.MILD -> CautionAmber
    DebtLevel.MODERATE -> CautionAmber
    DebtLevel.SEVERE -> AlertCoral
}
