import XCTest
@testable import SleepPulse

@MainActor
final class DashboardViewModelTests: XCTestCase {
    func testSnapshotUsesSharedScoreAndLatestDemoReading() {
        let viewModel = DashboardViewModel(readings: DemoReadings.night, scoring: SharedSleepScoring())

        XCTAssertEqual(viewModel.state.score, 71)
        XCTAssertEqual(viewModel.state.latestReading?.heartRateBpm, 56)
        XCTAssertEqual(viewModel.state.latestReading?.hrvMillis, 68)
        XCTAssertEqual(viewModel.state.latestReading?.stage, .rem)
    }

    func testEmptyReadingsHaveUnavailableLatestMetric() {
        let viewModel = DashboardViewModel(readings: [], scoring: SharedSleepScoring())

        XCTAssertEqual(viewModel.state.score, 0)
        XCTAssertNil(viewModel.state.latestReading)
    }
}
