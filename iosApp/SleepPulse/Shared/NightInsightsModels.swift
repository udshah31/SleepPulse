import Foundation
import SleepPulseShared

enum MetricTrendDirection: String {
    case rising = "RISING", falling = "FALLING", stable = "STABLE"
    var title: String { rawValue.capitalized }
    var icon: String {
        switch self { case .rising: "arrow.up.right"; case .falling: "arrow.down.right"; case .stable: "arrow.right" }
    }
}

struct RecoveryInsight: Equatable {
    let score: Int
    let tier: String
    let guidance: String
    let hrvDeviation: Double?
    let heartRateDeviation: Double

    var hrvComparison: String {
        hrvDeviation.map { Self.comparison($0, positiveMeansBelow: false) } ?? "Unavailable"
    }
    var heartRateComparison: String { Self.comparison(heartRateDeviation, positiveMeansBelow: true) }

    private static func comparison(_ deviation: Double, positiveMeansBelow: Bool) -> String {
        if abs(deviation) < 0.005 { return "At baseline" }
        let below = positiveMeansBelow ? deviation > 0 : deviation < 0
        return String(format: "%.0f%% %@ baseline", abs(deviation) * 100, below ? "below" : "above")
    }
}

struct ReadinessInsight: Equatable { let score: Int; let tier: String }
struct MetricTrend: Equatable {
    let recentAverage: Double
    let priorAverage: Double
    let direction: MetricTrendDirection
    init(_ snapshot: IosMetricTrendSnapshot) {
        recentAverage = snapshot.recentAverage
        priorAverage = snapshot.priorAverage
        direction = MetricTrendDirection(rawValue: snapshot.direction) ?? .stable
    }
}
struct SleepDebtInsight: Equatable {
    let deficitMinutes: Int
    let nights: Int
    let level: String
    let targetMinutes: Int
    var durationText: String { "\(deficitMinutes / 60)h \(deficitMinutes % 60)m" }
}

struct NightScoreChange: Equatable {
    let previousScore: Int?
    let direction: String?
    var icon: String {
        switch direction { case "UP": "arrow.up.right"; case "DOWN": "arrow.down.right"; default: "minus" }
    }
    var title: String {
        switch direction { case "UP": "Higher"; case "DOWN": "Lower"; case "FLAT": "Steady"; default: "No earlier comparison" }
    }
    var accessibilityText: String {
        guard let previousScore else { return "No earlier comparison" }
        return "\(title) compared with previous recorded score \(previousScore)"
    }
}

struct NightInsights: Equatable {
    let recordedNights: Int
    let baselineNights: Int
    let latestIsoDate: String?
    let recovery: RecoveryInsight?
    let readiness: ReadinessInsight?
    let debt: SleepDebtInsight?
    let consistencyScore: Int?
    let hrvTrend: MetricTrend?
    let heartRateTrend: MetricTrend?
    let recentKnownHrvNights: Int
    let priorKnownHrvNights: Int
    let scoreChanges: [Int32: NightScoreChange]

    var remainingBaselineDates: Int { max(0, 4 - recordedNights) }
    var remainingTrendDates: Int { max(0, 14 - recordedNights) }
    var baselineProgressText: String {
        "\(min(recordedNights, 4)) of 4 dates recorded. \(remainingBaselineDates) more \(remainingBaselineDates == 1 ? "date" : "dates") needed."
    }
    var trendProgressText: String {
        "\(min(recordedNights, 14)) of 14 recorded nights. Compare your latest 7 with the preceding 7."
    }
    var hrvUnavailableText: String {
        remainingTrendDates > 0 ? trendProgressText : "Needs known HRV in both 7-night windows. Missing values stay unavailable."
    }
    var latestDateText: String { latestIsoDate.map(NightDateFormatting.text) ?? "No saved night yet" }

    init(_ snapshot: IosNightInsightsSnapshot) {
        recordedNights = Int(snapshot.recordedNights)
        baselineNights = Int(snapshot.baselineNights)
        latestIsoDate = snapshot.latestIsoDate
        recovery = snapshot.recovery.map {
            RecoveryInsight(score: Int($0.score), tier: $0.tier.capitalized, guidance: $0.guidance,
                hrvDeviation: $0.hrvDeviation?.doubleValue, heartRateDeviation: $0.heartRateDeviation)
        }
        readiness = snapshot.readiness.map { ReadinessInsight(score: Int($0.score), tier: $0.tier.capitalized) }
        debt = snapshot.debt.map {
            SleepDebtInsight(deficitMinutes: Int($0.deficitMinutes), nights: Int($0.nights),
                level: $0.level.replacingOccurrences(of: "_", with: " ").capitalized, targetMinutes: Int($0.targetMinutes))
        }
        consistencyScore = snapshot.consistencyScore.map { Int($0.intValue) }
        hrvTrend = snapshot.hrvTrend.map(MetricTrend.init)
        heartRateTrend = snapshot.heartRateTrend.map(MetricTrend.init)
        recentKnownHrvNights = Int(snapshot.recentKnownHrvNights)
        priorKnownHrvNights = Int(snapshot.priorKnownHrvNights)
        scoreChanges = Dictionary(uniqueKeysWithValues: snapshot.scoreChanges.map {
            ($0.epochDay, NightScoreChange(previousScore: $0.previousScore.map { Int($0.intValue) }, direction: $0.direction))
        })
    }
}
