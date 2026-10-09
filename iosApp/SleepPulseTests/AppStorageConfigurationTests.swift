import XCTest
@testable import SleepPulse

@MainActor
final class AppStorageConfigurationTests: XCTestCase {
    private let key = "SLEEPPULSE_UI_TEST_STORAGE_ID"

    func testMissingOverrideKeepsBothDefaultStoresWithoutResolvingTestStorage() throws {
        let configuration = try AppStorageConfiguration(environment: [:], applicationSupport: {
            XCTFail("Normal launches must keep the stores' default storage")
            throw CocoaError(.fileReadUnknown)
        })
        XCTAssertNil(configuration.databasePath)
        XCTAssertNil(configuration.healthKitCacheURL)
    }

    func testInvalidIDsFailClosedBeforeAnyDirectoryAccess() {
        let id = UUID().uuidString
        for value in ["", "../", "/tmp", "../\(id)", "\(id)/..", " \(id)", "\(id)\n", "{\(id)}", "not-a-uuid"] {
            XCTAssertThrowsError(try AppStorageConfiguration(environment: [key: value], applicationSupport: {
                XCTFail("Invalid ID must not resolve or touch storage: \(value)")
                throw CocoaError(.fileReadUnknown)
            }))
        }
    }

    func testFreshUUIDCannotLoadPopulatedNormalOrOtherTestStores() async throws {
        let support = try temporarySupport()
        let normal = support.appendingPathComponent("SleepPulse", isDirectory: true)
        let other = normal.appendingPathComponent("UITests/11111111-1111-4111-8111-111111111111", isDirectory: true)
        for directory in [normal, other] { try await populate(directory) }
        let preserved = try [normal, other].flatMap { directory in
            try ["sleeppulse-simulated.db", "healthkit-sleep-cache.json"].map { name in
                let url = directory.appendingPathComponent(name)
                return (url, try Data(contentsOf: url))
            }
        }

        let id = UUID().uuidString
        let configuration = try AppStorageConfiguration(environment: [key: id], applicationSupport: { support })
        let path = try XCTUnwrap(configuration.databasePath)
        let cacheURL = try XCTUnwrap(configuration.healthKitCacheURL)
        XCTAssertEqual(URL(fileURLWithPath: path).deletingLastPathComponent(),
            normal.appendingPathComponent("UITests/\(id)", isDirectory: true))
        XCTAssertEqual(cacheURL.deletingLastPathComponent(), URL(fileURLWithPath: path).deletingLastPathComponent())
        let tracking = TrackingStore(databasePath: path)
        addTeardownBlock { await tracking.closeAndWait() }
        try await waitUntil { tracking.state.phase == .idle }
        XCTAssertTrue(tracking.state.nights.isEmpty)
        XCTAssertEqual(tracking.state.insights?.recordedNights, 0)
        let health = HealthKitSleepStore(client: FakeHealthKitClient(), cache: HealthKitCacheStore(url: cacheURL))
        XCTAssertEqual(health.state.phase, .notConnected)
        XCTAssertTrue(health.state.episodes.isEmpty)
        XCTAssertNil(health.state.fetchedAt)
        for (url, bytes) in preserved { XCTAssertEqual(try Data(contentsOf: url), bytes) }
    }

    func testSameUUIDReconstructionPreservesSavedNightAndPopulatedHealthCache() async throws {
        let support = try temporarySupport()
        let id = UUID().uuidString
        let first = try AppStorageConfiguration(environment: [key: id], applicationSupport: { support })
        let path = try XCTUnwrap(first.databasePath)
        let cacheURL = try XCTUnwrap(first.healthKitCacheURL)
        try await populate(URL(fileURLWithPath: path).deletingLastPathComponent())
        let bytes = try Data(contentsOf: cacheURL)

        // Canonicalize equivalent UUID spellings and never reset on intentional relaunch.
        let relaunched = try AppStorageConfiguration(environment: [key: id.lowercased()], applicationSupport: { support })
        XCTAssertEqual(relaunched.databasePath, path)
        XCTAssertEqual(relaunched.healthKitCacheURL, cacheURL)
        let tracking = TrackingStore(databasePath: try XCTUnwrap(relaunched.databasePath))
        addTeardownBlock { await tracking.closeAndWait() }
        try await waitUntil { tracking.state.phase == .idle }
        XCTAssertEqual(tracking.state.nights.count, 1)
        XCTAssertEqual(tracking.state.insights?.recordedNights, 1)
        XCTAssertNil(tracking.state.insights?.recovery)
        let health = HealthKitSleepStore(client: FakeHealthKitClient(),
            cache: HealthKitCacheStore(url: try XCTUnwrap(relaunched.healthKitCacheURL)))
        XCTAssertEqual(health.state.phase, .loaded)
        XCTAssertEqual(health.state.episodes.count, 4)
        XCTAssertEqual(try Data(contentsOf: cacheURL), bytes)
    }

    func testDirectoryCreationFailureDoesNotFallBackToUserStorage() throws {
        let support = try temporarySupport()
        try Data("not a directory".utf8).write(to: support.appendingPathComponent("SleepPulse"))
        XCTAssertThrowsError(try AppStorageConfiguration(environment: [key: UUID().uuidString], applicationSupport: { support }))
    }

    private func temporarySupport() throws -> URL {
        let url = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString, isDirectory: true)
        try FileManager.default.createDirectory(at: url, withIntermediateDirectories: true)
        addTeardownBlock { try FileManager.default.removeItem(at: url) }
        return url
    }

    private func populate(_ directory: URL) async throws {
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        let tracking = TrackingStore(databasePath: directory.appendingPathComponent("sleeppulse-simulated.db").path)
        addTeardownBlock { await tracking.closeAndWait() }
        try await waitUntil { tracking.state.phase == .idle }
        tracking.start()
        try await waitUntil { tracking.state.latestReading != nil }
        tracking.stop()
        try await waitUntil { tracking.state.phase == .idle && tracking.state.nights.count == 1 }
        await tracking.closeAndWait()
        let now = Date(timeIntervalSince1970: 1_791_460_800)
        let episodes = (1...4).map { day in
            HealthKitSleepEpisode(id: "watch-\(day)", sourceIdentifier: "watch", sourceName: "Apple Watch",
                start: now.addingTimeInterval(-Double(day) * 86_400),
                end: now.addingTimeInterval(-Double(day) * 86_400 + 28_800),
                inBedMinutes: nil, asleepMinutes: 480, awakeMinutes: nil, coreMinutes: nil, deepMinutes: nil, remMinutes: nil)
        }
        try HealthKitCacheStore(url: directory.appendingPathComponent("healthkit-sleep-cache.json"))
            .replace(with: HealthKitSleepCache(formatVersion: 1, episodes: episodes, fetchedAt: now,
                windowStart: now.addingTimeInterval(-30 * 86_400), windowEnd: now))
    }

    private func waitUntil(_ condition: @MainActor () -> Bool) async throws {
        let deadline = Date().addingTimeInterval(8)
        while !condition(), Date() < deadline { try await Task.sleep(for: .milliseconds(50)) }
        XCTAssertTrue(condition(), "Timed out waiting for real tracking state")
        if !condition() { throw NSError(domain: "AppStorageTests", code: 1) }
    }
}
