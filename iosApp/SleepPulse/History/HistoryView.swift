import SwiftUI

struct HistoryView: View {
    @StateObject private var viewModel: HistoryViewModel
    @ObservedObject var healthKitStore: HealthKitSleepStore

    init(store: TrackingStore, healthKitStore: HealthKitSleepStore) {
        _viewModel = StateObject(wrappedValue: HistoryViewModel(store: store))
        self.healthKitStore = healthKitStore
    }

    var body: some View {
        HistoryContent(state: viewModel.state) {
            AppleHealthSleepSection(store: healthKitStore)
        }
    }
}

struct HistoryContent<AdditionalContent: View>: View {
    let state: TrackingState
    private let additionalContent: AdditionalContent

    init(state: TrackingState, @ViewBuilder additionalContent: () -> AdditionalContent) {
        self.state = state
        self.additionalContent = additionalContent()
    }

    var body: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 20) {
                Text("History").font(.largeTitle.bold())
                Text("Simulated nights · latest 30 dates")
                    .font(.subheadline).foregroundStyle(CalmNightTheme.textSecondary)
                NightInsightsStatus(state: state)
                if !state.isLoadingHistory, state.nights.isEmpty, state.error == nil {
                    CalmNightCard {
                        Label("No simulated nights yet", systemImage: "moon.zzz.fill").font(.headline)
                        Text("Start tracking on Home, then stop and save to see your first session here.")
                            .font(.subheadline).foregroundStyle(CalmNightTheme.textSecondary).padding(.top, 8)
                    }
                }
                if !state.isLoadingHistory, !state.nights.isEmpty {
                    if let insights = state.insights { SavedNightInsightCards(insights: insights) }
                    HistoryTrendsView(nights: state.nights)
                    Text("Saved nights").font(.title2.bold())
                    ForEach(state.nights) { night in
                        SavedNightRow(night: night, change: state.insights?.scoreChanges[night.id])
                    }
                }
                additionalContent
            }
            .frame(maxWidth: 720).padding(20).frame(maxWidth: .infinity)
        }
        .background(CalmNightTheme.background.ignoresSafeArea())
        .foregroundStyle(CalmNightTheme.textPrimary)
        .scrollIndicators(.hidden)
    }
}

extension HistoryContent where AdditionalContent == EmptyView {
    init(state: TrackingState) {
        self.init(state: state) { EmptyView() }
    }
}

private struct SavedNightRow: View {
    let night: SavedNight
    let change: NightScoreChange?

    var body: some View {
        CalmNightCard {
            VStack(alignment: .leading, spacing: 14) {
                Text(night.dateText).font(.title3.bold())
                ViewThatFits(in: .horizontal) {
                    HStack { summary; Spacer(); duration }
                    VStack(alignment: .leading, spacing: 8) { summary; duration }
                }
                if let change {
                    Label(change.title, systemImage: change.icon).font(.subheadline)
                        .accessibilityLabel(change.accessibilityText)
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

    private var summary: some View {
        Label("Score \(night.score)", systemImage: "moon.stars.fill")
            .font(.title3.bold()).foregroundStyle(CalmNightTheme.accent)
    }

    private var duration: some View {
        Text(night.durationText).font(.title3.monospacedDigit())
    }
}

#Preview("History full") { HistoryContent(state: DemoNights.state()).preferredColorScheme(.dark) }
#Preview("History partial") { HistoryContent(state: DemoNights.state(count: 3)).preferredColorScheme(.dark) }
#Preview("History empty") { HistoryContent(state: DemoNights.state(count: 0)).preferredColorScheme(.dark) }
#Preview("History missing HRV") { HistoryContent(state: DemoNights.state(missingHrv: true)).preferredColorScheme(.dark) }
#Preview("History large text") {
    HistoryContent(state: DemoNights.state()).environment(\.dynamicTypeSize, .accessibility5).preferredColorScheme(.dark)
}
