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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sleeppulse.app.ui.components.CalmNightCard
import com.sleeppulse.app.ui.components.CalmNightSectionLabel
import com.sleeppulse.app.ui.components.RecoveryScoreCard
import com.sleeppulse.app.ui.components.scoreColor
import com.sleeppulse.app.ui.theme.CalmNightSurfaceDim
import com.sleeppulse.app.ui.theme.CalmNightTextSecondary
import com.sleeppulse.app.ui.theme.SleepIndigo

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
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(text = "Recovery Coach", style = MaterialTheme.typography.headlineMedium)
        Text(
            text = "A daily read on how ready your body is to do more.",
            style = MaterialTheme.typography.bodyMedium,
            color = CalmNightTextSecondary,
        )

        if (state.recoveryResult != null) {
            RecoveryScoreCard(
                recoveryResult = state.recoveryResult,
                recordedNightsCount = state.recordedNightsCount,
                modifier = Modifier.fillMaxWidth(),
            )
        } else if (!state.isLoading) {
            CalmNightCard(modifier = Modifier.fillMaxWidth(), containerColor = CalmNightSurfaceDim) {
                CalmNightSectionLabel(text = "Recovery baseline")
                Text(
                    text = "Building your baseline",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Text(
                    text = "Wear your sensor for a few more nights. SleepPulse needs enough history to make the comparison personal.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = CalmNightTextSecondary,
                    modifier = Modifier.padding(top = 6.dp),
                )
                Text(
                    text = "${state.recordedNightsCount} nights recorded",
                    style = MaterialTheme.typography.labelLarge,
                    color = SleepIndigo,
                    modifier = Modifier.padding(top = 14.dp),
                )
            }
        }

        if (state.readiness != null) {
            RecoveryInfoCard(title = "Today's readiness") {
                Text(
                    text = "${state.readiness!!.score} · ${state.readiness!!.tier.name.lowercase().replaceFirstChar { it.uppercase() }}",
                    style = MaterialTheme.typography.headlineSmall.copy(fontFeatureSettings = "tnum"),
                    color = scoreColor(state.readiness!!.score / 100f),
                )
                Text(
                    text = "Blends last night's recovery with your HRV, resting heart rate, trends, and sleep debt.",
                    style = MaterialTheme.typography.bodySmall,
                    color = CalmNightTextSecondary,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }

        if (state.personalizedAdvice != null) {
            RecoveryInfoCard(title = "Personalized advice") {
                Text(
                    text = state.personalizedAdvice!!,
                    style = MaterialTheme.typography.bodyMedium,
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
    CalmNightCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = CalmNightSurfaceDim,
        content = {
            CalmNightSectionLabel(text = title)
            Column(modifier = Modifier.padding(top = 8.dp), content = content)
        },
    )
}
