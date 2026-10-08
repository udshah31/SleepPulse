import Combine

@MainActor
final class RecoveryViewModel: ObservableObject {
    @Published private(set) var state: TrackingState
    private var observation: AnyCancellable?

    init(store: TrackingStore) {
        state = store.state
        observation = store.$state.sink { [weak self] in self?.state = $0 }
    }
}
