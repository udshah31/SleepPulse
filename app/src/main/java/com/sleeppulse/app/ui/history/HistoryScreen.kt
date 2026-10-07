package com.sleeppulse.app.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sleeppulse.app.ui.components.CalmNightCard
import com.sleeppulse.app.ui.components.CalmNightSectionLabel
import com.sleeppulse.app.ui.components.SleepDebtBadge
import com.sleeppulse.app.ui.components.SleepConsistencyBadge
import com.sleeppulse.app.ui.components.SleepStagesBar
import com.sleeppulse.app.ui.components.WeeklyTrendsChart
import com.sleeppulse.app.ui.theme.AlertCoral
import com.sleeppulse.app.ui.theme.CalmNightBackground
import com.sleeppulse.app.ui.theme.CalmNightSurfaceDim
import com.sleeppulse.app.ui.theme.CalmNightTextSecondary
import com.sleeppulse.app.ui.theme.CautionAmber
import com.sleeppulse.app.ui.theme.RecoveryGreen
import com.sleeppulse.shared.model.NightlySummary
import kotlinx.datetime.toJavaLocalDate
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

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        stickyHeader(key = "badges") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CalmNightBackground)
                    .padding(bottom = 12.dp),
            ) {
                Text(text = "History", style = MaterialTheme.typography.headlineMedium)
                Text(
                    text = "Patterns are easier to change when you can see them.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = CalmNightTextSecondary,
                    modifier = Modifier.padding(top = 4.dp, bottom = 18.dp),
                )
                CalmNightSectionLabel(text = "Your recent nights", modifier = Modifier.padding(bottom = 8.dp))
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
                        CalmNightCard(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            padding = 8.dp,
                        ) {
                            CalmNightSectionLabel(text = "Sleep score trend", modifier = Modifier.padding(horizontal = 8.dp))
                            WeeklyTrendsChart(nights = state.nights)
                        }
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
    val scoreColorValue = scoreBandColor(night.summary.sleepScore)
    CalmNightCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        padding = 14.dp,
        shape = RoundedCornerShape(22.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .border(2.dp, scoreColorValue.copy(alpha = 0.65f), CircleShape),
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
                    text = night.summary.date.toJavaLocalDate().format(DateTimeFormatter.ofPattern("EEEE, MMM d")),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = "${formatDuration(night.summary.totalSleepMinutes)} · HRV ${night.summary.avgHrvMillis?.let { "${it.toInt()} ms" } ?: "—"}",
                    style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                    color = CalmNightTextSecondary,
                    modifier = Modifier.padding(top = 3.dp),
                )

                SleepStagesBar(
                    totalMinutes = night.summary.totalSleepMinutes,
                    deepMinutes = night.summary.deepSleepMinutes,
                    remMinutes = night.summary.remSleepMinutes,
                    modifier = Modifier.padding(top = 12.dp),
                )

                NightTags(night.summary)


            }

            Text(
                text = trendArrow(night.trend),
                style = MaterialTheme.typography.titleMedium,
                color = trendColor(night.trend),
            )
        }
    }
}

private fun formatDuration(totalMinutes: Int): String {
    val h = totalMinutes / 60
    val m = totalMinutes % 60
    return "${h}h ${m.toString().padStart(2, '0')}m"
}

private fun scoreBandColor(score: Int): androidx.compose.ui.graphics.Color = when {
    score >= 70 -> RecoveryGreen
    score >= 50 -> CautionAmber
    else -> AlertCoral
}

private fun trendColor(trend: NightlySummary.Trend): androidx.compose.ui.graphics.Color = when (trend) {
    NightlySummary.Trend.UP -> RecoveryGreen
    NightlySummary.Trend.DOWN -> AlertCoral
    NightlySummary.Trend.FLAT -> CalmNightTextSecondary
}

private fun trendArrow(trend: NightlySummary.Trend): String = when (trend) {
    NightlySummary.Trend.UP -> "▲"
    NightlySummary.Trend.DOWN -> "▼"
    NightlySummary.Trend.FLAT -> "―"
}
