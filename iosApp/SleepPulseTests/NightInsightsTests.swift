import XCTest
import SwiftUI
@testable import SleepPulse

@MainActor
final class NightInsightsTests: XCTestCase {
    func testEmptyAndPartialFixturesExposeHonestProgress() throws {
        let empty = DemoNights.state(count: 0)
        let insights = try XCTUnwrap(empty.insights)
        XCTAssertNil(insights.recovery)
        XCTAssertNil(insights.debt)
        XCTAssertEqual(insights.remainingBaselineDates, 4)
        XCTAssertTrue(HistoryChartPoint.make(nights: empty.nights).isEmpty)
        let partial = try XCTUnwrap(DemoNights.state(count: 3).insights)
        XCTAssertNil(partial.readiness)
        XCTAssertEqual(partial.remainingBaselineDates, 1)
        XCTAssertEqual(partial.remainingTrendDates, 11)
        XCTAssertEqual(partial.consistencyScore, 100)
    }

    func testFullFixtureUsesActualKotlinCalculatorsAndPreservesZeroDebt() throws {
        let state = DemoNights.state()
        let insights = try XCTUnwrap(state.insights)
        XCTAssertEqual(insights.recordedNights, 14)
        XCTAssertEqual(insights.baselineNights, 7)
        XCTAssertEqual(insights.recovery?.score, 89)
        XCTAssertEqual(insights.readiness?.score, 99)
        XCTAssertEqual(insights.debt?.deficitMinutes, 0)
        XCTAssertEqual(insights.debt?.targetMinutes, 480)
        XCTAssertEqual(insights.hrvTrend?.direction, .rising)
        XCTAssertEqual(insights.heartRateTrend?.direction, .falling)
        XCTAssertEqual(insights.recentKnownHrvNights, 7)
        XCTAssertEqual(insights.priorKnownHrvNights, 7)
        XCTAssertEqual(insights.latestIsoDate, "2026-10-07")
        XCTAssertTrue(insights.recovery?.heartRateComparison.contains("below") == true)
        XCTAssertNil(insights.scoreChanges[state.nights.last!.id]?.direction)
    }

    func testMissingHrvKeepsHeartRateRecoveryAndExplainsCoverage() throws {
        let insights = try XCTUnwrap(DemoNights.state(missingHrv: true).insights)
        XCTAssertEqual(insights.recovery?.score, 73)
        XCTAssertEqual(insights.readiness?.score, 78)
        XCTAssertNil(insights.recovery?.hrvDeviation)
        XCTAssertNil(insights.hrvTrend)
        XCTAssertNotNil(insights.heartRateTrend)
        XCTAssertEqual(insights.recovery?.hrvComparison, "Unavailable")
        XCTAssertEqual(insights.remainingTrendDates, 0)
        XCTAssertTrue(insights.hrvUnavailableText.contains("known HRV"))
    }

    func testChartPointsAreChronologicalLimitedAndDoNotBridgeDateGaps() {
        let full = HistoryChartPoint.make(nights: DemoNights.state(count: 20).nights)
        XCTAssertEqual(full.count, 14)
        XCTAssertEqual(full.first?.id, 20_720)
        XCTAssertEqual(full.last?.id, 20_733)
        let gaps = HistoryChartPoint.make(nights: [night(10), night(8), night(7)])
        XCTAssertEqual(gaps.map(\.id), [7, 8, 10])
        XCTAssertEqual(gaps.map(\.segment), [0, 0, 1])
        XCTAssertEqual(gaps.last?.hours, 0)
        XCTAssertTrue(gaps.last?.durationAccessibility.contains("less than 1 minute") == true)
    }

    func testDateFormattingDoesNotShiftWithDeviceTimezone() {
        let previous = NSTimeZone.default
        defer { NSTimeZone.default = previous }
        NSTimeZone.default = TimeZone(secondsFromGMT: -12 * 3_600)!
        XCTAssertEqual(NightDateFormatting.text("2026-10-07"), "Oct 7, 2026")
        NSTimeZone.default = TimeZone(secondsFromGMT: 14 * 3_600)!
        XCTAssertEqual(NightDateFormatting.text("2026-10-07"), "Oct 7, 2026")
        XCTAssertEqual(NightDateFormatting.axisLabel(epochDay: 20_733), "Oct 7")
    }

    func testLoadingAndFailedSaveDoNotPresentRetainedInsightsAsFresh() {
        var state = DemoNights.state()
        state.phase = .recovering
        XCTAssertTrue(state.isLoadingHistory)
        XCTAssertEqual(state.insightsStatusText, "Loading saved-night insights…")
        state.phase = .failed
        state.error = "Could not save session"
        XCTAssertFalse(state.isLoadingHistory)
        XCTAssertEqual(state.insightsStatusText, "Last loaded data · Oct 7, 2026")
        XCTAssertEqual(state.insights?.recovery?.score, 89)
        state.nights = []
        state.insights = nil
        XCTAssertEqual(state.insightsStatusText, "Saved-night insights unavailable")
    }

    func testFixtureScreensRenderWithoutOpeningOrSeedingADatabase() async throws {
        for (name, state) in [("Empty", DemoNights.state(count: 0)), ("Partial", DemoNights.state(count: 3)),
                              ("Full", DemoNights.state()), ("Missing HRV", DemoNights.state(missingHrv: true))] {
            try await capture(RecoveryContent(state: state), name: "Recovery \(name)")
            try await capture(HistoryContent(state: state), name: "History \(name)")
        }
        try await capture(RecoveryContent(state: DemoNights.state()), name: "Recovery large text", largeText: true)
        try await capture(HistoryContent(state: DemoNights.state()), name: "History large text", largeText: true)
        try await capture(HistoryTrendsView(nights: DemoNights.state().nights)
            .padding(20).background(CalmNightTheme.background).foregroundStyle(CalmNightTheme.textPrimary),
            name: "Populated score and duration charts")
        try await capture(HistoryTrendsView(nights: [night(10), night(8), night(7)])
            .padding(20).background(CalmNightTheme.background).foregroundStyle(CalmNightTheme.textPrimary),
            name: "Gapped dates and zero durations")
        try await capture(RecoveryContent(state: DemoNights.state(missingHrv: true)),
            name: "Heart-rate-only recovery explanation", scrollOffset: 450)
        try await capture(ScrollView { HistoryTrendsView(nights: DemoNights.state().nights).padding(20) }
            .background(CalmNightTheme.background).foregroundStyle(CalmNightTheme.textPrimary),
            name: "Charts large text", largeText: true)
        try await capture(HistoryContent(state: DemoNights.state()), name: "History iPad", width: 834)
        let widelySpaced = Array(DemoNights.state().nights.prefix(13)) + [DemoNights.state(count: 114).nights.last!]
        try await capture(HistoryTrendsView(nights: widelySpaced)
            .padding(20).background(CalmNightTheme.background).foregroundStyle(CalmNightTheme.textPrimary),
            name: "Dense recent dates after a 100-day gap")
    }

    private func capture<Content: View>(_ content: Content, name: String, largeText: Bool = false,
                                       width: CGFloat = 402, scrollOffset: CGFloat? = nil) async throws {
        let scene = try XCTUnwrap(UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.first)
        let previousWindow = scene.windows.first(where: \.isKeyWindow)
        let window = UIWindow(windowScene: scene)
        let host = UIHostingController(rootView: content
            .environment(\.colorScheme, .dark)
            .environment(\.dynamicTypeSize, largeText ? .accessibility5 : .large))
        window.frame = CGRect(x: 0, y: 0, width: width, height: 874)
        window.rootViewController = host
        window.makeKeyAndVisible()
        defer { window.isHidden = true; previousWindow?.makeKey() }
        host.view.backgroundColor = UIColor(CalmNightTheme.background)
        host.view.layoutIfNeeded()
        try await Task.sleep(for: .milliseconds(100))
        if let scrollOffset, let scroll = scrollView(in: host.view) {
            scroll.setContentOffset(CGPoint(x: 0, y: scrollOffset), animated: false)
            host.view.layoutIfNeeded()
        }
        let image = UIGraphicsImageRenderer(bounds: host.view.bounds).image { _ in
            host.view.drawHierarchy(in: host.view.bounds, afterScreenUpdates: true)
        }
        let pixels = try XCTUnwrap(image.cgImage?.dataProvider?.data) as Data
        XCTAssertGreaterThan(Set(stride(from: 0, to: pixels.count, by: 257).map { pixels[$0] }).count, 10,
            "\(name) rendered a blank surface instead of fixture content")
        let attachment = XCTAttachment(image: image)
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
    }

    private func scrollView(in view: UIView) -> UIScrollView? {
        if let scroll = view as? UIScrollView { return scroll }
        return view.subviews.lazy.compactMap { self.scrollView(in: $0) }.first
    }

    private func night(_ day: Int32) -> SavedNight {
        SavedNight(id: day, isoDate: "1970-01-\(String(format: "%02d", day + 1))", score: 70,
            totalMinutes: 0, deepMinutes: 0, remMinutes: 0, averageHeartRate: 60, averageHrv: nil)
    }
}
