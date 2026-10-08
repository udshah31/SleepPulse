import Combine
import Foundation

typealias DashboardState = TrackingState

enum DashboardIntent { case start, stop, retry }

@MainActor
final class DashboardViewModel: ObservableObject {
    @Published private(set) var state: DashboardState
    private var observation: AnyCancellable?
    private var store: TrackingStore?

    init(store: TrackingStore) {
        self.store = store
        state = store.state
        observation = store.$state.sink { [weak self] in self?.state = $0 }
    }

    func onIntent(_ intent: DashboardIntent) {
        switch intent {
        case .start: store?.start()
        case .stop: store?.stop()
        case .retry: store?.retry()
        }
    }

    init(readings: [SleepReading], scoring: SleepScoring) {
        state = DashboardState(
            phase: .idle,
            score: scoring.score(readings: readings),
            latestReading: readings.last,
        )
    }
}
