import Foundation
import HealthKit

final class LiveHealthKitClient: HealthKitClient {
    /// Raw system values stay inside the adapter; no HealthKit types cross the client contract.
    struct RawSleepSample {
        let id: UUID
        let sourceIdentifier: String
        let sourceName: String
        let value: Int
        let start: Date
        let end: Date
    }

    /// The system I/O boundary lets client decisions be tested without real permissions.
    struct Operations {
        var isHealthDataAvailable: () -> Bool
        var isSleepAnalysisSupported: () -> Bool
        var authorizationRequestStatus: () async throws -> Int
        var requestReadAccess: () async throws -> Bool
        var readSleepSamples: (Date, Date) async throws -> [RawSleepSample]
    }

    private let operations: Operations

    init(operations: Operations = .live()) {
        self.operations = operations
    }

    func isHealthDataAvailable() -> Bool { operations.isHealthDataAvailable() }

    func authorizationState() async -> HealthKitAuthorizationState {
        guard isHealthDataAvailable() else { return .unavailable }
        guard operations.isSleepAnalysisSupported() else { return .unsupported }
        do {
            let rawValue = try await operations.authorizationRequestStatus()
            switch HKAuthorizationRequestStatus(rawValue: rawValue) {
            case .shouldRequest: return .shouldRequest
            case .unnecessary: return .unnecessary
            default: return .unknown
            }
        } catch {
            return .requestFailed(error.localizedDescription)
        }
    }

    func requestReadAccess() async throws {
        try requireSleepAnalysis()
        let success: Bool
        do {
            success = try await operations.requestReadAccess()
        } catch {
            if Self.requiresSettings(error) { throw HealthKitClientError.needsSettings(error.localizedDescription) }
            throw HealthKitClientError.requestFailed(error.localizedDescription)
        }
        guard success else {
            throw HealthKitClientError.requestFailed("Apple Health authorization request did not complete.")
        }
    }

    func readSleepSamples(from: Date, to: Date) async throws -> [HealthKitSleepSample] {
        try requireSleepAnalysis()
        do {
            return try await operations.readSleepSamples(from, to).map { sample in
                HealthKitSleepSample(
                    id: sample.id.uuidString,
                    sourceIdentifier: sample.sourceIdentifier,
                    sourceName: sample.sourceName,
                    stage: Self.stage(for: sample.value),
                    start: sample.start,
                    end: sample.end
                )
            }
        } catch {
            if Self.requiresSettings(error) { throw HealthKitClientError.needsSettings(error.localizedDescription) }
            throw HealthKitClientError.queryFailed(error.localizedDescription)
        }
    }

    private static func requiresSettings(_ error: Error) -> Bool {
        let error = error as NSError
        guard error.domain == HKErrorDomain else { return false }
        return [HKError.Code.errorAuthorizationDenied, .errorHealthDataRestricted, .errorRequiredAuthorizationDenied]
            .contains { $0.rawValue == error.code }
    }

    private func requireSleepAnalysis() throws {
        guard isHealthDataAvailable() else { throw HealthKitClientError.unavailable }
        guard operations.isSleepAnalysisSupported() else { throw HealthKitClientError.unsupported }
    }

    private static func stage(for rawValue: Int) -> HealthKitSleepStage {
        switch HKCategoryValueSleepAnalysis(rawValue: rawValue) {
        case .inBed: return .inBed
        case .asleepUnspecified: return .asleepUnspecified
        case .awake: return .awake
        case .asleepCore: return .core
        case .asleepDeep: return .deep
        case .asleepREM: return .rem
        default: return .unknown
        }
    }
}

extension LiveHealthKitClient.Operations {
    static func live() -> Self {
        let store = HKHealthStore()
        let sleepType = HKObjectType.categoryType(forIdentifier: .sleepAnalysis)
        return Self(
            isHealthDataAvailable: { HKHealthStore.isHealthDataAvailable() },
            isSleepAnalysisSupported: { sleepType != nil },
            authorizationRequestStatus: {
                guard let sleepType else { throw HealthKitClientError.unsupported }
                // authorizationStatus(for:) reports *write* access and cannot answer this question.
                return try await withCheckedThrowingContinuation { continuation in
                    store.getRequestStatusForAuthorization(toShare: [], read: [sleepType]) { status, error in
                        if let error {
                            continuation.resume(throwing: error)
                        } else {
                            continuation.resume(returning: status.rawValue)
                        }
                    }
                }
            },
            requestReadAccess: {
                guard let sleepType else { throw HealthKitClientError.unsupported }
                return try await withCheckedThrowingContinuation { continuation in
                    store.requestAuthorization(toShare: [], read: [sleepType]) { success, error in
                        if let error {
                            continuation.resume(throwing: error)
                        } else {
                            continuation.resume(returning: success)
                        }
                    }
                }
            },
            readSleepSamples: { from, to in
                guard let sleepType else { throw HealthKitClientError.unsupported }
                // No strictStartDate/strictEndDate: retain samples crossing either window boundary.
                let predicate = HKQuery.predicateForSamples(withStart: from, end: to, options: [])
                return try await withCheckedThrowingContinuation { continuation in
                    let query = HKSampleQuery(
                        sampleType: sleepType,
                        predicate: predicate,
                        limit: HKObjectQueryNoLimit,
                        sortDescriptors: [NSSortDescriptor(key: HKSampleSortIdentifierStartDate, ascending: true)]
                    ) { _, samples, error in
                        if let error {
                            continuation.resume(throwing: error)
                            return
                        }
                        guard let categories = (samples ?? []) as? [HKCategorySample] else {
                            continuation.resume(throwing: HealthKitClientError.queryFailed(
                                "Apple Health returned unexpected sleep sample types."
                            ))
                            return
                        }
                        continuation.resume(returning: categories.map { sample in
                            LiveHealthKitClient.RawSleepSample(
                                id: sample.uuid,
                                sourceIdentifier: sample.sourceRevision.source.bundleIdentifier,
                                sourceName: sample.sourceRevision.source.name,
                                value: sample.value,
                                start: sample.startDate,
                                end: sample.endDate
                            )
                        })
                    }
                    store.execute(query)
                }
            }
        )
    }
}
