import SwiftUI

struct RecoveryView: View {
    @StateObject private var viewModel: RecoveryViewModel

    init(store: TrackingStore) {
        _viewModel = StateObject(wrappedValue: RecoveryViewModel(store: store))
    }

    var body: some View { RecoveryContent(state: viewModel.state) }
}

/// State-only presentation also used by explicit previews and tests.
struct RecoveryContent: View {
    let state: TrackingState

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                Text("Recovery").font(.largeTitle.bold())
                Text("Simulated insights").font(.subheadline).foregroundStyle(CalmNightTheme.textSecondary)
                NightInsightsStatus(state: state)
                if !state.isLoadingHistory, let insights = state.insights,
                   state.error == nil || !state.nights.isEmpty {
                    if let recovery = insights.recovery {
                        RecoveryScoreCard(recovery: recovery, baselineNights: insights.baselineNights)
                    } else {
                        baselineCard(insights)
                    }
                    readinessCard(insights)
                    trendCards(insights)
                    if !state.nights.isEmpty { SavedNightInsightCards(insights: insights) }
                }
            }
            .frame(maxWidth: 720).padding(20).frame(maxWidth: .infinity)
        }
        .scrollIndicators(.hidden)
        .background(CalmNightTheme.background.ignoresSafeArea())
        .foregroundStyle(CalmNightTheme.textPrimary)
    }

    private func baselineCard(_ insights: NightInsights) -> some View {
        CalmNightCard {
            VStack(alignment: .leading, spacing: 14) {
                Label("Building your baseline", systemImage: "heart.text.square").font(.title3.bold())
                ProgressView(value: Double(min(insights.recordedNights, 4)), total: 4)
                    .tint(CalmNightTheme.recovery)
                    .accessibilityLabel("Recovery baseline")
                    .accessibilityValue(insights.baselineProgressText)
                Text(insights.baselineProgressText).font(.headline)
                Text("Save sessions on 4 different dates. Recovery compares the latest night with at least 3 preceding nights. Another session on the same date does not advance the baseline.")
                    .font(.subheadline).foregroundStyle(CalmNightTheme.textSecondary)
                if insights.recordedNights == 0 {
                    Text("Start tracking on Home, then stop and save your first session.")
                        .font(.subheadline).foregroundStyle(CalmNightTheme.textSecondary)
                }
            }
            .fixedSize(horizontal: false, vertical: true)
        }
    }

    private func readinessCard(_ insights: NightInsights) -> some View {
        CalmNightCard {
            VStack(alignment: .leading, spacing: 12) {
                Label("Readiness", systemImage: "figure.walk").font(.headline)
                if let readiness = insights.readiness {
                    Text("\(readiness.score) / 100").font(.largeTitle.bold().monospacedDigit())
                        .accessibilityLabel("Readiness \(readiness.score) out of 100, \(readiness.tier)")
                    Text(readiness.tier).font(.headline).foregroundStyle(CalmNightTheme.recovery)
                } else {
                    Text("Unavailable until your recovery baseline is ready.")
                        .font(.subheadline).foregroundStyle(CalmNightTheme.textSecondary)
                }
                Text("For the latest recorded date. Blends recovery, available trends and sleep debt using simulated data.")
                    .font(.subheadline).foregroundStyle(CalmNightTheme.textSecondary)
            }
            .fixedSize(horizontal: false, vertical: true)
        }
    }

    @ViewBuilder private func trendCards(_ insights: NightInsights) -> some View {
        Text("Recorded-night trends").font(.title2.bold())
        Text("Latest 7 recorded nights compared with the preceding 7. These dates need not be consecutive.")
            .font(.subheadline).foregroundStyle(CalmNightTheme.textSecondary)
        RecordedMetricTrendCard(title: "HRV", icon: "waveform.path.ecg", unit: "ms", trend: insights.hrvTrend,
            unavailable: insights.hrvUnavailableText,
            coverage: "Known HRV: \(insights.recentKnownHrvNights) of 7 recent · \(insights.priorKnownHrvNights) of 7 preceding", risingIsFavorable: true)
        RecordedMetricTrendCard(title: "Heart rate", icon: "heart.fill", unit: "bpm", trend: insights.heartRateTrend,
            unavailable: insights.trendProgressText, coverage: nil, risingIsFavorable: false)
    }
}

private struct RecoveryScoreCard: View {
    let recovery: RecoveryInsight
    let baselineNights: Int
    @Environment(\.dynamicTypeSize) private var textSize

    var body: some View {
        CalmNightCard {
            VStack(alignment: .leading, spacing: 16) {
                Label("Recovery score", systemImage: "heart.text.square.fill").font(.headline)
                Group {
                    if textSize.isAccessibilitySize {
                        scoreText
                    } else {
                        ZStack {
                            Circle().stroke(CalmNightTheme.recovery.opacity(0.18), lineWidth: 16)
                            Circle().trim(from: 0, to: CGFloat(recovery.score) / 100)
                                .stroke(CalmNightTheme.recovery, style: StrokeStyle(lineWidth: 16, lineCap: .round))
                                .rotationEffect(.degrees(-90))
                            scoreText
                        }
                        .frame(width: 200, height: 200).frame(maxWidth: .infinity)
                    }
                }
                .accessibilityElement(children: .ignore)
                .accessibilityLabel("Recovery score \(recovery.score) out of 100, \(recovery.tier)")
                Text(recovery.tier).font(.title3.bold()).foregroundStyle(CalmNightTheme.recovery)
                Text(recovery.guidance).font(.headline)
                Text("Compared with \(baselineNights) preceding recorded nights, excluding the latest night.")
                    .font(.subheadline).foregroundStyle(CalmNightTheme.textSecondary)
                comparison("Heart rate", recovery.heartRateComparison)
                comparison("HRV", recovery.hrvComparison)
                if recovery.hrvDeviation == nil {
                    Text("Recovery uses heart rate only. An HRV comparison needs a known latest value and at least 3 known baseline values.")
                        .font(.subheadline).foregroundStyle(CalmNightTheme.textSecondary)
                }
            }
            .fixedSize(horizontal: false, vertical: true)
        }
    }

    private var scoreText: some View {
        VStack(alignment: textSize.isAccessibilitySize ? .leading : .center, spacing: 4) {
            Text("\(recovery.score)").font(.largeTitle.bold().monospacedDigit())
            Text("out of 100").font(.subheadline).foregroundStyle(CalmNightTheme.textSecondary)
        }
    }

    private func comparison(_ title: String, _ value: String) -> some View {
        ViewThatFits(in: .horizontal) {
            HStack { Text(title); Spacer(); Text(value) }
            VStack(alignment: .leading, spacing: 4) { Text(title); Text(value) }
        }
        .font(.subheadline)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("\(title), \(value)")
    }
}

private struct RecordedMetricTrendCard: View {
    let title: String
    let icon: String
    let unit: String
    let trend: MetricTrend?
    let unavailable: String
    let coverage: String?
    let risingIsFavorable: Bool

    var body: some View {
        CalmNightCard {
            VStack(alignment: .leading, spacing: 12) {
                Label(title, systemImage: icon).font(.headline)
                if let trend {
                    Label(trend.direction.title, systemImage: trend.direction.icon)
                        .font(.title3.bold()).foregroundStyle(directionColor(trend.direction))
                    Text("Recent average \(String(format: "%.1f", trend.recentAverage)) \(unit)")
                        .font(.title3.monospacedDigit())
                    Text("Preceding average \(String(format: "%.1f", trend.priorAverage)) \(unit)")
                        .font(.subheadline).foregroundStyle(CalmNightTheme.textSecondary)
                    if let coverage {
                        Text(coverage).font(.footnote).foregroundStyle(CalmNightTheme.textSecondary)
                    }
                } else {
                    Text("Unavailable").font(.headline)
                    Text(unavailable).font(.subheadline).foregroundStyle(CalmNightTheme.textSecondary)
                }
            }
            .fixedSize(horizontal: false, vertical: true)
        }
    }

    private func directionColor(_ direction: MetricTrendDirection) -> Color {
        if direction == .stable { return CalmNightTheme.accent }
        return (direction == .rising) == risingIsFavorable ? CalmNightTheme.recovery : .orange
    }
}

#Preview("Recovery full") { RecoveryContent(state: DemoNights.state()).preferredColorScheme(.dark) }
#Preview("Recovery partial") { RecoveryContent(state: DemoNights.state(count: 3)).preferredColorScheme(.dark) }
#Preview("Recovery empty") { RecoveryContent(state: DemoNights.state(count: 0)).preferredColorScheme(.dark) }
#Preview("Recovery missing HRV") { RecoveryContent(state: DemoNights.state(missingHrv: true)).preferredColorScheme(.dark) }
#Preview("Recovery large text") {
    RecoveryContent(state: DemoNights.state()).environment(\.dynamicTypeSize, .accessibility5).preferredColorScheme(.dark)
}
