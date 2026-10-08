import SwiftUI

struct HistoryView: View {
    @StateObject private var viewModel: HistoryViewModel

    init(store: TrackingStore) {
        _viewModel = StateObject(wrappedValue: HistoryViewModel(store: store))
    }

    var body: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 20) {
                Text("History").font(.largeTitle.bold())
                Text("Simulated nights · latest 30 dates")
                    .font(.subheadline).foregroundStyle(CalmNightTheme.textSecondary)
                if viewModel.state.phase == .recovering {
                    ProgressView("Loading saved nights…").tint(CalmNightTheme.accent)
                } else if viewModel.nights.isEmpty {
                    CalmNightCard {
                        Label("No simulated nights yet", systemImage: "moon.zzz.fill").font(.headline)
                        Text("Start tracking on Home, then stop and save to see your first session here.")
                            .font(.subheadline).foregroundStyle(CalmNightTheme.textSecondary).padding(.top, 8)
                    }
                }
                if let error = viewModel.state.error {
                    Text(error).font(.subheadline).foregroundStyle(.orange)
                    Text("Use Retry save on Home to recover your data.")
                        .font(.subheadline).foregroundStyle(CalmNightTheme.textSecondary)
                }
                ForEach(viewModel.nights) { night in
                    CalmNightCard {
                        VStack(alignment: .leading, spacing: 14) {
                            Text(night.dateText).font(.title3.bold())
                            ViewThatFits(in: .horizontal) {
                                HStack { summary(night); Spacer(); duration(night) }
                                VStack(alignment: .leading, spacing: 8) { summary(night); duration(night) }
                            }
                            Text("Deep \(night.deepMinutes) min · REM \(night.remMinutes) min")
                                .font(.subheadline).foregroundStyle(CalmNightTheme.textSecondary)
                            Text("Average heart rate \(night.averageHeartRate) bpm")
                                .font(.subheadline).foregroundStyle(CalmNightTheme.textSecondary)
                            Text("Average HRV \(night.hrvText) ms")
                                .font(.subheadline).foregroundStyle(CalmNightTheme.textSecondary)
                                .accessibilityLabel("Average HRV \(night.hrvAccessibilityText)")
                        }
                    }
                    .accessibilityIdentifier("savedNight-\(night.id)")
                }
            }
            .frame(maxWidth: 720).padding(20).frame(maxWidth: .infinity)
        }
        .background(CalmNightTheme.background.ignoresSafeArea())
        .foregroundStyle(CalmNightTheme.textPrimary)
        .scrollIndicators(.hidden)
    }

    private func summary(_ night: SavedNight) -> some View {
        Label("Score \(night.score)", systemImage: "moon.stars.fill")
            .font(.title3.bold()).foregroundStyle(CalmNightTheme.accent)
    }

    private func duration(_ night: SavedNight) -> some View {
        Text(night.durationText).font(.title3.monospacedDigit())
    }
}
