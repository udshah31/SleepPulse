import SleepPulseShared

/// Explicit preview/test fixtures. The running application's store never uses these nights.
enum DemoNights {
    static func state(count: Int = 14, missingHrv: Bool = false) -> TrackingState {
        let summaries = (0..<max(0, count)).map { age in
            let day = Int32(20_733 - age)
            let hr: Int32 = age == 0 ? 54 : (age < 7 ? 60 : 68)
            let hrv = age == 0 ? 62.5 : (age < 7 ? 50.0 : 40.0)
            return NightlySummary(
                date: Kotlinx_datetimeLocalDate.companion.fromEpochDays(epochDays: day),
                bedtimeEpochMillis: Int64(day) * 86_400_000 + 22 * 3_600_000 + Int64(age) * 300_000,
                sleepScore: Int32(age == 0 ? 86 : 72 + age % 4 * 3),
                avgHeartRateBpm: hr, avgHrvMillis: missingHrv ? nil : KotlinDouble(value: hrv),
                totalSleepMinutes: Int32(480 + age % 3 * 30), deepSleepMinutes: 90, remSleepMinutes: 100, tags: []
            )
        }
        let history = IosHistorySnapshotBuilder.shared.build(nights: summaries)
        return TrackingState(snapshot: IosTrackingSnapshot(phase: "IDLE", score: nil,
            elapsedSeconds: 0, latest: nil, nights: history.nights, error: nil, notice: nil,
            insights: history.insights), isForeground: true)
    }
}
