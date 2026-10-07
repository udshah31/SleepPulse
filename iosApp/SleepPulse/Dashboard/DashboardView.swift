import SwiftUI

struct DashboardView: View {
    @StateObject private var viewModel: DashboardViewModel

    init(readings: [SleepReading] = DemoReadings.night, scoring: SleepScoring = SharedSleepScoring()) {
        _viewModel = StateObject(wrappedValue: DashboardViewModel(readings: readings, scoring: scoring))
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                header
                scoreCard
                latestReadingCard
                CalmNightCard {
                    Label("Shared core", systemImage: "checkmark.seal.fill")
                        .font(.headline)
                        .foregroundStyle(CalmNightTheme.recovery)
                    Text("This demo score is calculated by the SleepPulse Kotlin Multiplatform core.")
                        .font(.subheadline)
                        .foregroundStyle(CalmNightTheme.textSecondary)
                        .fixedSize(horizontal: false, vertical: true)
                        .padding(.top, 8)
                }
            }
            .frame(maxWidth: 720)
            .padding(.horizontal, 20)
            .padding(.vertical, 24)
            .frame(maxWidth: .infinity)
        }
        .scrollIndicators(.hidden)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(
            LinearGradient(
                colors: [CalmNightTheme.background, CalmNightTheme.backgroundEnd],
                startPoint: .top,
                endPoint: .bottom,
            )
            .ignoresSafeArea()
        )
        .preferredColorScheme(.dark)
    }

    private var header: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("SleepPulse")
                .font(.largeTitle.weight(.bold))
                .foregroundStyle(CalmNightTheme.textPrimary)
            HStack(spacing: 8) {
                Circle()
                    .fill(CalmNightTheme.recovery)
                    .frame(width: 8, height: 8)
                    .accessibilityHidden(true)
                Text("Demo data")
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(CalmNightTheme.textSecondary)
            }
        }
    }

    private var scoreCard: some View {
        CalmNightCard {
            VStack(alignment: .leading, spacing: 16) {
                Text("Sleep score")
                    .font(.headline)
                    .foregroundStyle(CalmNightTheme.textSecondary)

                ZStack {
                    Circle()
                        .stroke(CalmNightTheme.accent.opacity(0.18), lineWidth: 18)
                    Circle()
                        .trim(from: 0, to: CGFloat(viewModel.state.score) / 100)
                        .stroke(
                            CalmNightTheme.accent,
                            style: StrokeStyle(lineWidth: 18, lineCap: .round),
                        )
                        .rotationEffect(.degrees(-90))
                    VStack(spacing: 2) {
                        Text("\(viewModel.state.score)")
                            .font(.system(size: 56, weight: .bold, design: .rounded))
                            .foregroundStyle(CalmNightTheme.textPrimary)
                        Text("out of 100")
                            .font(.subheadline)
                            .foregroundStyle(CalmNightTheme.textSecondary)
                    }
                }
                .frame(width: 210, height: 210)
                .frame(maxWidth: .infinity)
                .accessibilityElement(children: .ignore)
                .accessibilityLabel("Sleep score \(viewModel.state.score) out of 100")
            }
        }
    }

    private var latestReadingCard: some View {
        CalmNightCard {
            VStack(alignment: .leading, spacing: 16) {
                Text("Latest demo reading")
                    .font(.headline)
                    .foregroundStyle(CalmNightTheme.textSecondary)

                if let reading = viewModel.state.latestReading {
                    LazyVGrid(columns: [GridItem(.adaptive(minimum: 120), spacing: 12)], spacing: 12) {
                        MetricCard(title: "Heart rate", value: "\(reading.heartRateBpm)", unit: "bpm", icon: "heart.fill")
                        MetricCard(title: "HRV", value: reading.hrvMillis.map { "\(Int($0))" } ?? "—", unit: "ms", icon: "waveform.path.ecg")
                        MetricCard(title: "Stage", value: reading.stage.displayName, unit: "", icon: "moon.stars.fill")
                    }
                } else {
                    Text("No readings yet")
                        .foregroundStyle(CalmNightTheme.textTertiary)
                }
            }
        }
    }
}

private struct MetricCard: View {
    let title: String
    let value: String
    let unit: String
    let icon: String

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Label(title, systemImage: icon)
                .font(.caption.weight(.semibold))
                .foregroundStyle(CalmNightTheme.textSecondary)
            HStack(alignment: .firstTextBaseline, spacing: 4) {
                Text(value)
                    .font(.title2.weight(.bold).monospacedDigit())
                    .foregroundStyle(CalmNightTheme.textPrimary)
                if !unit.isEmpty {
                    Text(unit)
                        .font(.caption)
                        .foregroundStyle(CalmNightTheme.textSecondary)
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(unit.isEmpty ? "\(title), \(value)" : "\(title), \(value) \(unit)")
    }
}
