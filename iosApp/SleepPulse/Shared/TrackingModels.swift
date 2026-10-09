import Foundation
import SleepPulseShared

enum TrackingPhase: String {
    case recovering = "RECOVERING", idle = "IDLE", starting = "STARTING"
    case tracking = "TRACKING", saving = "SAVING", failed = "FAILED"

    var title: String {
        switch self {
        case .recovering: "Loading and recovering saved sessions…"
        case .idle: "Ready to track"
        case .starting: "Starting…"
        case .tracking: "Recording simulated readings"
        case .saving: "Saving session…"
        case .failed: "Tracking needs attention"
        }
    }
}

struct TrackingState: Equatable {
    var phase: TrackingPhase = .recovering
    var score: Int?
    var elapsedSeconds: Int64 = 0
    var latestReading: SleepReading?
    var nights: [SavedNight] = []
    var insights: NightInsights?
    var error: String?
    var notice: String?
    var isForeground = true

    var canStart: Bool { phase == .idle && isForeground }
    var canStop: Bool { phase == .tracking }
    var elapsedText: String { String(format: "%02lld:%02lld", elapsedSeconds / 60, elapsedSeconds % 60) }
    var isLoadingHistory: Bool { phase == .recovering || (insights == nil && error == nil) }
    var insightsStatusText: String {
        if isLoadingHistory { return "Loading saved-night insights…" }
        if error != nil {
            guard !nights.isEmpty, let insights else { return "Saved-night insights unavailable" }
            return "Last loaded data · \(insights.latestDateText)"
        }
        guard !nights.isEmpty, let insights else { return "No saved night yet" }
        return "Latest recorded date · \(insights.latestDateText)"
    }
}

struct SavedNight: Equatable, Identifiable {
    let id: Int32
    let isoDate: String
    let score: Int
    let totalMinutes: Int
    let deepMinutes: Int
    let remMinutes: Int
    let averageHeartRate: Int
    let averageHrv: Double?

    var durationText: String { totalMinutes == 0 ? "<1 min" : "\(totalMinutes) min" }
    var hrvText: String { averageHrv.map { "\(Int($0.rounded()))" } ?? "—" }
    var hrvAccessibilityText: String { averageHrv.map { "\(Int($0.rounded())) milliseconds" } ?? "unavailable" }

    var dateText: String { NightDateFormatting.text(isoDate) }
}

extension TrackingState {
    init(snapshot: IosTrackingSnapshot, isForeground: Bool) {
        self.init(
            phase: TrackingPhase(rawValue: snapshot.phase) ?? .failed,
            score: snapshot.score.map { Int($0.intValue) }, elapsedSeconds: snapshot.elapsedSeconds,
            latestReading: snapshot.latest.map {
                SleepReading(timestampMillis: $0.timestampMillis, heartRateBpm: $0.heartRateBpm,
                    hrvMillis: $0.hrvMillis?.doubleValue, stage: ReadingStage(sharedName: $0.stage))
            },
            nights: snapshot.nights.map {
                SavedNight(id: $0.epochDay, isoDate: $0.isoDate, score: Int($0.score),
                    totalMinutes: Int($0.totalMinutes), deepMinutes: Int($0.deepMinutes),
                    remMinutes: Int($0.remMinutes), averageHeartRate: Int($0.averageHeartRate), averageHrv: $0.averageHrv?.doubleValue)
            },
            insights: snapshot.insights.map(NightInsights.init),
            error: snapshot.error, notice: snapshot.notice, isForeground: isForeground
        )
    }
}

private extension ReadingStage {
    init(sharedName: String) {
        switch sharedName {
        case "AWAKE": self = .awake
        case "DEEP": self = .deep
        case "REM": self = .rem
        default: self = .light
        }
    }
}
