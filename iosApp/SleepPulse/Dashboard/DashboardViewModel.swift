import Combine
import Foundation

struct DashboardState: Equatable {
    let score: Int
    let latestReading: SleepReading?
}

@MainActor
final class DashboardViewModel: ObservableObject {
    @Published private(set) var state: DashboardState

    init(readings: [SleepReading], scoring: SleepScoring) {
        state = DashboardState(
            score: scoring.score(readings: readings),
            latestReading: readings.last,
        )
    }
}
