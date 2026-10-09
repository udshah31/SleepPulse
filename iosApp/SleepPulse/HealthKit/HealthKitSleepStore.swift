import Combine
import Foundation
import UIKit

@MainActor
final class HealthKitSleepStore: ObservableObject {
    @Published private(set) var state: HealthKitSleepState
    var isRefreshing: Bool { state.phase == .loading }

    private let client: HealthKitClient
    private let cache: HealthKitCacheStorage
    private let now: () -> Date
    private let calendar: Calendar
    private var lastLoaded: HealthKitSleepCache?
    private var operation: Task<Void, Never>?

    init(client: HealthKitClient, cache: HealthKitCacheStorage,
         now: @escaping () -> Date = Date.init, calendar: Calendar = .current) {
        self.client = client
        self.cache = cache
        self.now = now
        self.calendar = calendar
        state = HealthKitSleepState(phase: .notConnected)
        do {
            lastLoaded = try cache.load()
            if let lastLoaded {
                state = snapshot(phase: lastLoaded.episodes.isEmpty ? .empty : .loaded)
            }
        } catch {
            // Ignore only this native cache. A manual successful refresh can replace it.
            state = HealthKitSleepState(phase: .notConnected, error: error.localizedDescription)
        }
        if !client.isHealthDataAvailable() { presentFailure(HealthKitClientError.unavailable) }
        // Launch never requests access or queries sleep samples. Both actions are explicit.
    }

    static func live() -> HealthKitSleepStore {
        HealthKitSleepStore(client: LiveHealthKitClient(), cache: HealthKitCacheStore())
    }

    /// The sole permission-request entry point. Repeated actions join the current operation.
    @discardableResult
    func connect() -> Task<Void, Never> { start(requestAccess: true) }

    /// Manual refresh only; never requests permissions, even without a previous connection.
    @discardableResult
    func refresh() -> Task<Void, Never> { start(requestAccess: false) }

    private func start(requestAccess: Bool) -> Task<Void, Never> {
        if let operation { return operation }
        let task = Task {
            defer { operation = nil }
            do {
                guard client.isHealthDataAvailable() else { throw HealthKitClientError.unavailable }
                if requestAccess { try await client.requestReadAccess() }
                let end = now()
                guard Self.isNormalizable(end),
                      let start = calendar.date(byAdding: .day, value: -30, to: end) else {
                    throw ImportError.invalidWindow
                }
                let samples = try await client.readSleepSamples(from: start, to: end)
                // The normalizer converts timestamps to integer IDs/durations. Reject invalid
                // dates before those conversions rather than trapping or publishing partial data.
                guard samples.allSatisfy({ Self.isNormalizable($0.start) && Self.isNormalizable($0.end) }) else {
                    throw ImportError.invalidSamples
                }
                let episodes = HealthKitSleepNormalizer.episodes(from: samples)
                let loaded = HealthKitSleepCache(formatVersion: HealthKitSleepCache.currentFormatVersion,
                    episodes: episodes, fetchedAt: now(), windowStart: start, windowEnd: end)
                try cache.replace(with: loaded)
                lastLoaded = loaded
                state = snapshot(phase: episodes.isEmpty ? .empty : .loaded)
            } catch {
                presentFailure(error)
            }
        }
        // Install the gate before publishing loading (including synchronous observers).
        operation = task
        state = snapshot(phase: .loading)
        return task
    }

    private func presentFailure(_ error: Error) {
        let phase: HealthKitSleepPhase
        if lastLoaded != nil { phase = .stale } // An empty successful cache is still valid.
        else if let clientError = error as? HealthKitClientError,
                clientError == .unavailable || clientError == .unsupported { phase = .unavailable }
        else { phase = .failed }
        let settingsURL: URL?
        if case .needsSettings = error as? HealthKitClientError {
            settingsURL = URL(string: UIApplication.openSettingsURLString)
        } else {
            settingsURL = nil
        }
        state = snapshot(phase: phase, error: error.localizedDescription, openSettingsURL: settingsURL)
    }

    private func snapshot(phase: HealthKitSleepPhase, error: String? = nil, openSettingsURL: URL? = nil) -> HealthKitSleepState {
        HealthKitSleepState(phase: phase, episodes: lastLoaded?.episodes ?? [], fetchedAt: lastLoaded?.fetchedAt,
            windowStart: lastLoaded?.windowStart, windowEnd: lastLoaded?.windowEnd,
            error: error, openSettingsURL: openSettingsURL)
    }

    private static func isNormalizable(_ date: Date) -> Bool {
        let milliseconds = (date.timeIntervalSince1970 * 1_000).rounded()
        return milliseconds.isFinite && milliseconds >= Double(Int64.min) && milliseconds < Double(Int64.max)
    }

    private enum ImportError: LocalizedError {
        case invalidWindow, invalidSamples
        var errorDescription: String? {
            switch self {
            case .invalidWindow: return "Could not determine the Apple Health date window."
            case .invalidSamples: return "Apple Health returned invalid sleep dates."
            }
        }
    }
}
