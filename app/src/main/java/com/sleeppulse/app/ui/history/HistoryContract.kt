package com.sleeppulse.app.ui.history

import com.sleeppulse.shared.model.NightlySummary
import com.sleeppulse.shared.analytics.SleepDebt

sealed class HistoryIntent {
    data object Load : HistoryIntent()
    data class SelectTab(val tab: HistoryTab) : HistoryIntent()
    data object ExportData : HistoryIntent()
}

enum class HistoryTab {
    LIST, TRENDS
}

data class HistoryState(
    val isLoading: Boolean = true,
    val nights: List<NightWithTrend> = emptyList(),
    val sleepDebt: SleepDebt? = null,
    val consistencyScore: Int? = null,
    val selectedTab: HistoryTab = HistoryTab.LIST,
)

data class NightWithTrend(
    val summary: NightlySummary,
    val trend: NightlySummary.Trend,
)
