import SwiftUI

struct NightInsightsStatus: View {
    let state: TrackingState

    var body: some View {
        if state.isLoadingHistory {
            ProgressView(state.insightsStatusText).tint(CalmNightTheme.accent)
        } else {
            VStack(alignment: .leading, spacing: 8) {
                Text(state.insightsStatusText)
                    .foregroundStyle(state.error == nil ? CalmNightTheme.textSecondary : .orange)
                if let error = state.error {
                    Text(error).foregroundStyle(.orange)
                    Text("Use Retry save on Home to recover your data. These insights exclude any unsaved session.")
                        .foregroundStyle(CalmNightTheme.textSecondary)
                }
            }
            .font(.subheadline)
            .fixedSize(horizontal: false, vertical: true)
        }
    }
}

struct SavedNightInsightCards: View {
    let insights: NightInsights

    var body: some View {
        CalmNightCard {
            VStack(alignment: .leading, spacing: 16) {
                Label("Sleep debt", systemImage: "moon.zzz.fill").font(.headline)
                if let debt = insights.debt {
                    Text(debt.durationText).font(.title.bold().monospacedDigit())
                        .accessibilityLabel("Sleep debt \(debt.deficitMinutes) minutes")
                    Text("\(debt.level) · latest \(debt.nights) recorded \(debt.nights == 1 ? "night" : "nights") · 8-hour target")
                        .font(.subheadline).foregroundStyle(CalmNightTheme.textSecondary)
                    Text("Uses actual recorded minutes. Short simulated sessions can show a large illustrative deficit.")
                        .font(.footnote).foregroundStyle(CalmNightTheme.textSecondary)
                } else {
                    Text("Available after your first saved date.").foregroundStyle(CalmNightTheme.textSecondary)
                }
                Divider().overlay(CalmNightTheme.textTertiary)
                Label("Bedtime consistency", systemImage: "clock.badge.checkmark").font(.headline)
                if let score = insights.consistencyScore {
                    Text("\(score) / 100").font(.title.bold().monospacedDigit())
                        .accessibilityLabel("Bedtime consistency \(score) out of 100")
                    Text("Across all \(insights.recordedNights) retained recorded nights.")
                        .font(.subheadline).foregroundStyle(CalmNightTheme.textSecondary)
                } else {
                    Text("Needs 2 recorded dates to compare bedtimes.")
                        .font(.subheadline).foregroundStyle(CalmNightTheme.textSecondary)
                }
            }
            .fixedSize(horizontal: false, vertical: true)
        }
    }
}
