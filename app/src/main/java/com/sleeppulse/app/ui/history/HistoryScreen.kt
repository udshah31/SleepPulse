package com.sleeppulse.app.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sleeppulse.app.data.model.NightlySummary
import java.time.format.DateTimeFormatter

@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.onIntent(HistoryIntent.Load)
    }

    LazyColumn(modifier = Modifier.padding(16.dp)) {
        items(state.nights, key = { it.summary.date }) { night ->
            NightRow(night)
        }
    }
}

@Composable
private fun NightRow(night: NightWithTrend) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = night.summary.date.format(DateTimeFormatter.ofPattern("MMM d")),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = "Score ${night.summary.sleepScore} ${trendArrow(night.trend)}",
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

private fun trendArrow(trend: NightlySummary.Trend): String = when (trend) {
    NightlySummary.Trend.UP -> "▲"
    NightlySummary.Trend.DOWN -> "▼"
    NightlySummary.Trend.FLAT -> "―"
}
