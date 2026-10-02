package com.sleeppulse.app.data.source

import kotlin.math.sqrt

/**
 * Rolling RMSSD over the last [windowMillis] of RR-intervals. A beat outside 300..2000 ms is kept
 * as a gap marker so the pairs on either side of it are skipped (successive differences across a
 * dropped beat are meaningless). Returns null until [minDiffs] valid differences exist.
 * Not thread-safe; owned by a single GATT callback.
 */
class RmssdCalculator(
    private val windowMillis: Long = 60_000L,
    private val minDiffs: Int = 10,
) {
    private class Beat(val atMillis: Long, val rrMillis: Double) // NaN = dropped

    private val beats = ArrayDeque<Beat>()

    fun add(timestampMillis: Long, rrMillis: Double) {
        beats.addLast(Beat(timestampMillis, if (rrMillis in MIN_RR_MILLIS..MAX_RR_MILLIS) rrMillis else Double.NaN))
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

    fun reset() = beats.clear()

    private companion object {
        const val MIN_RR_MILLIS = 300.0
        const val MAX_RR_MILLIS = 2000.0
    }
}
