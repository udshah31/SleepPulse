package com.sleeppulse.app.ui.history

import com.sleeppulse.app.data.model.NightlySummary

sealed class HistoryIntent {
    data object Load : HistoryIntent()
}

data class HistoryState(
    val isLoading: Boolean = true,
    val nights: List<NightWithTrend> = emptyList(),
)

data class NightWithTrend(
    val summary: NightlySummary,
    val trend: NightlySummary.Trend,
)
