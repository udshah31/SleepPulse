import Combine

@MainActor
final class HistoryViewModel: ObservableObject {
    @Published private(set) var state: TrackingState
    private var observation: AnyCancellable?
    var nights: [SavedNight] { state.nights }

    init(store: TrackingStore) {
        state = store.state
        observation = store.$state.sink { [weak self] in self?.state = $0 }
    }
}
