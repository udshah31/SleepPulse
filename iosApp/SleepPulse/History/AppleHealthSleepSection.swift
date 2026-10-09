import SwiftUI

/// Observes the app-owned store. Only explicit button actions perform HealthKit work.
struct AppleHealthSleepSection: View {
    @ObservedObject var store: HealthKitSleepStore
    @Environment(\.openURL) private var openURL

    var body: some View {
        AppleHealthSleepContent(state: store.state,
            onConnect: { store.connect() },
            onRefresh: { store.refresh() },
            onSettings: {
                if let url = store.state.openSettingsURL { openURL(url) }
            })
    }
}

/// State-only content for History, previews, and presentation tests.
struct AppleHealthSleepContent: View {
    let state: HealthKitSleepState
    let onConnect: () -> Void
    let onRefresh: () -> Void
    let onSettings: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 20) {
            Text("From Apple Health").font(.title2.bold())
                .accessibilityAddTraits(.isHeader)
                .accessibilityIdentifier("appleHealthHeading")
            Text("Imported sleep · separate from simulated nights")
                .font(.subheadline).foregroundStyle(CalmNightTheme.textSecondary)
            CalmNightCard {
                VStack(alignment: .leading, spacing: 12) {
                    Text(statusTitle).font(.headline)
                        .accessibilityIdentifier("appleHealthStatus")
                    Text(statusMessage).font(.subheadline)
                        .foregroundStyle(CalmNightTheme.textSecondary)
                    if state.phase == .loading {
                        ProgressView().tint(CalmNightTheme.accent).accessibilityHidden(true)
                    }
                    if let error = state.error {
                        Text(error).font(.subheadline)
                            .accessibilityIdentifier("appleHealthError")
                    }
                    if let fetchedAt = state.fetchedAt {
                        Text("Last fetched: \(AppleHealthDateText.text(fetchedAt))")
                            .font(.caption).foregroundStyle(CalmNightTheme.textSecondary)
                            .accessibilityIdentifier("appleHealthFetchedAt")
                    }
                    if let start = state.windowStart, let end = state.windowEnd {
                        Text("Import window: \(AppleHealthDateText.text(start)) – \(AppleHealthDateText.text(end))")
                            .font(.caption).foregroundStyle(CalmNightTheme.textSecondary)
                            .accessibilityIdentifier("appleHealthWindow")
                    }
                    if state.phase == .stale, state.episodes.isEmpty {
                        Text("No sleep data in the last loaded result.")
                            .font(.subheadline).foregroundStyle(CalmNightTheme.textSecondary)
                    }
                    actions
                }
            }
            ForEach(orderedEpisodes) { episode in
                AppleHealthSleepRow(episode: episode)
            }
        }
        .fixedSize(horizontal: false, vertical: true)
        .foregroundStyle(CalmNightTheme.textPrimary)
        .tint(CalmNightTheme.accent)
    }

    private var orderedEpisodes: [HealthKitSleepEpisode] {
        // Match normalization order, including a deterministic tie-break for different sources.
        state.episodes.sorted {
            if $0.start != $1.start { return $0.start > $1.start }
            if $0.end != $1.end { return $0.end > $1.end }
            return $0.id < $1.id
        }
    }

    @ViewBuilder private var actions: some View {
        if state.phase != .unavailable {
            if state.phase == .notConnected || state.phase == .failed ||
                (state.phase == .loading && state.fetchedAt == nil) {
                Button(action: onConnect) {
                    Text("Connect Apple Health").frame(maxWidth: .infinity, minHeight: 32)
                }
                .buttonStyle(.borderedProminent)
                .accessibilityIdentifier("connectAppleHealth")
                .disabled(state.phase == .loading)
            } else {
                Button(action: onRefresh) {
                    Text("Refresh Apple Health").frame(maxWidth: .infinity, minHeight: 32)
                }
                .buttonStyle(.bordered)
                .accessibilityIdentifier("refreshAppleHealth")
                .disabled(state.phase == .loading)
            }
        }
        if state.openSettingsURL != nil {
            Button(action: onSettings) {
                Text("Open Settings").frame(maxWidth: .infinity, minHeight: 32)
            }
            .buttonStyle(.bordered)
            .accessibilityIdentifier("openAppleHealthSettings")
        }
    }

    private var statusTitle: String {
        switch state.phase {
        case .unavailable: return "Apple Health is unavailable"
        case .notConnected: return "Connect your sleep history"
        case .loading: return "Loading Apple Health sleep…"
        case .loaded: return "Sleep from Apple Health"
        case .empty: return "No Apple Health sleep records found"
        case .stale: return "Last loaded Apple Health sleep"
        case .failed: return "Could not load Apple Health sleep"
        }
    }

    private var statusMessage: String {
        switch state.phase {
        case .unavailable: return "Sleep import is not available on this device."
        case .notConnected: return "Connect to request access to sleep records from the last 30 days."
        case .loading:
            return state.fetchedAt == nil ? "Your sleep import is in progress." : "Showing last loaded sleep while refreshing."
        case .loaded: return "Refresh to check for new sleep records from the last 30 days."
        case .empty: return "Apple Health returned no sleep records for this window. You can review sleep data and sharing in the Health app, then refresh."
        case .stale: return "The latest import could not finish. Try refreshing again."
        case .failed: return "Try connecting again to request access and retry the import."
        }
    }
}

private struct AppleHealthSleepRow: View {
    let episode: HealthKitSleepEpisode

    private var metrics: [SleepMetric] {
        [SleepMetric(title: "Asleep", minutes: episode.asleepMinutes),
         SleepMetric(title: "In bed", minutes: episode.inBedMinutes),
         SleepMetric(title: "Awake", minutes: episode.awakeMinutes),
         SleepMetric(title: "Core", minutes: episode.coreMinutes),
         SleepMetric(title: "Deep", minutes: episode.deepMinutes),
         SleepMetric(title: "REM", minutes: episode.remMinutes)]
    }

    private var source: String {
        episode.sourceName.isEmpty ? episode.sourceIdentifier : episode.sourceName
    }

    var body: some View {
        CalmNightCard {
            VStack(alignment: .leading, spacing: 12) {
                Text(AppleHealthDateText.text(episode.start)).font(.title3.bold())
                Text("Source: \(source)").font(.subheadline)
                    .foregroundStyle(CalmNightTheme.textSecondary)
                ForEach(metrics, id: \.title) { metric in
                    ViewThatFits(in: .horizontal) {
                        HStack {
                            Text(metric.title)
                            Spacer(minLength: 16)
                            Text(metric.text).monospacedDigit()
                        }
                        VStack(alignment: .leading, spacing: 4) {
                            Text(metric.title)
                            Text(metric.text).monospacedDigit()
                        }
                    }
                    .font(.subheadline)
                }
            }
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Apple Health sleep. Source: \(source). \(AppleHealthDateText.text(episode.start)). " +
            metrics.map { "\($0.title) \($0.spokenText)" }.joined(separator: ". "))
        .accessibilityIdentifier("appleHealthEpisode-\(episode.id)")
    }
}

private struct SleepMetric {
    let title: String
    let minutes: Int?

    var text: String {
        guard let minutes else { return "Unavailable" }
        return minutes < 1 ? "<1 min" : "\(minutes) min"
    }

    var spokenText: String {
        guard let minutes else { return "Unavailable" }
        if minutes < 1 { return "less than 1 minute" }
        return minutes == 1 ? "1 minute" : "\(minutes) minutes"
    }
}

private enum AppleHealthDateText {
    static func text(_ date: Date) -> String {
        // A date-only summary uses UTC components before entering the shared date formatter.
        // Formatting an instant in the device zone first would shift the date when travelling.
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(secondsFromGMT: 0)!
        let components = calendar.dateComponents([.year, .month, .day], from: date)
        guard let year = components.year, let month = components.month, let day = components.day else {
            return "Unavailable"
        }
        return NightDateFormatting.text(String(format: "%04d-%02d-%02d", year, month, day))
    }
}

private struct AppleHealthPreview: View {
    let phase: HealthKitSleepPhase

    var body: some View {
        HistoryContent(state: DemoNights.state(count: 0)) {
            AppleHealthSleepContent(state: state, onConnect: {}, onRefresh: {}, onSettings: {})
        }
        .preferredColorScheme(.dark)
    }

    private var state: HealthKitSleepState {
        let start = Date(timeIntervalSince1970: 1_791_410_400) // Oct 7, 2026, 22:00 UTC
        let end = start.addingTimeInterval(8 * 3_600)
        let episode = HealthKitSleepEpisode(id: "preview-watch", sourceIdentifier: "preview.watch", sourceName: "Apple Watch",
            start: start, end: end, inBedMinutes: 480, asleepMinutes: 420,
            awakeMinutes: nil, coreMinutes: 300, deepMinutes: 60, remMinutes: 60)
        let hasSnapshot = phase != .unavailable
        return HealthKitSleepState(phase: phase, episodes: [.loaded, .stale].contains(phase) ? [episode] : [],
            fetchedAt: hasSnapshot ? end : nil, windowStart: hasSnapshot ? end.addingTimeInterval(-30 * 86_400) : nil,
            windowEnd: hasSnapshot ? end : nil, error: phase == .stale ? "Could not refresh sleep records." : nil)
    }
}

#Preview("Apple Health unavailable") { AppleHealthPreview(phase: .unavailable) }
#Preview("Apple Health empty") { AppleHealthPreview(phase: .empty) }
#Preview("Apple Health loaded") { AppleHealthPreview(phase: .loaded) }
#Preview("Apple Health stale") { AppleHealthPreview(phase: .stale) }
#Preview("Apple Health large text") {
    AppleHealthPreview(phase: .loaded).environment(\.dynamicTypeSize, .accessibility5)
}
