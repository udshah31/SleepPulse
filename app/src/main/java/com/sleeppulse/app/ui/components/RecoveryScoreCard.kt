package com.sleeppulse.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sleeppulse.app.ui.dashboard.RecoveryResult

private const val MIN_NIGHTS_FOR_RECOVERY = 4

/**
 * Shows today's Recovery Score (last night vs. a 7-night rolling baseline), or a
 * "building your baseline" message when there isn't yet enough recorded history.
 */
@Composable
fun RecoveryScoreCard(
    recoveryResult: RecoveryResult?,
    recordedNightsCount: Int,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (recoveryResult != null) {
                Text(
                    text = "Recovery: ${recoveryResult.score}",
                    style = MaterialTheme.typography.titleLarge,
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
        }
    }
}
