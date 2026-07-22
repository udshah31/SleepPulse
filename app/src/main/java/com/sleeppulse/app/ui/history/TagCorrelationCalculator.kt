package com.sleeppulse.app.ui.history

import com.sleeppulse.app.data.model.NightlySummary

data class TagCorrelation(
    val tag: String,
    val avgScoreWithTag: Double,
    val avgScoreWithoutTag: Double,
    val scoreDelta: Double, // withTag - withoutTag; negative means the tag correlates with worse nights
    val taggedNightCount: Int,
)

/**
 * Compares average sleep score on nights carrying a given tag vs nights without it, across
 * all recorded history. Turns manually-entered tags (caffeine, late-workout, ...) into a
 * personal correlation insight instead of a note attached to a single night.
 *
 * A tag needs at least [MIN_TAGGED_NIGHTS] occurrences to be reported — otherwise the
 * comparison is too noisy to act on. Results are sorted worst-impact first.
 */
object TagCorrelationCalculator {

    private const val MIN_TAGGED_NIGHTS = 3

    fun analyze(nights: List<NightlySummary>): List<TagCorrelation> {
        val allTags = nights.flatMap { it.tags }.distinct()

        return allTags.mapNotNull { tag ->
            val withTag = nights.filter { tag in it.tags }
            if (withTag.size < MIN_TAGGED_NIGHTS) return@mapNotNull null

            val withoutTag = nights.filter { tag !in it.tags }
            if (withoutTag.isEmpty()) return@mapNotNull null

            val avgWith = withTag.map { it.sleepScore }.average()
            val avgWithout = withoutTag.map { it.sleepScore }.average()

            TagCorrelation(
                tag = tag,
                avgScoreWithTag = avgWith,
                avgScoreWithoutTag = avgWithout,
                scoreDelta = avgWith - avgWithout,
                taggedNightCount = withTag.size,
            )
        }.sortedBy { it.scoreDelta }
    }
}
