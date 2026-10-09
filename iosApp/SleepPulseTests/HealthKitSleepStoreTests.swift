import Foundation
import XCTest
@testable import SleepPulse

@MainActor
final class HealthKitSleepStoreTests: XCTestCase {
    private let now = healthDate("2026-10-08T12:00:00Z")

    func testInitializationLoadsCacheWithoutRequestingPermissionOrQuerying() {
        let old = savedCache()
        let client = FakeHealthKitClient()
        let storage = FakeCacheStorage(value: old)
        let store = HealthKitSleepStore(client: client, cache: storage)

        assertSnapshot(store.state, equals: old)
        XCTAssertEqual(store.state.phase, .loaded)
        XCTAssertFalse(store.isRefreshing)
        XCTAssertEqual(client.requestReadAccessCallCount, 0)
        XCTAssertTrue(client.readWindows.isEmpty)
        XCTAssertEqual(storage.writeCount, 0)
    }

    func testInitializationWithoutCacheDoesNotStartWork() {
        let client = FakeHealthKitClient()
        let store = HealthKitSleepStore(client: client, cache: FakeCacheStorage())
        XCTAssertEqual(store.state.phase, .notConnected)
        XCTAssertNil(store.state.fetchedAt)
        XCTAssertEqual(client.requestReadAccessCallCount, 0)
        XCTAssertTrue(client.readWindows.isEmpty)
    }

    func testConnectRequestsOnceThenNormalizesAndPersistsWholeWindow() async throws {
        let client = FakeHealthKitClient()
        let sample = sleepSample()
        client.sampleResult = .success([sample, sample])
        let storage = FakeCacheStorage()
        let store = HealthKitSleepStore(client: client, cache: storage, now: { self.now }, calendar: utcCalendar())

        let operation = store.connect()
        XCTAssertTrue(store.isRefreshing)
        XCTAssertEqual(store.state.phase, .loading)
        await operation.value

        XCTAssertEqual(client.requestReadAccessCallCount, 1)
        XCTAssertEqual(client.readWindows.count, 1)
        XCTAssertEqual(client.readWindows.first?.from, healthDate("2026-09-08T12:00:00Z"))
        XCTAssertEqual(client.readWindows.first?.to, now)
        XCTAssertEqual(storage.writeCount, 1)
        XCTAssertEqual(store.state.phase, .loaded)
        XCTAssertFalse(store.isRefreshing)
        XCTAssertNil(store.state.error)
        XCTAssertNil(store.state.openSettingsURL)
        XCTAssertEqual(store.state.episodes.count, 1)
        XCTAssertEqual(store.state.episodes.first?.sourceIdentifier, "watch")
        XCTAssertEqual(store.state.episodes.first?.coreMinutes, 60)
        XCTAssertEqual(store.state.episodes.first?.asleepMinutes, 60)
        XCTAssertNil(store.state.episodes.first?.deepMinutes)
        assertSnapshot(store.state, equals: try XCTUnwrap(storage.value))
        XCTAssertEqual(store.state.fetchedAt, now)
    }

    func testManualRefreshNeverRequestsAccessOrInterpretsRequestStatusAsDenial() async {
        let statuses: [HealthKitAuthorizationState] = [.unknown, .shouldRequest, .unnecessary, .requestFailed("status error")]
        for status in statuses {
            let client = FakeHealthKitClient()
            client.authorizationResult = status
            let store = HealthKitSleepStore(client: client, cache: FakeCacheStorage(), now: { self.now })
            await store.refresh().value
            XCTAssertEqual(client.requestReadAccessCallCount, 0)
            XCTAssertEqual(client.readWindows.count, 1)
            XCTAssertEqual(store.state.phase, .empty)
            XCTAssertNil(store.state.error)
            XCTAssertNil(store.state.openSettingsURL)
        }
    }

    func testEmptySuccessReplacesNonemptyCacheAndSurvivesRelaunch() async {
        let storage = FakeCacheStorage(value: savedCache())
        let client = FakeHealthKitClient()
        let store = HealthKitSleepStore(client: client, cache: storage, now: { self.now })
        await store.refresh().value
        XCTAssertEqual(store.state.phase, .empty)
        XCTAssertEqual(store.state.episodes, [])
        XCTAssertEqual(store.state.fetchedAt, now)
        XCTAssertEqual(storage.value?.episodes, [])
        XCTAssertEqual(storage.writeCount, 1)

        let reopened = HealthKitSleepStore(client: client, cache: storage)
        XCTAssertEqual(reopened.state.phase, .empty)
        XCTAssertEqual(reopened.state.fetchedAt, now)
        XCTAssertEqual(client.readWindows.count, 1)
    }

    func testFullWindowReplacementRemovesDeletedEpisodesAndPreviousSources() async {
        let storage = FakeCacheStorage(value: savedCache())
        let client = FakeHealthKitClient()
        client.sampleResult = .success([sleepSample(source: "new-source")])
        let store = HealthKitSleepStore(client: client, cache: storage, now: { self.now })
        await store.refresh().value
        XCTAssertEqual(store.state.episodes.map(\.sourceIdentifier), ["new-source"])
        XCTAssertEqual(storage.value?.episodes, store.state.episodes)

        client.sampleResult = .success([sleepSample(source: "replacement")])
        await store.refresh().value
        XCTAssertEqual(store.state.episodes.map(\.sourceIdentifier), ["replacement"])
        XCTAssertEqual(storage.value?.episodes, store.state.episodes)
        XCTAssertEqual(storage.writeCount, 2)
    }

    func testUnavailableDoesNotPromptQueryOrWrite() async {
        let client = FakeHealthKitClient()
        client.available = false
        let storage = FakeCacheStorage()
        let store = HealthKitSleepStore(client: client, cache: storage)
        XCTAssertEqual(store.state.phase, .unavailable)
        await store.connect().value
        await store.refresh().value
        XCTAssertEqual(store.state.phase, .unavailable)
        XCTAssertEqual(client.requestReadAccessCallCount, 0)
        XCTAssertTrue(client.readWindows.isEmpty)
        XCTAssertEqual(storage.writeCount, 0)
        XCTAssertNil(store.state.openSettingsURL)
    }

    func testUnavailabilityRetainsValidCache() async {
        let old = savedCache()
        let client = FakeHealthKitClient()
        client.available = false
        let storage = FakeCacheStorage(value: old)
        let store = HealthKitSleepStore(client: client, cache: storage)
        assertSnapshot(store.state, equals: old)
        await store.refresh().value
        XCTAssertEqual(store.state.phase, .stale)
        assertSnapshot(store.state, equals: old)
        XCTAssertEqual(storage.value, old)
    }

    func testAuthorizationFailureDoesNotQueryAndPreservesCacheWhenPresent() async {
        for old in [nil, savedCache()] {
            let client = FakeHealthKitClient()
            client.requestAccessError = HealthKitClientError.requestFailed("request interrupted")
            let storage = FakeCacheStorage(value: old)
            let store = HealthKitSleepStore(client: client, cache: storage)
            await store.connect().value
            XCTAssertEqual(store.state.phase, old == nil ? .failed : .stale)
            XCTAssertEqual(store.state.error, "request interrupted")
            XCTAssertNil(store.state.openSettingsURL)
            XCTAssertFalse(store.isRefreshing)
            XCTAssertTrue(client.readWindows.isEmpty)
            XCTAssertEqual(storage.value, old)
            XCTAssertEqual(storage.writeCount, 0)
            if let old { assertSnapshot(store.state, equals: old) }
        }
    }

    func testQueryFailureRetainsNonemptyAndEmptySuccessCaches() async {
        for old in [savedCache(), savedCache(episodes: [])] {
            let client = FakeHealthKitClient()
            client.sampleResult = .failure(HealthKitClientError.queryFailed("device locked"))
            let storage = FakeCacheStorage(value: old)
            let store = HealthKitSleepStore(client: client, cache: storage)
            await store.refresh().value
            XCTAssertEqual(store.state.phase, .stale)
            XCTAssertEqual(store.state.error, "device locked")
            XCTAssertNil(store.state.openSettingsURL)
            assertSnapshot(store.state, equals: old)
            XCTAssertEqual(storage.value, old)
            XCTAssertEqual(storage.writeCount, 0)
        }
    }

    func testQueryFailureWithoutCacheCanBeRetriedManually() async {
        let client = FakeHealthKitClient()
        client.sampleResult = .failure(HealthKitClientError.queryFailed("query failed"))
        let store = HealthKitSleepStore(client: client, cache: FakeCacheStorage(), now: { self.now })
        await store.refresh().value
        XCTAssertEqual(store.state.phase, .failed)
        XCTAssertNil(store.state.fetchedAt)
        XCTAssertNotNil(store.state.error)
        client.sampleResult = .success([])
        await store.refresh().value
        XCTAssertEqual(store.state.phase, .empty)
        XCTAssertNil(store.state.error)
        XCTAssertEqual(client.requestReadAccessCallCount, 0)
    }

    func testExplicitNeedsSettingsFromRequestOrQueryExposesCodableURLOnlyUntilRetry() async throws {
        for duringConnect in [true, false] {
            let client = FakeHealthKitClient()
            let failure = HealthKitClientError.needsSettings("system restricted access")
            if duringConnect { client.requestAccessError = failure }
            else { client.sampleResult = .failure(failure) }
            let store = HealthKitSleepStore(client: client, cache: FakeCacheStorage(value: savedCache()))
            await (duringConnect ? store.connect() : store.refresh()).value
            XCTAssertEqual(store.state.phase, .stale)
            XCTAssertEqual(store.state.openSettingsURL?.absoluteString, "app-settings:")
            XCTAssertEqual(try JSONDecoder().decode(HealthKitSleepState.self, from: JSONEncoder().encode(store.state)), store.state)
            client.requestAccessError = nil
            client.sampleResult = .success([])
            await store.refresh().value
            XCTAssertNil(store.state.openSettingsURL)
            XCTAssertEqual(store.state.phase, .empty)
        }
    }

    func testAtomicWriteFailureKeepsPriorSnapshotAndDiskBytesThenAllowsRetry() async throws {
        let url = try temporaryCacheURL()
        let old = savedCache()
        try HealthKitCacheStore(url: url).replace(with: old)
        let bytes = try Data(contentsOf: url)
        var failWrite = true
        var writes = 0
        let cache = HealthKitCacheStore(url: url, write: { data, destination, options in
            writes += 1
            XCTAssertTrue(options.contains(.atomic))
            XCTAssertEqual(destination, url)
            if failWrite { throw CocoaError(.fileWriteOutOfSpace) }
            try data.write(to: destination, options: options)
        })
        let client = FakeHealthKitClient()
        client.sampleResult = .success([sleepSample(source: "replacement")])
        let store = HealthKitSleepStore(client: client, cache: cache, now: { self.now })
        await store.refresh().value
        XCTAssertEqual(store.state.phase, .stale)
        XCTAssertNotNil(store.state.error)
        assertSnapshot(store.state, equals: old)
        XCTAssertEqual(try Data(contentsOf: url), bytes)
        XCTAssertEqual(try HealthKitCacheStore(url: url).load(), old)
        failWrite = false
        await store.refresh().value
        XCTAssertEqual(writes, 2)
        XCTAssertEqual(store.state.phase, .loaded)
        XCTAssertEqual(try cache.load()?.episodes.map(\.sourceIdentifier), ["replacement"])
    }

    func testStorageEncodingFailureDoesNotPublishNewSnapshot() async throws {
        let old = savedCache()
        let url = try temporaryCacheURL()
        let storage = HealthKitCacheStore(url: url)
        try storage.replace(with: old)
        let bytes = try Data(contentsOf: url)
        var clockReads = 0
        let store = HealthKitSleepStore(client: FakeHealthKitClient(), cache: storage, now: {
            clockReads += 1
            // Valid query window, then an unencodable completion timestamp exercises the real encoder.
            return clockReads == 1 ? self.now : Date(timeIntervalSince1970: .infinity)
        })
        await store.refresh().value
        XCTAssertEqual(store.state.phase, .stale)
        XCTAssertNotNil(store.state.error)
        assertSnapshot(store.state, equals: old)
        XCTAssertEqual(try storage.load(), old)
        XCTAssertEqual(try Data(contentsOf: url), bytes)
    }

    func testInvalidSampleCannotReachNormalizerOrReplaceValidCache() async {
        let old = savedCache()
        let client = FakeHealthKitClient()
        client.sampleResult = .success([HealthKitSleepSample(
            id: "invalid", sourceIdentifier: "watch", sourceName: "Watch", stage: .core,
            start: Date(timeIntervalSince1970: .infinity), end: now
        )])
        let storage = FakeCacheStorage(value: old)
        let store = HealthKitSleepStore(client: client, cache: storage)
        await store.refresh().value
        XCTAssertEqual(store.state.phase, .stale)
        XCTAssertNotNil(store.state.error)
        assertSnapshot(store.state, equals: old)
        XCTAssertEqual(storage.writeCount, 0)
    }

    func testCorruptAndUnknownVersionCacheAreIgnoredWithoutTouchingSimulatedFile() async throws {
        for data in [Data("broken json".utf8), try JSONEncoder().encode(savedCache(version: 999))] {
            let url = try temporaryCacheURL()
            let simulatedURL = url.deletingLastPathComponent().appendingPathComponent("sleeppulse-simulated.db")
            let simulatedBytes = Data("independent simulated database sentinel".utf8)
            try simulatedBytes.write(to: simulatedURL)
            try data.write(to: url)
            let client = FakeHealthKitClient()
            let store = HealthKitSleepStore(client: client, cache: HealthKitCacheStore(url: url), now: { self.now })
            XCTAssertEqual(store.state.phase, .notConnected)
            XCTAssertTrue(store.state.episodes.isEmpty)
            XCTAssertNil(store.state.fetchedAt)
            XCTAssertTrue(client.readWindows.isEmpty)
            XCTAssertEqual(client.requestReadAccessCallCount, 0)
            XCTAssertEqual(try Data(contentsOf: simulatedURL), simulatedBytes)
            await store.refresh().value
            XCTAssertEqual(store.state.phase, .empty)
            XCTAssertEqual(try HealthKitCacheStore(url: url).load()?.fetchedAt, now)
            XCTAssertEqual(try Data(contentsOf: simulatedURL), simulatedBytes)
        }
    }

    func testConnectAndRefreshShareGateWhileAuthorizationIsSuspended() async {
        let gate = OperationGate()
        let client = FakeHealthKitClient()
        client.beforeRequest = { await gate.suspend() }
        let storage = FakeCacheStorage(value: savedCache())
        let store = HealthKitSleepStore(client: client, cache: storage, now: { self.now })
        let first = store.connect()
        await gate.waitUntilEntered()
        let refresh = store.refresh()
        let connect = store.connect()
        XCTAssertEqual(client.requestReadAccessCallCount, 1)
        XCTAssertTrue(client.readWindows.isEmpty)
        XCTAssertEqual(storage.writeCount, 0)
        XCTAssertTrue(store.isRefreshing)
        assertSnapshot(store.state, equals: savedCache())
        await gate.release()
        await first.value
        await refresh.value
        await connect.value
        XCTAssertEqual(client.requestReadAccessCallCount, 1)
        XCTAssertEqual(client.readWindows.count, 1)
        XCTAssertEqual(storage.writeCount, 1)
        XCTAssertEqual(store.state.phase, .empty)
    }

    func testRepeatedRefreshAndConnectShareGateWhileQueryIsSuspended() async {
        let gate = OperationGate()
        let client = FakeHealthKitClient()
        client.beforeRead = { await gate.suspend() }
        let storage = FakeCacheStorage(value: savedCache())
        var clock = now
        let store = HealthKitSleepStore(client: client, cache: storage, now: { clock })
        let first = store.refresh()
        await gate.waitUntilEntered()
        let second = store.refresh()
        let connect = store.connect()
        XCTAssertEqual(client.readWindows.count, 1)
        XCTAssertEqual(client.requestReadAccessCallCount, 0)
        XCTAssertEqual(storage.writeCount, 0)
        assertSnapshot(store.state, equals: savedCache())
        clock = now.addingTimeInterval(60)
        await gate.release()
        await first.value
        await second.value
        await connect.value
        XCTAssertEqual(storage.writeCount, 1)
        XCTAssertEqual(client.readWindows.count, 1)
        XCTAssertEqual(client.requestReadAccessCallCount, 0)
        XCTAssertEqual(store.state.windowEnd, now)
        XCTAssertEqual(store.state.fetchedAt, clock)
        XCTAssertFalse(store.isRefreshing)
    }

    func testThirtyCalendarDayWindowCrossesSpringAndFallDST() async {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "America/Los_Angeles")!
        let cases = [
            ("2026-03-20T12:00:00-07:00", "2026-02-18T12:00:00-08:00", 719.0),
            ("2026-11-15T12:00:00-08:00", "2026-10-16T12:00:00-07:00", 721.0)
        ]
        for (endText, startText, hours) in cases {
            let end = healthDate(endText)
            let client = FakeHealthKitClient()
            let store = HealthKitSleepStore(client: client, cache: FakeCacheStorage(), now: { end }, calendar: calendar)
            await store.refresh().value
            XCTAssertEqual(client.readWindows.first?.from, healthDate(startText))
            XCTAssertEqual(client.readWindows.first?.to, end)
            XCTAssertEqual(store.state.windowStart, healthDate(startText))
            XCTAssertEqual(store.state.windowEnd, end)
            XCTAssertEqual(end.timeIntervalSince(healthDate(startText)) / 3_600, hours)
        }
    }

    private func assertSnapshot(_ state: HealthKitSleepState, equals cache: HealthKitSleepCache,
                                file: StaticString = #filePath, line: UInt = #line) {
        XCTAssertEqual(state.episodes, cache.episodes, file: file, line: line)
        XCTAssertEqual(state.fetchedAt, cache.fetchedAt, file: file, line: line)
        XCTAssertEqual(state.windowStart, cache.windowStart, file: file, line: line)
        XCTAssertEqual(state.windowEnd, cache.windowEnd, file: file, line: line)
    }
}

final class HealthKitCacheStoreTests: XCTestCase {
    func testMissingCacheIsNotAnEmptySuccess() throws {
        XCTAssertNil(try HealthKitCacheStore(url: temporaryCacheURL()).load())
    }

    func testRoundTripAndReplacementIncludeMetadataAndOptionalStages() throws {
        let url = try temporaryCacheURL().deletingLastPathComponent()
            .appendingPathComponent("nested/healthkit-sleep-cache.json")
        let cache = HealthKitCacheStore(url: url)
        let old = savedCache()
        try cache.replace(with: old)
        XCTAssertEqual(try HealthKitCacheStore(url: url).load(), old)
        let empty = savedCache(episodes: [])
        try cache.replace(with: empty)
        XCTAssertEqual(try HealthKitCacheStore(url: url).load(), empty)
    }

    func testCorruptAndUnknownVersionsAreRejected() throws {
        let url = try temporaryCacheURL()
        let cache = HealthKitCacheStore(url: url)
        for bytes in [Data("{}".utf8), Data("not json".utf8), try JSONEncoder().encode(savedCache(version: 999))] {
            try bytes.write(to: url)
            XCTAssertThrowsError(try cache.load())
            XCTAssertEqual(try Data(contentsOf: url), bytes)
        }
    }

    func testEncodingFailureLeavesPreviousFileIntactWithoutCallingWriter() throws {
        let url = try temporaryCacheURL()
        try HealthKitCacheStore(url: url).replace(with: savedCache())
        let bytes = try Data(contentsOf: url)
        let cache = HealthKitCacheStore(url: url, write: { _, _, _ in XCTFail("Must encode before writing") })
        let invalid = HealthKitSleepCache(formatVersion: 1, episodes: [], fetchedAt: Date(timeIntervalSince1970: .infinity),
                                         windowStart: healthDate("2026-09-08T12:00:00Z"), windowEnd: healthDate("2026-10-08T12:00:00Z"))
        XCTAssertThrowsError(try cache.replace(with: invalid)) { XCTAssertTrue($0 is EncodingError) }
        XCTAssertEqual(try Data(contentsOf: url), bytes)
        XCTAssertEqual(try HealthKitCacheStore(url: url).load(), savedCache())
    }
}

// Native fake only replaces HealthKit I/O. All transitions/normalization/cache decisions are real.
final class FakeHealthKitClient: HealthKitClient {
    var available = true
    var authorizationResult: HealthKitAuthorizationState = .shouldRequest
    var requestAccessError: Error?
    var sampleResult: Result<[HealthKitSleepSample], Error> = .success([])
    var beforeRequest: (() async -> Void)?
    var beforeRead: (() async -> Void)?
    private(set) var authorizationStateCallCount = 0
    private(set) var requestReadAccessCallCount = 0
    private(set) var readWindows: [(from: Date, to: Date)] = []

    func isHealthDataAvailable() -> Bool { available }

    func authorizationState() async -> HealthKitAuthorizationState {
        authorizationStateCallCount += 1
        return authorizationResult
    }

    func requestReadAccess() async throws {
        requestReadAccessCallCount += 1
        await beforeRequest?()
        if let requestAccessError { throw requestAccessError }
    }

    func readSleepSamples(from: Date, to: Date) async throws -> [HealthKitSleepSample] {
        readWindows.append((from, to))
        await beforeRead?()
        return try sampleResult.get()
    }
}

private final class FakeCacheStorage: HealthKitCacheStorage {
    var value: HealthKitSleepCache?
    private(set) var writeCount = 0
    init(value: HealthKitSleepCache? = nil) { self.value = value }
    func load() throws -> HealthKitSleepCache? { value }
    func replace(with cache: HealthKitSleepCache) throws {
        writeCount += 1
        value = cache
    }
}

// One-shot rendezvous: tests know exactly when a dependency is suspended, without timeouts/polling.
private actor OperationGate {
    private var entered = false
    private var released = false
    private var waitingForEntry: CheckedContinuation<Void, Never>?
    private var suspended: [CheckedContinuation<Void, Never>] = []
    func suspend() async {
        guard !released else { return }
        await withCheckedContinuation { continuation in
            suspended.append(continuation)
            entered = true
            waitingForEntry?.resume()
            waitingForEntry = nil
        }
    }
    func waitUntilEntered() async {
        if !entered { await withCheckedContinuation { waitingForEntry = $0 } }
    }
    func release() {
        released = true
        suspended.forEach { $0.resume() }
        suspended = []
    }
}

private func healthDate(_ text: String) -> Date { ISO8601DateFormatter().date(from: text)! }
private func utcCalendar() -> Calendar {
    var calendar = Calendar(identifier: .gregorian)
    calendar.timeZone = TimeZone(secondsFromGMT: 0)!
    return calendar
}
private func sleepSample(source: String = "watch") -> HealthKitSleepSample {
    HealthKitSleepSample(id: "sample", sourceIdentifier: source, sourceName: "Watch", stage: .core,
                        start: healthDate("2026-10-07T22:00:00Z"), end: healthDate("2026-10-07T23:00:00Z"))
}
private func savedCache(version: Int = 1, episodes: [HealthKitSleepEpisode]? = nil) -> HealthKitSleepCache {
    let old = HealthKitSleepEpisode(id: "old", sourceIdentifier: "old-watch", sourceName: "Old Watch",
        start: healthDate("2026-10-06T22:00:00Z"), end: healthDate("2026-10-07T06:00:00Z"),
        inBedMinutes: 480, asleepMinutes: 420, awakeMinutes: nil, coreMinutes: 300, deepMinutes: 60, remMinutes: 60)
    return HealthKitSleepCache(formatVersion: version, episodes: episodes ?? [old],
        fetchedAt: healthDate("2026-10-07T12:00:00Z"), windowStart: healthDate("2026-09-07T12:00:00Z"),
        windowEnd: healthDate("2026-10-07T12:00:00Z"))
}
private extension XCTestCase {
    func temporaryCacheURL() throws -> URL {
        let directory = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString, isDirectory: true)
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        addTeardownBlock { try FileManager.default.removeItem(at: directory) }
        return directory.appendingPathComponent("healthkit-sleep-cache.json")
    }
}
