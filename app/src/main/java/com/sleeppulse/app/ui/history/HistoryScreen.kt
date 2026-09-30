package com.sleeppulse.app.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.SuggestionChip
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.runtime.remember
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sleeppulse.app.data.model.NightlySummary
import com.sleeppulse.app.ui.components.SleepDebtBadge
import com.sleeppulse.app.ui.components.SleepConsistencyBadge
import com.sleeppulse.app.ui.components.SleepStagesBar
import com.sleeppulse.app.ui.components.WeeklyTrendsChart
import com.sleeppulse.app.ui.theme.AlertCoral
import com.sleeppulse.app.ui.theme.CalmNightSurfaceDim
import com.sleeppulse.app.ui.theme.CalmNightTextSecondary
import com.sleeppulse.app.ui.theme.CautionAmber
import com.sleeppulse.app.ui.theme.ClinicalTeal
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.onIntent(HistoryIntent.Load)
    }

    LazyColumn(modifier = Modifier.padding(16.dp)) {
        stickyHeader(key = "badges") {
            Column(modifier = Modifier.padding(bottom = 8.dp)) {
                SleepDebtBadge(
                    sleepDebt = state.sleepDebt,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                SleepConsistencyBadge(
                    score = state.consistencyScore,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                
                TabRow(selectedTabIndex = state.selectedTab.ordinal) {
                    HistoryTab.entries.forEach { tab ->
                        Tab(
                            selected = state.selectedTab == tab,
                            onClick = { viewModel.onIntent(HistoryIntent.SelectTab(tab)) },
                            text = { Text(tab.name.lowercase().replaceFirstChar { it.uppercase() }) }
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = { viewModel.onIntent(HistoryIntent.ExportData) }) {
                        Text("Export CSV")
                    }
                }
            }
        }
        
        when (state.selectedTab) {
            HistoryTab.LIST -> {
                if (state.nights.isEmpty() && !state.isLoading) {
                    item {
                        EmptyHistoryState()
                    }
                } else {
                    items(state.nights, key = { it.summary.date }) { night ->
                        NightRow(night)
                    }
                }
                item(key = "other-apps") { OtherAppsSleepSection() }
            }
            HistoryTab.TRENDS -> {
                item {
                    if (state.nights.isEmpty() && !state.isLoading) {
                        EmptyHistoryState()
                    } else {
                        WeeklyTrendsChart(nights = state.nights)
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyHistoryState() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 64.dp, start = 24.dp, end = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .background(CalmNightSurfaceDim, RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = "☾", style = MaterialTheme.typography.headlineMedium)
        }
        Text(
            text = "Nothing recorded yet",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 16.dp),
        )
        Text(
            text = "Your first completed night appears here automatically, with a trend arrow against the one before it.",
            style = MaterialTheme.typography.bodyMedium,
            color = CalmNightTextSecondary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}

@Composable
private fun NightRow(night: NightWithTrend) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .background(CalmNightSurfaceDim, RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val scoreColorValue = scoreBandColor(night.summary.sleepScore)
        Box(
            modifier = Modifier
                .size(46.dp)
                .background(scoreColorValue.copy(alpha = 0.16f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "${night.summary.sleepScore}",
                style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                color = scoreColorValue,
            )
        }

        Column(modifier = Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(
                text = night.summary.date.format(DateTimeFormatter.ofPattern("EEEE, MMM d")),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = "${formatDuration(night.summary.totalSleepMinutes)} · HRV ${night.summary.avgHrvMillis.toInt()}ms",
                style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                color = CalmNightTextSecondary,
            )

            SleepStagesBar(
                totalMinutes = night.summary.totalSleepMinutes,
                deepMinutes = night.summary.deepSleepMinutes,
                remMinutes = night.summary.remSleepMinutes,
                modifier = Modifier.padding(top = 10.dp),
            )

            if (night.summary.tags.isNotEmpty()) {
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    night.summary.tags.forEach { tag ->
                        SuggestionChip(
                            onClick = { },
                            label = { Text(text = tag, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            SleepChart()
        }

        Text(
            text = trendArrow(night.trend),
            style = MaterialTheme.typography.bodyLarge,
            color = trendColor(night.trend),
        )
    }
}

private fun formatDuration(totalMinutes: Int): String {
    val h = totalMinutes / 60
    val m = totalMinutes % 60
    return "${h}h ${m.toString().padStart(2, '0')}m"
}

private fun scoreBandColor(score: Int): androidx.compose.ui.graphics.Color = when {
    score >= 70 -> ClinicalTeal
    score >= 50 -> CautionAmber
    else -> AlertCoral
}

private fun trendColor(trend: NightlySummary.Trend): androidx.compose.ui.graphics.Color = when (trend) {
    NightlySummary.Trend.UP -> ClinicalTeal
    NightlySummary.Trend.DOWN -> AlertCoral
    NightlySummary.Trend.FLAT -> CalmNightTextSecondary
}

@Composable
fun SleepChart() {
    val animationProgress = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        animationProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 1500, easing = LinearEasing)
        )
    }

    val primaryColor = ClinicalTeal

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .padding(vertical = 8.dp)
    ) {
        val width = size.width
        val height = size.height
        
        val path = Path().apply {
            moveTo(0f, height * 0.2f)
            lineTo(width * 0.2f, height * 0.8f) // Deep
            lineTo(width * 0.4f, height * 0.5f) // Light
            lineTo(width * 0.5f, height * 0.1f) // Awake
            lineTo(width * 0.7f, height * 0.3f) // REM
            lineTo(width * 0.85f, height * 0.9f) // Deep
            lineTo(width, height * 0.4f) // Light
        }
        
        clipRect(right = width * animationProgress.value) {
            drawPath(
                path = path,
                color = primaryColor,
                style = Stroke(width = 6f)
            )
            // Draw gradient/fill under path for a more advanced look could go here
        }
    }
}

private fun trendArrow(trend: NightlySummary.Trend): String = when (trend) {
    NightlySummary.Trend.UP -> "▲"
    NightlySummary.Trend.DOWN -> "▼"
    NightlySummary.Trend.FLAT -> "―"
}
