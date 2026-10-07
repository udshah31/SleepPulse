package com.sleeppulse.shared.analytics

import com.sleeppulse.shared.model.NightlySummary

data class TagCorrelation(
    val tag: String,
    val avgScoreWithTag: Double,
    val avgScoreWithoutTag: Double,
    val scoreDelta: Double,
    val taggedNightCount: Int,
)

object TagCorrelationCalculator {
    private const val MIN_TAGGED_NIGHTS = 3

    fun analyze(nights: List<NightlySummary>): List<TagCorrelation> =
        nights.flatMap { it.tags }.distinct().mapNotNull { tag ->
            val withTag = nights.filter { tag in it.tags }
            if (withTag.size < MIN_TAGGED_NIGHTS) return@mapNotNull null
            val withoutTag = nights.filter { tag !in it.tags }
            if (withoutTag.isEmpty()) return@mapNotNull null
            val avgWith = withTag.map { it.sleepScore }.average()
            val avgWithout = withoutTag.map { it.sleepScore }.average()
            TagCorrelation(tag, avgWith, avgWithout, avgWith - avgWithout, withTag.size)
        }.sortedBy { it.scoreDelta }
}
