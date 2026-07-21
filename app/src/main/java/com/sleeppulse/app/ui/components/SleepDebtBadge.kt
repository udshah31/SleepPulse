package com.sleeppulse.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.sleeppulse.app.ui.history.DebtLevel
import com.sleeppulse.app.ui.history.SleepDebt

/**
 * Compact banner shown at the top of the History screen. Displays total sleep deficit
 * over the last 7 nights with a colour-coded tier indicator, or a "no data yet" message
 * when [sleepDebt] is null.
 */
@Composable
fun SleepDebtBadge(
    sleepDebt: SleepDebt?,
    modifier: Modifier = Modifier,
) {
    val containerColor = if (sleepDebt != null) debtColor(sleepDebt.level) else Color(0xFF78909C)

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = "Sleep Debt (7 nights)",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                )
                if (sleepDebt != null) {
                    Text(
                        text = formatDeficit(sleepDebt.deficitMinutes),
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                    )
                } else {
                    Text(
                        text = "No history yet",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                    )
                }
            }
            if (sleepDebt != null) {
                Text(
                    text = debtEmoji(sleepDebt.level),
                    style = MaterialTheme.typography.headlineMedium,
                )
            }
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
    DebtLevel.CAUGHT_UP -> Color(0xFF388E3C)  // green
    DebtLevel.MILD -> Color(0xFFF9A825)        // amber
    DebtLevel.MODERATE -> Color(0xFFE64A19)    // deep orange
    DebtLevel.SEVERE -> Color(0xFFB71C1C)      // dark red
}

private fun debtEmoji(level: DebtLevel): String = when (level) {
    DebtLevel.CAUGHT_UP -> "🟢"
    DebtLevel.MILD -> "🟡"
    DebtLevel.MODERATE -> "🟠"
    DebtLevel.SEVERE -> "🔴"
}
