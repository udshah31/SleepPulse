package com.sleeppulse.app.testutil

import com.sleeppulse.app.data.local.NightlySummaryDao
import com.sleeppulse.app.data.local.NightlySummaryEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeNightlySummaryDao : NightlySummaryDao {
    val entitiesFlow = MutableStateFlow<List<NightlySummaryEntity>>(emptyList())
    val recordedCalls: MutableList<String> = mutableListOf()

    override fun observeRecent(): Flow<List<NightlySummaryEntity>> = entitiesFlow

    override suspend fun upsert(summary: NightlySummaryEntity) {
        recordedCalls.add("upsert")
        val withoutExisting = entitiesFlow.value.filterNot { it.dateEpochDay == summary.dateEpochDay }
        entitiesFlow.value = (withoutExisting + summary).sortedByDescending { it.dateEpochDay }
    }

    override suspend fun trimToLast30Days() {
        recordedCalls.add("trimToLast30Days")
        entitiesFlow.value = entitiesFlow.value
            .sortedByDescending { it.dateEpochDay }
            .take(30)
    }
}
