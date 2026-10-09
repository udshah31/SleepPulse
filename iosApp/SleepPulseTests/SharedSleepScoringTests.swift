import XCTest
@testable import SleepPulse

final class SharedSleepScoringTests: XCTestCase {
    func testDemoScoreUsesSharedCalculator() {
        XCTAssertEqual(SharedSleepScoring().score(readings: DemoReadings.night), 71)
    }

    func testEmptyReadingsScoreZero() {
        XCTAssertEqual(SharedSleepScoring().score(readings: []), 0)
    }

    func testMissingHrvRemainsUnknown() {
        let readings = DemoReadings.night.map {
            SleepReading(
                timestampMillis: $0.timestampMillis,
                heartRateBpm: $0.heartRateBpm,
                hrvMillis: nil,
                stage: $0.stage,
            )
        }

        XCTAssertEqual(SharedSleepScoring().score(readings: readings), 80)
    }
}
