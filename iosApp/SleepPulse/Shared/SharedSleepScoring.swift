import SleepPulseShared

enum ReadingStage: Equatable {
    case awake
    case light
    case deep
    case rem

    var displayName: String {
        switch self {
        case .awake: "Awake"
        case .light: "Light"
        case .deep: "Deep"
        case .rem: "REM"
        }
    }

    var sharedStage: SleepStage {
        switch self {
        case .awake: .awake
        case .light: .light
        case .deep: .deep
        case .rem: .rem
        }
    }
}

struct SleepReading: Equatable {
    let timestampMillis: Int64
    let heartRateBpm: Int32
    let hrvMillis: Double?
    let stage: ReadingStage
}

protocol SleepScoring {
    func score(readings: [SleepReading]) -> Int
}

struct SharedSleepScoring: SleepScoring {
    func score(readings: [SleepReading]) -> Int {
        let sharedReadings = readings.map { reading in
            SensorReading(
                timestampMillis: reading.timestampMillis,
                heartRateBpm: reading.heartRateBpm,
                hrvMillis: reading.hrvMillis.map { KotlinDouble(value: $0) },
                sleepStage: reading.stage.sharedStage,
            )
        }

        return Int(SleepScoreCalculator.shared.score(readings: sharedReadings))
    }
}
