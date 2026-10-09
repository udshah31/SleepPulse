import Foundation

/// Whether an authorization request is needed, never whether sleep read access was granted.
/// HealthKit intentionally does not reveal read denial; `.unnecessary` is not read approval.
enum HealthKitAuthorizationState: Equatable {
    case unavailable
    case unsupported
    case unknown
    case shouldRequest
    case unnecessary
    case requestFailed(String)
}

enum HealthKitClientError: Error, Equatable, LocalizedError {
    case unavailable
    case unsupported
    case requestFailed(String)
    case queryFailed(String)
    /// Only explicit system authorization-denied/restricted errors establish this outcome.
    case needsSettings(String)

    var errorDescription: String? {
        switch self {
        case .unavailable: return "Apple Health is unavailable on this device."
        case .unsupported: return "Apple Health sleep analysis is unsupported on this device."
        case .requestFailed(let message): return message
        case .queryFailed(let message): return message
        case .needsSettings(let message): return message
        }
    }
}

protocol HealthKitClient {
    func isHealthDataAvailable() -> Bool
    func authorizationState() async -> HealthKitAuthorizationState
    /// Completes the permission request flow; success does not establish read authorization.
    func requestReadAccess() async throws
    /// Returns native samples overlapping the supplied window, including crossing boundaries.
    /// The store owns the 30-calendar-day window and normalization. Empty data is not denial.
    func readSleepSamples(from: Date, to: Date) async throws -> [HealthKitSleepSample]
}
