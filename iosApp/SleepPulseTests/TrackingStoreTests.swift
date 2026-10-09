import SwiftUI
import XCTest
@testable import SleepPulse

@MainActor
final class TrackingStoreTests: XCTestCase {
    func testRealFrameworkSessionSavesAndReloadsWithoutLiveMetrics() async throws {
        let path = temporaryPath()
        let store = TrackingStore(databasePath: path)
        defer { store.close() }
        try await waitUntil { store.state.phase == .idle }
        XCTAssertNil(store.state.score)
        let home = DashboardViewModel(store: store)
        let history = HistoryViewModel(store: store)
        let recovery = RecoveryViewModel(store: store)
        store.start()
        try await waitUntil { home.state.latestReading != nil }
        XCTAssertNotNil(home.state.score)
        store.stop()
        store.stop()
        try await waitUntil { store.state.phase == .idle && history.nights.count == 1 }
        XCTAssertEqual(recovery.state.insights?.recordedNights, 1)
        XCTAssertEqual(recovery.state.nights, history.nights)
        XCTAssertNil(recovery.state.insights?.recovery)
        XCTAssertEqual(history.nights.first?.durationText, "<1 min")
        await store.closeAndWait()
        let reopened = TrackingStore(databasePath: path)
        defer { reopened.close() }
        try await waitUntil { reopened.state.phase == .idle && reopened.state.nights.count == 1 }
        XCTAssertNil(reopened.state.latestReading)
        XCTAssertNil(reopened.state.score)
        XCTAssertEqual(reopened.state.insights?.recordedNights, 1)
        XCTAssertEqual(reopened.state.insights, recovery.state.insights)
        XCTAssertEqual(reopened.state.nights, history.nights)
    }

    func testInactiveKeepsSessionAndBackgroundSavesWithoutAutoResume() async throws {
        let store = TrackingStore(databasePath: temporaryPath())
        defer { store.close() }
        try await waitUntil { store.state.phase == .idle }
        store.start()
        try await waitUntil { store.state.latestReading != nil }
        store.sceneChanged(.inactive)
        XCTAssertEqual(store.state.phase, .tracking)
        store.sceneChanged(.background)
        try await waitUntil { store.state.phase == .idle && store.state.nights.count == 1 }
        store.sceneChanged(.active)
        XCTAssertEqual(store.state.phase, .idle)
        let recorded = store.state.elapsedSeconds
        try await Task.sleep(nanoseconds: 1_200_000_000)
        XCTAssertEqual(store.state.elapsedSeconds, recorded)
    }

    func testStartBackgroundRaceNeverStartsLater() async throws {
        let store = TrackingStore(databasePath: temporaryPath())
        defer { store.close() }
        try await waitUntil { store.state.phase == .idle }
        store.start()
        store.sceneChanged(.background)
        try await waitUntil { store.state.phase == .idle }
        store.sceneChanged(.active)
        try await Task.sleep(nanoseconds: 1_200_000_000)
        XCTAssertEqual(store.state.phase, .idle)
        XCTAssertNil(store.state.latestReading)
    }

    func testInterruptedSessionRecoversAfterStoreRecreation() async throws {
        let path = temporaryPath()
        let store = TrackingStore(databasePath: path)
        try await waitUntil { store.state.phase == .idle }
        store.start()
        try await waitUntil { store.state.latestReading != nil }
        await store.closeAndWait() // Simulates ownership ending without Stop, leaving a durable raw session.
        let reopened = TrackingStore(databasePath: path)
        defer { reopened.close() }
        try await waitUntil { reopened.state.phase == .idle && reopened.state.nights.count == 1 }
        XCTAssertNil(reopened.state.latestReading)
        XCTAssertFalse(reopened.state.phase == .tracking)
    }

    func testOpenFailureDisablesStartAndRetryOpensRepairedPath() async throws {
        let directory = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        let path = directory.appendingPathComponent("tracking.db").path
        let store = TrackingStore(databasePath: path)
        defer { store.close() }
        try await waitUntil { store.state.phase == .failed }
        XCTAssertFalse(store.state.canStart)
        XCTAssertNotNil(store.state.error)
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        store.retry()
        try await waitUntil { store.state.phase == .idle }
        XCTAssertTrue(store.state.canStart)
    }

    func testUnknownHrvAndDateOnlyDisplayHaveStableNativeValues() {
        let night = SavedNight(id: 20_000, isoDate: "2024-10-04", score: 80, totalMinutes: 0,
            deepMinutes: 0, remMinutes: 0, averageHeartRate: 60, averageHrv: nil)
        XCTAssertEqual(night.dateText, "Oct 4, 2024")
        XCTAssertEqual(night.hrvText, "—")
        XCTAssertEqual(night.hrvAccessibilityText, "unavailable")
    }

    func testAppleHealthImportAndStaleCacheStaySeparateFromSimulatedTrackingAndInsights() async throws {
        let store = TrackingStore(databasePath: temporaryPath())
        defer { store.close() }
        let history = HistoryViewModel(store: store)
        let recovery = RecoveryViewModel(store: store)
        let client = FakeHealthKitClient()
        let directory = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        let cache = HealthKitCacheStore(url: directory.appendingPathComponent("healthkit.json"))
        addTeardownBlock { try FileManager.default.removeItem(at: directory) }
        let end = Date(timeIntervalSince1970: 1_791_460_800)
        client.sampleResult = .success((1...4).map { day in
            let start = end.addingTimeInterval(-Double(day) * 86_400)
            return HealthKitSleepSample(id: "watch-\(day)", sourceIdentifier: "watch", sourceName: "Apple Watch",
                stage: .asleepUnspecified, start: start, end: start.addingTimeInterval(8 * 3_600))
        })
        let health = HealthKitSleepStore(client: client, cache: cache, now: { end })
        try await waitUntil { store.state.phase == .idle }
        let initialInsights = recovery.state.insights
        store.start()
        try await waitUntil { store.state.latestReading != nil }
        XCTAssertEqual(client.requestReadAccessCallCount, 0, "Simulated Start must not request HealthKit access")
        XCTAssertTrue(client.readWindows.isEmpty)

        await health.connect().value
        XCTAssertEqual(health.state.phase, .loaded)
        XCTAssertEqual(health.state.episodes.count, 4)
        XCTAssertEqual(client.requestReadAccessCallCount, 1)
        XCTAssertEqual(store.state.phase, .tracking)
        XCTAssertTrue(store.state.canStop)
        XCTAssertTrue(history.nights.isEmpty, "Imported dates must not seed simulated History/charts")
        XCTAssertEqual(recovery.state.insights, initialInsights, "Four imported dates must not create a Recovery baseline")

        store.stop()
        try await waitUntil { store.state.phase == .idle && history.nights.count == 1 }
        let savedState = store.state
        let savedNights = history.nights
        let savedInsights = recovery.state.insights
        client.sampleResult = .failure(HealthKitClientError.queryFailed("Device locked"))
        await health.refresh().value
        XCTAssertEqual(health.state.phase, .stale)
        XCTAssertEqual(health.state.episodes.count, 4)
        XCTAssertEqual(store.state, savedState)
        XCTAssertEqual(history.nights, savedNights)
        XCTAssertEqual(recovery.state.insights, savedInsights)

        client.sampleResult = .success([])
        await health.refresh().value
        XCTAssertEqual(health.state.phase, .empty)
        XCTAssertEqual(client.requestReadAccessCallCount, 1, "Refresh must not request access again")
        XCTAssertEqual(store.state, savedState)
        XCTAssertEqual(history.nights, savedNights)
        XCTAssertEqual(recovery.state.insights, savedInsights)
        XCTAssertEqual(recovery.state.insights?.recordedNights, 1)
        XCTAssertNil(recovery.state.insights?.recovery)
    }

    private func temporaryPath() -> String {
        FileManager.default.temporaryDirectory.appendingPathComponent("sleeppulse-\(UUID().uuidString).db").path
    }

    private func waitUntil(_ condition: @MainActor () -> Bool) async throws {
        let deadline = Date().addingTimeInterval(8)
        while !condition(), Date() < deadline { try await Task.sleep(nanoseconds: 50_000_000) }
        XCTAssertTrue(condition(), "Timed out waiting for framework state")
        if !condition() { throw NSError(domain: "TrackingTests", code: 1) }
    }
}
