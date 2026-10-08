import Foundation

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
    var error: String?
    var notice: String?
    var isForeground = true

    var canStart: Bool { phase == .idle && isForeground }
    var canStop: Bool { phase == .tracking }
    var elapsedText: String { String(format: "%02lld:%02lld", elapsedSeconds / 60, elapsedSeconds % 60) }
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

    var dateText: String {
        let parser = DateFormatter()
        parser.calendar = Calendar(identifier: .gregorian)
        parser.locale = Locale(identifier: "en_US_POSIX")
        parser.timeZone = TimeZone(secondsFromGMT: 0)
        parser.dateFormat = "yyyy-MM-dd"
        guard let date = parser.date(from: isoDate) else { return isoDate }
        let formatter = DateFormatter()
        formatter.calendar = Calendar(identifier: .gregorian)
        formatter.timeZone = TimeZone(secondsFromGMT: 0) // Date-only; never shift through local midnight.
        formatter.dateStyle = .medium
        return formatter.string(from: date)
    }
}
