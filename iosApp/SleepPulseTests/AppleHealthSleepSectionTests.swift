import Combine
import SwiftUI
import XCTest
@testable import SleepPulse

@MainActor
final class AppleHealthSleepSectionTests: XCTestCase {
    override func setUpWithError() throws {
        try super.setUpWithError()
        // SwiftUI builds its runtime AX tree only for an accessibility client. This
        // test-host-only system hook enables inspection without a third-party dependency.
        let handle = try XCTUnwrap(dlopen("/usr/lib/libAccessibility.dylib", RTLD_LAZY))
        addTeardownBlock { dlclose(handle) }
        let setter = try XCTUnwrap(dlsym(handle, "_AXSSetAutomationEnabled"))
        let getter = try XCTUnwrap(dlsym(handle, "_AXSAutomationEnabled"))
        let setEnabled = unsafeBitCast(setter, to: (@convention(c) (Int32) -> Void).self)
        let previous = unsafeBitCast(getter, to: (@convention(c) () -> Int32).self)()
        setEnabled(1)
        addTeardownBlock { setEnabled(previous) }
    }

    func testUnavailableDoesNotOfferConnectionOrImplyDenial() async throws {
        try await render(snapshotContent(.init(phase: .unavailable)), height: 874) { host in
            let elements = accessibleElements(in: host.view)
            XCTAssertTrue(labels(elements).contains("Apple Health is unavailable"))
            XCTAssertFalse(labels(elements).lowercased().contains("denied"))
            XCTAssertNil(element("connectAppleHealth", in: elements))
            XCTAssertNil(element("refreshAppleHealth", in: elements))
            try attachVisibleSnapshot(host, "Apple Health unavailable", ids: ["appleHealthHeading", "appleHealthStatus"])
        }
    }

    func testNotConnectedAndPromptFailureKeepUsableConnectRetry() async throws {
        for phase in [HealthKitSleepPhase.notConnected, .failed] {
            var connections = 0
            var refreshes = 0
            let view = AppleHealthSleepContent(state: .init(phase: phase, error: "The request could not finish."),
                onConnect: { connections += 1 }, onRefresh: { refreshes += 1 }, onSettings: {})
            try await render(view) { host in
                let elements = accessibleElements(in: host.view)
                let connect = try XCTUnwrap(element("connectAppleHealth", in: elements))
                XCTAssertEqual(connect.accessibilityLabel, "Connect Apple Health")
                XCTAssertFalse(connect.accessibilityTraits.contains(.notEnabled))
                XCTAssertTrue(connect.accessibilityActivate())
                XCTAssertEqual(connections, 1)
                XCTAssertEqual(refreshes, 0)
                XCTAssertTrue(labels(elements).contains("The request could not finish."))
                if phase == .failed { XCTAssertTrue(labels(elements).contains("Try connecting again")) }
            }
        }
    }

    func testLoadingDisablesActionsAndRetainsCachedRowsAndMetadata() async throws {
        for cached in [false, true] {
            let state = HealthKitSleepState(phase: .loading, episodes: cached ? [episode()] : [],
                fetchedAt: cached ? date("2026-10-08T12:00:00Z") : nil,
                windowStart: cached ? date("2026-09-08T12:00:00Z") : nil,
                windowEnd: cached ? date("2026-10-08T12:00:00Z") : nil)
            try await render(content(state)) { host in
                let elements = accessibleElements(in: host.view)
                XCTAssertTrue(labels(elements).contains("Loading Apple Health sleep"))
                let action = try XCTUnwrap(element(cached ? "refreshAppleHealth" : "connectAppleHealth", in: elements))
                XCTAssertTrue(action.accessibilityTraits.contains(.notEnabled))
                if cached {
                    XCTAssertNotNil(element("appleHealthEpisode-watch", in: elements))
                    XCTAssertTrue(labels(elements).contains("Last fetched: Oct 8, 2026"))
                    XCTAssertTrue(labels(elements).contains("Sep 8, 2026"))
                    XCTAssertTrue(labels(elements).contains("last loaded"))
                }
            }
        }
    }

    func testLoadedRowIsOneCompleteVoiceOverElement() async throws {
        try await render(snapshotContent(.init(phase: .loaded, episodes: [episode()],
            fetchedAt: date("2026-10-08T12:00:00Z"), windowStart: date("2026-09-08T12:00:00Z"),
            windowEnd: date("2026-10-08T12:00:00Z"))), height: 874) { host in
            let elements = accessibleElements(in: host.view)
            XCTAssertEqual(element("appleHealthHeading", in: elements)?.accessibilityLabel, "From Apple Health")
            let rows = elements.filter { identifier($0) == "appleHealthEpisode-watch" }
            XCTAssertEqual(rows.count, 1)
            let row = try XCTUnwrap(rows.first)
            let summary = try XCTUnwrap(row.accessibilityLabel)
            for text in ["Source: Apple Watch", "Oct 7, 2026", "Asleep 420 minutes", "In bed 480 minutes",
                         "Awake Unavailable", "Core 300 minutes", "Deep 60 minutes", "REM 60 minutes"] {
                XCTAssertTrue(summary.contains(text), "Missing \(text) in \(summary)")
            }
            // Children must not also appear as separate VoiceOver stops.
            XCTAssertEqual(elements.filter { ($0.accessibilityLabel ?? "").contains("Apple Watch") }.count, 1)
            XCTAssertNotNil(element("refreshAppleHealth", in: elements))
            try attachVisibleSnapshot(host, "Apple Health loaded", ids: ["appleHealthHeading", "appleHealthFetchedAt",
                "appleHealthWindow", "refreshAppleHealth", "appleHealthEpisode-watch"])
        }
    }

    func testNilStagesAndZeroOrSubminuteDurationsRemainDistinct() async throws {
        let samples = [0.0, 30.0].enumerated().map { index, duration in
            HealthKitSleepSample(id: "sample-\(index)", sourceIdentifier: "source-\(index)", sourceName: "Source \(index)",
                stage: .asleepUnspecified, start: date("2026-10-07T22:00:00Z"),
                end: date("2026-10-07T22:00:00Z").addingTimeInterval(duration))
        }
        let episodes = HealthKitSleepNormalizer.episodes(from: samples)
        try await render(snapshotContent(.init(phase: .loaded, episodes: episodes)), height: 1_200) { host in
            let rows = accessibleElements(in: host.view).filter { (identifier($0) ?? "").hasPrefix("appleHealthEpisode-") }
            XCTAssertEqual(rows.count, 2)
            for row in rows {
                let summary = row.accessibilityLabel ?? ""
                XCTAssertTrue(summary.contains("Asleep less than 1 minute"))
                for stage in ["In bed", "Awake", "Core", "Deep", "REM"] {
                    XCTAssertTrue(summary.contains("\(stage) Unavailable"))
                }
            }
            try attachVisibleSnapshot(host, "Apple Health zero and subminute - unavailable stages",
                ids: episodes.map { "appleHealthEpisode-\($0.id)" })
        }
        try await render(content(.init(phase: .loaded, episodes: [episode(asleep: nil)]))) { host in
            let row = try XCTUnwrap(element("appleHealthEpisode-watch", in: accessibleElements(in: host.view)))
            XCTAssertTrue(row.accessibilityLabel?.contains("Asleep Unavailable") == true)
        }
    }

    func testRowsAreNewestFirstWithDeterministicTiesAndStableIdentifiers() async throws {
        let older = episode(id: "old", start: "2026-10-06T22:00:00Z")
        let a = episode(id: "a")
        let b = episode(id: "b")
        for episodes in [[older, b, a], [a, older, b]] {
            try await render(content(.init(phase: .loaded, episodes: episodes))) { host in
                let rows = accessibleElements(in: host.view).filter { (identifier($0) ?? "").hasPrefix("appleHealthEpisode-") }
                XCTAssertEqual(rows.map(identifier), ["appleHealthEpisode-a", "appleHealthEpisode-b", "appleHealthEpisode-old"])
            }
        }
    }

    func testDateOnlySummaryDoesNotShiftWithDeviceTimezone() async throws {
        let previous = NSTimeZone.default
        defer { NSTimeZone.default = previous }
        for hours in [-12, 14] {
            NSTimeZone.default = TimeZone(secondsFromGMT: hours * 3_600)!
            try await render(content(.init(phase: .loaded, episodes: [episode(start: "2026-10-07T00:30:00Z")]))) { host in
                let row = try XCTUnwrap(element("appleHealthEpisode-watch", in: accessibleElements(in: host.view)))
                XCTAssertTrue(row.accessibilityLabel?.contains("Oct 7, 2026") == true)
            }
        }
    }

    func testEmptyIsSuccessfulNoDataNotDenialAndRefreshIsExplicit() async throws {
        var refreshes = 0
        try await render(AppleHealthSleepContent(state: .init(phase: .empty), onConnect: {},
            onRefresh: { refreshes += 1 }, onSettings: {}).padding(20)
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
                .background(CalmNightTheme.background), height: 874) { host in
            let elements = accessibleElements(in: host.view)
            XCTAssertEqual(refreshes, 0)
            XCTAssertEqual(element("appleHealthStatus", in: elements)?.accessibilityLabel, "No Apple Health sleep records found")
            XCTAssertFalse(labels(elements).lowercased().contains("denied"))
            XCTAssertNil(element("openAppleHealthSettings", in: elements))
            let refresh = try XCTUnwrap(element("refreshAppleHealth", in: elements))
            try attachVisibleSnapshot(host, "Apple Health empty", ids: ["appleHealthHeading", "appleHealthStatus", "refreshAppleHealth"])
            XCTAssertTrue(refresh.accessibilityActivate())
            XCTAssertEqual(refreshes, 1)
        }
    }

    func testStaleCacheIncludingEmptyCacheExplainsFailureAndOffersRefresh() async throws {
        for episodes in [[episode()], []] {
            try await render(snapshotContent(.init(phase: .stale, episodes: episodes,
                fetchedAt: date("2026-10-08T12:00:00Z"), windowStart: date("2026-09-08T12:00:00Z"),
                windowEnd: date("2026-10-08T12:00:00Z"), error: "Could not read sleep.")), height: 874) { host in
                let elements = accessibleElements(in: host.view)
                let text = labels(elements)
                XCTAssertTrue(text.contains("Last loaded Apple Health sleep"))
                XCTAssertTrue(text.contains("Could not read sleep."))
                XCTAssertTrue(text.contains("Try refreshing again"))
                XCTAssertTrue(text.contains("Last fetched: Oct 8, 2026"))
                XCTAssertNotNil(element("refreshAppleHealth", in: elements))
                XCTAssertTrue(text.contains("Import window: Sep 8, 2026 – Oct 8, 2026"))
                if episodes.isEmpty { XCTAssertTrue(text.contains("No sleep data in the last loaded result")) }
                try attachVisibleSnapshot(host, episodes.isEmpty ? "Apple Health stale empty cache" : "Apple Health stale retained sleep",
                    ids: ["appleHealthHeading", "appleHealthError", "appleHealthFetchedAt", "appleHealthWindow", "refreshAppleHealth"] +
                        episodes.map { "appleHealthEpisode-\($0.id)" })
            }
        }
    }

    func testSettingsActionRequiresStateEvidence() async throws {
        for url in [nil, URL(string: UIApplication.openSettingsURLString)] {
            var settings = 0
            try await render(AppleHealthSleepContent(state: .init(phase: .failed, openSettingsURL: url),
                onConnect: {}, onRefresh: {}, onSettings: { settings += 1 })) { host in
                let action = element("openAppleHealthSettings", in: accessibleElements(in: host.view))
                if url == nil { XCTAssertNil(action) }
                else {
                    XCTAssertTrue(try XCTUnwrap(action).accessibilityActivate())
                    XCTAssertEqual(settings, 1)
                }
            }
        }
    }

    func testObservedWrapperOnlyConnectsOrRefreshesFromButtonsAndRecoversAfterPromptFailure() async throws {
        let client = FakeHealthKitClient()
        client.requestAccessError = HealthKitClientError.needsSettings("Review access in Settings.")
        let directory = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        let store = HealthKitSleepStore(client: client,
            cache: HealthKitCacheStore(url: directory.appendingPathComponent("sleep.json")))
        addTeardownBlock {
            if FileManager.default.fileExists(atPath: directory.path) { try FileManager.default.removeItem(at: directory) }
        }
        var openedURLs: [URL] = []
        let view = AppleHealthSleepSection(store: store).environment(\.openURL, OpenURLAction { url in
            openedURLs.append(url)
            return .handled
        })
        try await render(view) { host in
            XCTAssertEqual(client.requestReadAccessCallCount, 0)
            XCTAssertTrue(client.readWindows.isEmpty)
            XCTAssertTrue(openedURLs.isEmpty)
            try activate("connectAppleHealth", in: host)
            await waitForPhase(.failed, store: store)
            try await settle(host)
            try activate("openAppleHealthSettings", in: host)
            XCTAssertEqual(openedURLs, [URL(string: UIApplication.openSettingsURLString)!])

            client.requestAccessError = nil
            try activate("connectAppleHealth", in: host)
            await waitForPhase(.empty, store: store)
            try await settle(host)
            XCTAssertEqual(client.requestReadAccessCallCount, 2)
            XCTAssertEqual(client.readWindows.count, 1)
            XCTAssertEqual(element("appleHealthStatus", in: accessibleElements(in: host.view))?.accessibilityLabel,
                "No Apple Health sleep records found")
            XCTAssertNil(element("openAppleHealthSettings", in: accessibleElements(in: host.view)))

            try activate("refreshAppleHealth", in: host)
            await waitForPhase(.empty, store: store)
            try await settle(host)
            XCTAssertEqual(client.requestReadAccessCallCount, 2, "Refresh must never prompt")
            XCTAssertEqual(client.readWindows.count, 2)
        }
    }

    func testHistoryHasOneScrollAndAppleHealthIsReachableAtLargeTextAndIPadWidth() async throws {
        for (width, largeText) in [(402.0, true), (834.0, false)] {
            let view = HistoryContent(state: DemoNights.state(count: 3)) {
                content(.init(phase: .loaded, episodes: [episode()]))
            }
            try await render(view, width: width, height: 874, largeText: largeText) { host in
                let scrolls = scrollViews(in: host.view)
                XCTAssertEqual(scrolls.count, 1)
                let scroll = try XCTUnwrap(scrolls.first)
                // Lazy content refines its height as it appears. Reaching the bottom
                // must expose Apple Health through the same scroll view as simulated nights.
                for _ in 0..<3 {
                    scroll.setContentOffset(CGPoint(x: 0, y: max(0, scroll.contentSize.height - scroll.bounds.height)), animated: false)
                    try await settle(host)
                }
                let row = try XCTUnwrap(element("appleHealthEpisode-watch", in: accessibleElements(in: host.view)))
                XCTAssertGreaterThan(row.accessibilityFrame.height, 0)
                XCTAssertTrue(row.accessibilityFrame.intersects(host.view.convert(host.view.bounds, to: nil)))
                XCTAssertLessThanOrEqual(row.accessibilityFrame.width, 720)
                XCTAssertEqual(row.accessibilityFrame.midX, host.view.convert(host.view.bounds, to: nil).midX, accuracy: 2)
                let image = UIGraphicsImageRenderer(bounds: host.view.bounds).image { _ in
                    host.view.drawHierarchy(in: host.view.bounds, afterScreenUpdates: true)
                }
                let attachment = XCTAttachment(image: image)
                attachment.name = largeText ? "Apple Health History accessibility text" : "Apple Health History iPad width"
                attachment.lifetime = .keepAlways
                add(attachment)
            }
        }
    }

    private func content(_ state: HealthKitSleepState) -> some View {
        AppleHealthSleepContent(state: state, onConnect: {}, onRefresh: {}, onSettings: {})
    }

    private func snapshotContent(_ state: HealthKitSleepState) -> some View {
        content(state).padding(20)
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
            .background(CalmNightTheme.background)
    }

    private func attachVisibleSnapshot(_ host: UIHostingController<AnyView>, _ name: String, ids: [String]) throws {
        let elements = accessibleElements(in: host.view)
        let bounds = host.view.convert(host.view.bounds, to: nil).insetBy(dx: -1, dy: -1)
        for id in ids {
            let frame = try XCTUnwrap(element(id, in: elements), "Missing \(id)").accessibilityFrame
            XCTAssertGreaterThan(frame.height, 0, id)
            XCTAssertTrue(bounds.contains(frame), "\(id) clipped: \(frame) outside \(bounds)")
        }
        let image = UIGraphicsImageRenderer(bounds: host.view.bounds).image { _ in
            host.view.drawHierarchy(in: host.view.bounds, afterScreenUpdates: true)
        }
        let attachment = XCTAttachment(image: image)
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
    }

    private func render<Content: View>(_ content: Content, width: CGFloat = 402, height: CGFloat = 2_400,
        largeText: Bool = false, check: (UIHostingController<AnyView>) async throws -> Void) async throws {
        let scene = try XCTUnwrap(UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.first)
        let previous = scene.windows.first(where: \.isKeyWindow)
        let window = UIWindow(windowScene: scene)
        let host = UIHostingController(rootView: AnyView(content
            .environment(\.locale, Locale(identifier: "en_US"))
            .environment(\.colorScheme, .dark)
            .environment(\.dynamicTypeSize, largeText ? .accessibility5 : .large)))
        window.frame = CGRect(x: 0, y: 0, width: width, height: height)
        window.rootViewController = host
        window.makeKeyAndVisible()
        defer { window.isHidden = true; previous?.makeKey() }
        try await settle(host)
        try await check(host)
    }

    private func settle(_ host: UIHostingController<AnyView>) async throws {
        host.view.layoutIfNeeded()
        try await Task.sleep(for: .milliseconds(100))
        host.view.layoutIfNeeded()
    }

    private func activate(_ id: String, in host: UIHostingController<AnyView>) throws {
        XCTAssertTrue(try XCTUnwrap(element(id, in: accessibleElements(in: host.view))).accessibilityActivate())
    }

    private func waitForPhase(_ phase: HealthKitSleepPhase, store: HealthKitSleepStore) async {
        let reachedPhase = expectation(description: "HealthKit phase \(phase)")
        let observation = store.$state.filter { $0.phase == phase }.prefix(1).sink { _ in reachedPhase.fulfill() }
        await fulfillment(of: [reachedPhase], timeout: 2)
        observation.cancel()
    }

    private func accessibleElements(in root: NSObject) -> [NSObject] {
        var visited = Set<ObjectIdentifier>()
        func walk(_ object: NSObject) -> [NSObject] {
            guard visited.insert(ObjectIdentifier(object)).inserted else { return [] }
            if object.isAccessibilityElement { return [object] }
            if let children = object.accessibilityElements as? [NSObject], !children.isEmpty {
                return children.flatMap(walk)
            }
            let count = object.accessibilityElementCount()
            if count > 0, count < 10_000 {
                return (0..<count).compactMap { object.accessibilityElement(at: $0) as? NSObject }.flatMap(walk)
            }
            return (object as? UIView)?.subviews.flatMap(walk) ?? []
        }
        return walk(root)
    }

    private func labels(_ elements: [NSObject]) -> String { elements.compactMap(\.accessibilityLabel).joined(separator: "\n") }
    private func identifier(_ element: NSObject) -> String? {
        // SwiftUI's AX nodes implement this public selector without protocol conformance.
        guard element.responds(to: NSSelectorFromString("accessibilityIdentifier")) else { return nil }
        return element.value(forKey: "accessibilityIdentifier") as? String
    }
    private func element(_ id: String, in elements: [NSObject]) -> NSObject? {
        elements.first { identifier($0) == id }
    }
    private func scrollViews(in view: UIView) -> [UIScrollView] {
        ((view as? UIScrollView).map { [$0] } ?? []) + view.subviews.flatMap(scrollViews)
    }
    private func date(_ value: String) -> Date { ISO8601DateFormatter().date(from: value)! }
    private func episode(id: String = "watch", start: String = "2026-10-07T22:00:00Z", asleep: Int? = 420) -> HealthKitSleepEpisode {
        HealthKitSleepEpisode(id: id, sourceIdentifier: "com.apple.watch", sourceName: "Apple Watch",
            start: date(start), end: date("2026-10-08T06:00:00Z"), inBedMinutes: 480, asleepMinutes: asleep,
            awakeMinutes: nil, coreMinutes: 300, deepMinutes: 60, remMinutes: 60)
    }
}
