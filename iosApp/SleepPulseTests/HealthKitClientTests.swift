import Foundation
import XCTest
@testable import SleepPulse

final class HealthKitClientTests: XCTestCase {
    private let start = Date(timeIntervalSince1970: 1_790_000_000)
    private let end = Date(timeIntervalSince1970: 1_790_086_400)

    func testReadMapsAllRawSleepCategoriesAndPreservesUnknownValues() async throws {
        let cases: [(Int, HealthKitSleepStage)] = [
            (0, .inBed), (1, .asleepUnspecified), (2, .awake),
            (3, .core), (4, .deep), (5, .rem), (-1, .unknown), (999, .unknown)
        ]
        var operations = stubOperations()
        let records = cases.map { record(value: $0.0) }
        operations.readSleepSamples = { _, _ in records }

        let samples = try await LiveHealthKitClient(operations: operations)
            .readSleepSamples(from: start, to: end)

        XCTAssertEqual(samples.map(\.stage), cases.map { $0.1 })
    }

    func testReadPreservesIdentityProvenanceAndUnclippedDatesWithoutNormalizing() async throws {
        var operations = stubOperations()
        let records = [
            record(value: 3, source: "com.example.watch", name: "Bedroom Watch"),
            record(value: 4, source: "com.example.phone", name: "Sleep App")
        ]
        operations.readSleepSamples = { from, to in
            XCTAssertEqual(from, self.start)
            XCTAssertEqual(to, self.end)
            return records
        }

        let samples = try await LiveHealthKitClient(operations: operations)
            .readSleepSamples(from: start, to: end)

        XCTAssertEqual(samples.count, 2)
        XCTAssertEqual(samples[0].id, "12345678-1234-1234-1234-123456789ABC")
        XCTAssertEqual(samples[0].sourceIdentifier, "com.example.watch")
        XCTAssertEqual(samples[0].sourceName, "Bedroom Watch")
        XCTAssertEqual(samples[0].start, start.addingTimeInterval(-3_600))
        XCTAssertEqual(samples[0].end, end.addingTimeInterval(3_600))
        XCTAssertEqual(samples[1].sourceIdentifier, "com.example.phone")
        XCTAssertEqual(samples[1].sourceName, "Sleep App")
    }

    func testAuthorizationReportsRequestStatusRatherThanReadPermission() async {
        let cases: [(Int, HealthKitAuthorizationState)] = [
            (0, .unknown), (1, .shouldRequest), (2, .unnecessary), (999, .unknown)
        ]
        for (rawValue, expected) in cases {
            var operations = stubOperations()
            operations.authorizationRequestStatus = { rawValue }
            let state = await LiveHealthKitClient(operations: operations).authorizationState()
            XCTAssertEqual(state, expected)
        }
    }

    func testAuthorizationStatusErrorRemainsExplicit() async {
        var operations = stubOperations()
        operations.authorizationRequestStatus = { throw TestFailure("status failed") }

        let state = await LiveHealthKitClient(operations: operations).authorizationState()

        XCTAssertEqual(state, .requestFailed("status failed"))
    }

    func testSuccessfulRequestDoesNotClaimReadAuthorization() async throws {
        var operations = stubOperations()
        operations.authorizationRequestStatus = { 0 }
        operations.requestReadAccess = { true }
        let client = LiveHealthKitClient(operations: operations)

        try await client.requestReadAccess()

        let state = await client.authorizationState()
        XCTAssertEqual(state, .unknown)
    }

    func testUnsuccessfulRequestWithoutSystemErrorIsStillRequestFailure() async {
        var operations = stubOperations()
        operations.requestReadAccess = { false }

        do {
            try await LiveHealthKitClient(operations: operations).requestReadAccess()
            XCTFail("An unsuccessful authorization request must not succeed")
        } catch {
            guard case .requestFailed = error as? HealthKitClientError else {
                return XCTFail("Expected request failure, got \(error)")
            }
        }
    }

    func testRequestErrorPreservesItsMessageAsRequestFailure() async {
        var operations = stubOperations()
        operations.requestReadAccess = { throw TestFailure("request interrupted") }

        await assertError(.requestFailed("request interrupted")) {
            try await LiveHealthKitClient(operations: operations).requestReadAccess()
        }
    }

    func testEmptyQueryIsSuccessfulAndDoesNotInferDenialOrPrompt() async throws {
        var operations = stubOperations()
        operations.authorizationRequestStatus = {
            XCTFail("Reading must not gate on authorization request status")
            return 1
        }
        operations.requestReadAccess = {
            XCTFail("Reading must not prompt for permissions")
            return false
        }
        operations.readSleepSamples = { _, _ in [] }

        let samples = try await LiveHealthKitClient(operations: operations)
            .readSleepSamples(from: start, to: end)

        XCTAssertEqual(samples, [])
    }

    func testQueryErrorRemainsQueryFailureRatherThanEmptyDataOrDenial() async {
        var operations = stubOperations()
        operations.readSleepSamples = { _, _ in throw TestFailure("device locked") }

        await assertError(.queryFailed("device locked")) {
            _ = try await LiveHealthKitClient(operations: operations)
                .readSleepSamples(from: self.start, to: self.end)
        }
    }

    func testUnavailableHealthDataShortCircuitsAllSystemOperations() async {
        var operations = stubOperations()
        operations.isHealthDataAvailable = { false }
        operations.isSleepAnalysisSupported = {
            XCTFail("Unavailable HealthKit must short-circuit before looking up types")
            return true
        }
        let client = LiveHealthKitClient(operations: operations)

        XCTAssertFalse(client.isHealthDataAvailable())
        let state = await client.authorizationState()
        XCTAssertEqual(state, .unavailable)
        await assertError(.unavailable) { try await client.requestReadAccess() }
        await assertError(.unavailable) {
            _ = try await client.readSleepSamples(from: self.start, to: self.end)
        }
    }

    func testUnsupportedSleepTypeIsDistinctFromUnavailableHealthData() async {
        var operations = stubOperations()
        operations.isSleepAnalysisSupported = { false }
        let client = LiveHealthKitClient(operations: operations)

        XCTAssertTrue(client.isHealthDataAvailable())
        let state = await client.authorizationState()
        XCTAssertEqual(state, .unsupported)
        await assertError(.unsupported) { try await client.requestReadAccess() }
        await assertError(.unsupported) {
            _ = try await client.readSleepSamples(from: self.start, to: self.end)
        }
    }

    func testExplicitSystemDeniedAndRestrictedErrorsMapToNeedsSettings() async {
        // HKDefines.h: restricted = 2, authorizationDenied = 4, requiredAuthorizationDenied = 10.
        for code in [2, 4, 10] {
            let error = NSError(domain: "com.apple.healthkit", code: code,
                                userInfo: [NSLocalizedDescriptionKey: "explicit system restriction"])
            var operations = stubOperations()
            operations.requestReadAccess = { throw error }
            operations.readSleepSamples = { _, _ in throw error }
            let client = LiveHealthKitClient(operations: operations)
            await assertError(.needsSettings("explicit system restriction")) { try await client.requestReadAccess() }
            await assertError(.needsSettings("explicit system restriction")) {
                _ = try await client.readSleepSamples(from: self.start, to: self.end)
            }
        }
    }

    func testOtherSystemErrorsAndMatchingCodesInOtherDomainsDoNotImplyDenial() async {
        for (domain, code) in [("com.apple.healthkit", 5), ("com.apple.healthkit", 6),
                               ("com.apple.healthkit", 7), ("com.apple.healthkit", 11), ("other", 4)] {
            let error = NSError(domain: domain, code: code, userInfo: [NSLocalizedDescriptionKey: "not denial evidence"])
            var operations = stubOperations()
            operations.requestReadAccess = { throw error }
            operations.readSleepSamples = { _, _ in throw error }
            let client = LiveHealthKitClient(operations: operations)
            await assertError(.requestFailed("not denial evidence")) { try await client.requestReadAccess() }
            await assertError(.queryFailed("not denial evidence")) {
                _ = try await client.readSleepSamples(from: self.start, to: self.end)
            }
        }
    }

    // Only the system I/O is replaced; tests exercise LiveHealthKitClient's real decisions.
    private func stubOperations() -> LiveHealthKitClient.Operations {
        LiveHealthKitClient.Operations(
            isHealthDataAvailable: { true },
            isSleepAnalysisSupported: { true },
            authorizationRequestStatus: {
                XCTFail("Unexpected authorization status lookup")
                return 0
            },
            requestReadAccess: {
                XCTFail("Unexpected authorization request")
                return false
            },
            readSleepSamples: { _, _ in
                XCTFail("Unexpected sleep query")
                return []
            }
        )
    }

    private func record(
        value: Int,
        source: String = "com.example.watch",
        name: String = "Watch"
    ) -> LiveHealthKitClient.RawSleepSample {
        LiveHealthKitClient.RawSleepSample(
            id: UUID(uuidString: "12345678-1234-1234-1234-123456789ABC")!,
            sourceIdentifier: source,
            sourceName: name,
            value: value,
            start: start.addingTimeInterval(-3_600),
            end: end.addingTimeInterval(3_600)
        )
    }

    private func assertError(
        _ expected: HealthKitClientError,
        file: StaticString = #filePath,
        line: UInt = #line,
        operation: () async throws -> Void
    ) async {
        do {
            try await operation()
            XCTFail("Expected \(expected)", file: file, line: line)
        } catch {
            XCTAssertEqual(error as? HealthKitClientError, expected, file: file, line: line)
        }
    }

    private struct TestFailure: LocalizedError {
        let message: String
        init(_ message: String) { self.message = message }
        var errorDescription: String? { message }
    }
}
