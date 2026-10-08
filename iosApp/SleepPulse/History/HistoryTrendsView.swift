import SwiftUI
import Charts

struct HistoryTrendsView: View {
    let nights: [SavedNight]
    @Environment(\.dynamicTypeSize) private var textSize
    private var points: [HistoryChartPoint] { HistoryChartPoint.make(nights: nights) }
    private var dateRange: String {
        guard let first = points.first, let last = points.last else { return "No recorded dates" }
        return first.id == last.id ? first.night.dateText : "\(first.night.dateText) to \(last.night.dateText)"
    }
    private var dayRange: ClosedRange<Double> {
        (points.first.map { $0.day - 0.5 } ?? 0)...(points.last.map { $0.day + 0.5 } ?? 1)
    }
    private var axisDays: [Double] {
        guard !points.isEmpty else { return [] }
        let first = points[0].day
        let last = points[points.count - 1].day
        let days = textSize.isAccessibilitySize ? [first, last] : [first, floor((first + last) / 2), last]
        return Array(Set(days)).sorted()
    }

    var body: some View {
        if !points.isEmpty {
            CalmNightCard {
                VStack(alignment: .leading, spacing: 20) {
                    Text("Recorded patterns").font(.title2.bold())
                    Text("Latest \(points.count) recorded \(points.count == 1 ? "date" : "dates") · \(dateRange)")
                        .font(.subheadline).foregroundStyle(CalmNightTheme.textSecondary)
                        .fixedSize(horizontal: false, vertical: true)
                    Text("Sleep score").font(.headline)
                    scoreChart
                    Text("Recorded duration").font(.headline)
                    durationChart
                    Text(points.count == 1 ? "One recorded date. More dates will reveal a pattern." : "Gaps are unrecorded dates. Score lines stop at each gap.")
                        .font(.footnote).foregroundStyle(CalmNightTheme.textSecondary)
                        .fixedSize(horizontal: false, vertical: true)
                    DisclosureGroup("Values by recorded date") {
                        ForEach(points) { point in
                            VStack(alignment: .leading, spacing: 4) {
                                Text(point.night.dateText).font(.subheadline.bold())
                                Text("Score \(point.night.score) · \(point.night.durationText) recorded")
                                    .font(.subheadline).foregroundStyle(CalmNightTheme.textSecondary)
                            }
                            .frame(maxWidth: .infinity, alignment: .leading).padding(.vertical, 4)
                            .accessibilityElement(children: .ignore)
                            .accessibilityLabel("\(point.durationAccessibility), sleep score \(point.night.score) out of 100")
                        }
                    }
                    .tint(CalmNightTheme.accent)
                }
            }
        }
    }

    private var scoreChart: some View {
        Chart(points) { point in
            LineMark(x: .value("Recorded date", point.day), y: .value("Sleep score", point.night.score),
                series: .value("Consecutive dates", point.segment))
                .foregroundStyle(CalmNightTheme.accent).interpolationMethod(.linear)
                .accessibilityHidden(true)
            PointMark(x: .value("Recorded date", point.day), y: .value("Sleep score", point.night.score))
                .foregroundStyle(CalmNightTheme.accent)
                .accessibilityLabel(point.night.dateText)
                .accessibilityValue("Sleep score \(point.night.score) out of 100")
        }
        .chartXScale(domain: dayRange).chartYScale(domain: 0...100)
        .chartXAxis { dateAxis }
        .chartYAxis {
            AxisMarks(position: .leading, values: [0, 50, 100]) { value in
                AxisGridLine()
                AxisValueLabel { if let score = value.as(Int.self) { Text("\(score)").font(.caption) } }
            }
        }
        .frame(height: 200)
        .accessibilityLabel("Sleep score chart, \(dateRange)")
        .accessibilityValue("\(points.count) recorded dates, score range 0 to 100")
        .accessibilityIdentifier("sleepScoreChart")
    }

    private var durationChart: some View {
        Chart(points) { point in
            // Day-unit bounds preserve separation even when a long gap compresses a recent cluster.
            RectangleMark(xStart: .value("Recorded date start", point.day - 0.3),
                xEnd: .value("Recorded date end", point.day + 0.3),
                yStart: .value("Recorded hours start", 0.0), yEnd: .value("Recorded hours", point.hours))
                .foregroundStyle(CalmNightTheme.recovery)
                .accessibilityLabel(point.night.dateText)
                .accessibilityValue(point.durationAccessibility)
        }
        .chartXScale(domain: dayRange).chartYScale(domain: 0...max(1, ceil(points.map(\.hours).max() ?? 0)))
        .chartXAxis { dateAxis }
        .chartYAxis {
            AxisMarks(position: .leading, values: .automatic(desiredCount: 4)) { value in
                AxisGridLine()
                AxisValueLabel { if let hours = value.as(Double.self) { Text(hours, format: .number).font(.caption) } }
            }
        }
        .chartYAxisLabel("Hours")
        .frame(height: 200)
        .accessibilityLabel("Recorded duration chart, \(dateRange)")
        .accessibilityValue("\(points.count) recorded dates, actual duration in hours")
        .accessibilityIdentifier("recordedDurationChart")
    }

    private var dateAxis: some AxisContent {
        AxisMarks(values: axisDays) { value in
            AxisGridLine().foregroundStyle(CalmNightTheme.textTertiary.opacity(0.35))
            AxisTick()
            AxisValueLabel(anchor: value.index == 0 ? .topLeading : (value.index == axisDays.count - 1 ? .topTrailing : .top)) {
                if let day = value.as(Double.self) {
                    Text(NightDateFormatting.axisLabel(epochDay: Int32(day)))
                        .font(.caption).foregroundStyle(CalmNightTheme.textSecondary)
                }
            }
        }
    }
}
