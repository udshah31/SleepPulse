import Foundation

enum HealthKitSleepStage: String, Codable, Equatable {
    case inBed
    case asleepUnspecified
    case awake
    case core
    case deep
    case rem
    case unknown
}

struct HealthKitSleepSample: Codable, Equatable, Identifiable {
    let id: String
    let sourceIdentifier: String
    let sourceName: String
    let stage: HealthKitSleepStage
    let start: Date
    let end: Date
}

struct HealthKitSleepEpisode: Codable, Equatable, Identifiable {
    let id: String
    let sourceIdentifier: String
    let sourceName: String
    let start: Date
    let end: Date
    let inBedMinutes: Int?
    let asleepMinutes: Int?
    let awakeMinutes: Int?
    let coreMinutes: Int?
    let deepMinutes: Int?
    let remMinutes: Int?
}

struct HealthKitSleepCache: Codable, Equatable {
    let formatVersion: Int
    let episodes: [HealthKitSleepEpisode]
    let fetchedAt: Date
    let windowStart: Date
    let windowEnd: Date
}

enum HealthKitSleepPhase: String, Codable, Equatable {
    case unavailable
    case notConnected
    case loading
    case loaded
    case empty
    case stale
    case failed
}

struct HealthKitSleepState: Codable, Equatable {
    let phase: HealthKitSleepPhase
    let episodes: [HealthKitSleepEpisode]
    let fetchedAt: Date?
    let windowStart: Date?
    let windowEnd: Date?
    let error: String?

    init(
        phase: HealthKitSleepPhase,
        episodes: [HealthKitSleepEpisode] = [],
        fetchedAt: Date? = nil,
        windowStart: Date? = nil,
        windowEnd: Date? = nil,
        error: String? = nil
    ) {
        self.phase = phase
        self.episodes = episodes
        self.fetchedAt = fetchedAt
        self.windowStart = windowStart
        self.windowEnd = windowEnd
        self.error = error
    }
}
