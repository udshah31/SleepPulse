enum DemoReadings {
    static let night: [SleepReading] = [
        SleepReading(timestampMillis: 1_780_000_000_000, heartRateBpm: 58, hrvMillis: 62, stage: .light),
        SleepReading(timestampMillis: 1_780_000_060_000, heartRateBpm: 60, hrvMillis: 65, stage: .deep),
        SleepReading(timestampMillis: 1_780_000_120_000, heartRateBpm: 56, hrvMillis: 68, stage: .rem),
    ]
}
