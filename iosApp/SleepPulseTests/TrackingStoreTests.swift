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
