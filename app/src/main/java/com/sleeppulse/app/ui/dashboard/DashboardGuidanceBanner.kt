package com.sleeppulse.app.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sleeppulse.app.ui.theme.ClinicalTeal

private const val MIN_NIGHTS_FOR_RECOVERY = 4

/**
 * Teal-tinted callout under the recovery gauge — the one line of "why" behind the score,
 * or a baseline-building nudge when there isn't yet enough history for [recoveryResult].
 * Dashboard-only: mirrors the guidance card from the redesign mockup without touching the
 * shared [com.sleeppulse.app.ui.components.RecoveryScoreCard] used on the Recovery tab.
 */
@Composable
fun DashboardGuidanceBanner(
    recoveryResult: RecoveryResult?,
    recordedNightsCount: Int,
    modifier: Modifier = Modifier,
) {
    val text = recoveryResult?.guidance
        ?: "Building your baseline ($recordedNightsCount/$MIN_NIGHTS_FOR_RECOVERY nights) — recovery guidance appears once there's enough history to compare against."

    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        modifier = modifier
            .fillMaxWidth()
            .background(ClinicalTeal.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
            .border(1.dp, ClinicalTeal.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
    )
}
