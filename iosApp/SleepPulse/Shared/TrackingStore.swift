import Combine
import SleepPulseShared
import SwiftUI
import UIKit

@MainActor
final class TrackingStore: ObservableObject {
    @Published private(set) var state = TrackingState()
    private let customPath: String?
    private var controller: IosTrackingController?
    private var observation: TrackingObservation?
    private var backgroundTask: UIBackgroundTaskIdentifier = .invalid
    private var foreground = true
    private var pendingStart = false
    private var closed = false

    init(databasePath: String? = nil) {
        customPath = databasePath
        open()
    }

    private func open() {
        do {
            let path: String
            if let customPath { path = customPath }
            else {
                let directory = try FileManager.default.url(for: .applicationSupportDirectory,
                    in: .userDomainMask, appropriateFor: nil, create: true)
                    .appendingPathComponent("SleepPulse", isDirectory: true)
                try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
                path = directory.appendingPathComponent("sleeppulse-simulated.db").path
            }
            let controller = IosTrackingController(databasePath: path)
            self.controller = controller
            controller.setForeground(foreground: foreground)
            observation = controller.observe { [weak self] snapshot in
                // Kotlin guarantees main-dispatcher callbacks; no asynchronous hop can reorder them.
                MainActor.assumeIsolated { self?.receive(snapshot) }
            }
        } catch {
            state.phase = .failed
            state.error = "Could not open tracking data. \(error.localizedDescription)"
        }
    }

    private func receive(_ snapshot: IosTrackingSnapshot) {
        guard !closed else { return }
        state = TrackingState(snapshot: snapshot, isForeground: foreground)
        if state.phase == .tracking || state.phase == .failed || state.phase == .idle {
            pendingStart = false
        }
        if state.phase == .idle || state.phase == .failed { endBackgroundTask() }
    }

    func start() {
        guard !closed, state.canStart else { return }
        pendingStart = true
        controller?.start()
    }

    func stop() {
        guard !closed, state.canStop || state.phase == .starting else { return }
        controller?.stop()
    }

    func retry() {
        guard !closed, state.phase == .failed else { return }
        if let controller { controller.retry() }
        else { open() }
    }

    func sceneChanged(_ phase: ScenePhase) {
        guard !closed else { return }
        switch phase {
        case .background:
            foreground = false
            state.isForeground = false
            if pendingStart || state.phase == .tracking || state.phase == .starting || state.phase == .saving {
                beginBackgroundTask()
            }
            controller?.setForeground(foreground: false)
        case .active:
            foreground = true
            state.isForeground = true
            controller?.setForeground(foreground: true)
        case .inactive: break
        @unknown default: break
        }
    }

    private func beginBackgroundTask() {
        guard backgroundTask == .invalid else { return }
        backgroundTask = UIApplication.shared.beginBackgroundTask(withName: "Save simulated session") { [weak self] in
            MainActor.assumeIsolated { self?.endBackgroundTask() }
        }
    }

    private func endBackgroundTask() {
        guard backgroundTask != .invalid else { return }
        UIApplication.shared.endBackgroundTask(backgroundTask)
        backgroundTask = .invalid
    }

    func close() {
        guard !closed else { return }
        closed = true
        observation?.cancel()
        observation = nil
        controller?.close()
        controller = nil
        endBackgroundTask()
    }

    /// Awaitable ownership shutdown for callers that will immediately reopen the same path.
    /// The synchronous `close()` remains available for normal app teardown.
    func closeAndWait() async {
        guard !closed else { return }
        closed = true
        observation?.cancel()
        observation = nil
        let controller = controller
        self.controller = nil
        endBackgroundTask()
        await withCheckedContinuation { continuation in
            controller?.closeAsync {
                continuation.resume()
            } ?? continuation.resume()
        }
    }

    deinit {
        observation?.cancel()
        controller?.close()
    }
}
