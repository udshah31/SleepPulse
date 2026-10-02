package com.sleeppulse.app.data.source

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Rolling RMSSD over the last [windowMillis] of RR-intervals. A beat outside 300..2000 ms is kept
 * as a gap marker so the pairs on either side of it are skipped (successive differences across a
 * dropped beat are meaningless). A beat more than 20% off the previous raw beat (missed beat,
 * ectopic) is dropped too; it is compared with the previous raw beat, not the last valid one, so a
 * genuine step change rejects one beat and cannot lock the filter out. Returns null until
 * [minDiffs] valid differences exist. [rmssd] evicts old beats as a side effect, so it expects a
 * non-decreasing `nowMillis`.
 * Not thread-safe; owned by a single GATT callback.
 */
class RmssdCalculator(
    private val windowMillis: Long = 60_000L,
    private val minDiffs: Int = 10,
) {
    private class Beat(val atMillis: Long, val rrMillis: Double) // NaN = dropped

    private val beats = ArrayDeque<Beat>()
    private var prevRaw: Double? = null // previous raw beat added, even if it was rejected

    fun add(timestampMillis: Long, rrMillis: Double) {
        val prev = prevRaw
        val inRange = rrMillis in MIN_RR_MILLIS..MAX_RR_MILLIS
        val steady = prev == null || prev !in MIN_RR_MILLIS..MAX_RR_MILLIS ||
            abs(rrMillis - prev) <= MAX_RELATIVE_CHANGE * prev
        beats.addLast(Beat(timestampMillis, if (inRange && steady) rrMillis else Double.NaN))
        prevRaw = rrMillis
    }

    fun rmssd(nowMillis: Long): Double? {
        while (beats.isNotEmpty() && beats.first().atMillis < nowMillis - windowMillis) beats.removeFirst()
        var sumSquares = 0.0
        var diffs = 0
        for (i in 1 until beats.size) {
            val a = beats[i - 1].rrMillis
            val b = beats[i].rrMillis
            if (a.isNaN() || b.isNaN()) continue
            sumSquares += (b - a) * (b - a)
            diffs++
        }
        return if (diffs < minDiffs) null else sqrt(sumSquares / diffs)
    }

    fun reset() {
        beats.clear()
        prevRaw = null
    }

    private companion object {
        const val MIN_RR_MILLIS = 300.0
        const val MAX_RR_MILLIS = 2000.0
        const val MAX_RELATIVE_CHANGE = 0.20
    }
}
