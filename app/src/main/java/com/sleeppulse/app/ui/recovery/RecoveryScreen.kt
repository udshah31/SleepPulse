package com.sleeppulse.app.ui.recovery

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sleeppulse.app.ui.components.RecoveryScoreCard
import com.sleeppulse.app.ui.components.scoreColor
import com.sleeppulse.app.ui.theme.CalmNightSurfaceDim
import com.sleeppulse.app.ui.theme.CalmNightTextSecondary
import com.sleeppulse.app.ui.theme.ClinicalTeal

@Composable
fun RecoveryScreen(
    viewModel: RecoveryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.onIntent(RecoveryIntent.Load)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(
            text = "Recovery Coach",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.fillMaxWidth(),
        )

        if (state.recoveryResult != null) {
            RecoveryScoreCard(
                recoveryResult = state.recoveryResult,
                recordedNightsCount = state.recordedNightsCount,
                modifier = Modifier.fillMaxWidth()
            )
        } else if (!state.isLoading) {
            Text(
                text = "Not enough data for recovery score. Wear your sensor for a few more nights.",
                style = MaterialTheme.typography.bodyMedium,
                color = CalmNightTextSecondary,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ClinicalTeal.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
                    .border(1.dp, ClinicalTeal.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            )
        }

        if (state.personalizedAdvice != null) {
            RecoveryInfoCard(title = "Personalized advice") {
                Text(
                    text = state.personalizedAdvice!!,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        if (state.readiness != null) {
            RecoveryInfoCard(title = "Today's readiness") {
                Text(
                    text = "${state.readiness!!.score} — ${state.readiness!!.tier.name.lowercase().replaceFirstChar { it.uppercase() }}",
                    style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"),
                    color = scoreColor(state.readiness!!.score / 100f),
                )
                Text(
                    text = "Blends last night's recovery with your HRV/resting-HR trends and sleep debt.",
                    style = MaterialTheme.typography.bodySmall,
                    color = CalmNightTextSecondary,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun RecoveryInfoCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(CalmNightSurfaceDim, RoundedCornerShape(20.dp))
            .padding(20.dp),
    ) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = CalmNightTextSecondary,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        content()
    }
}
