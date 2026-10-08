package com.sleeppulse.shared.tracking

import kotlinx.coroutines.Job

data class IosReadingSnapshot(val timestampMillis: Long, val heartRateBpm: Int, val hrvMillis: Double?, val stage: String)

data class IosNightSnapshot(
    val epochDay: Int,
    val isoDate: String,
    val score: Int,
    val totalMinutes: Int,
    val deepMinutes: Int,
    val remMinutes: Int,
    val averageHeartRate: Int,
    val averageHrv: Double?,
)

data class IosTrackingSnapshot(
    val phase: String = "RECOVERING",
    val score: Int? = null,
    val elapsedSeconds: Long = 0,
    val latest: IosReadingSnapshot? = null,
    val nights: List<IosNightSnapshot> = emptyList(),
    val error: String? = null,
    val notice: String? = null,
)

class TrackingObservation internal constructor(private val job: Job) {
    fun cancel() { job.cancel() }
}
