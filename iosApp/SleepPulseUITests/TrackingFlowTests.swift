import XCTest

@MainActor
final class TrackingFlowTests: XCTestCase {
    override func setUpWithError() throws { continueAfterFailure = false }

    func testRealTimeTrackingAcrossTabsSaveAndRelaunch() throws {
        let app = isolatedApp()
        app.launch()
        assertNoSystemSheet(app)
        attach(app, "Home idle")
        app.tabBars.buttons["History"].tap()
        XCTAssertTrue(app.staticTexts["No simulated nights yet"].waitForExistence(timeout: 5))
        try assertDisconnectedAppleHealth(app)
        attach(app, "Apple Health before simulated Start - no system sheet")
        app.tabBars.buttons["Recovery"].tap()
        assertSimulatedBaseline(in: app, recordedDates: 0)
        app.tabBars.buttons["Home"].tap()
        let control = app.buttons["trackingControl"]
        let minuteValue = app.staticTexts.matching(NSPredicate(format: "label == %@", "1 min")).firstMatch
        reveal(control, in: app)
        XCTAssertTrue(control.waitForExistence(timeout: 8))
        XCTAssertTrue(control.isEnabled)
        control.tap()
        XCTAssertTrue(app.buttons["Stop and save"].waitForExistence(timeout: 8))
        assertNoSystemSheet(app)
        let started = Date()
        app.tabBars.buttons["History"].tap()
        try assertDisconnectedAppleHealth(app)
        attach(app, "Apple Health during simulated tracking - no system sheet")
        app.tabBars.buttons["Recovery"].tap()
        assertSimulatedBaseline(in: app, recordedDates: 0)
        attach(app, "Recovery baseline while tracking")
        app.tabBars.buttons["Home"].tap()
        reveal(control, in: app)
        XCTAssertEqual(control.label, "Stop and save")
        XCTAssertTrue(control.isEnabled)
        assertNoSystemSheet(app)
        Thread.sleep(forTimeInterval: max(0, 66 - Date().timeIntervalSince(started)))
        let elapsed = app.staticTexts["recordedTime"].label
        XCTAssertTrue(elapsed.hasPrefix("Recorded time 01:"), "Expected real recorded minute, got \(elapsed)")
        XCTAssertGreaterThanOrEqual(Date().timeIntervalSince(started), 65)
        attach(app, "Home after real-time minute")
        control.tap()
        expectation(for: NSPredicate(format: "label == %@ AND enabled == true", "Start tracking"), evaluatedWith: control)
        waitForExpectations(timeout: 8)
        app.tabBars.buttons["History"].tap()
        reveal(minuteValue, in: app)
        XCTAssertTrue(minuteValue.waitForExistence(timeout: 8))
        attach(app, "Saved History")
        app.terminate()
        app.launch()
        assertNoSystemSheet(app)
        app.tabBars.buttons["History"].tap()
        reveal(minuteValue, in: app)
        XCTAssertTrue(minuteValue.waitForExistence(timeout: 8))
        attach(app, "History after relaunch")
        try assertDisconnectedAppleHealth(app)
        app.tabBars.buttons["Recovery"].tap()
        assertSimulatedBaseline(in: app, recordedDates: 1)
    }

    func testBackgroundSaveAndLargeTextLayout() throws {
        let app = isolatedApp()
        app.launchArguments = ["-UIPreferredContentSizeCategoryName", "UICTContentSizeCategoryAccessibilityXXXL"]
        app.launch()
        app.tabBars.buttons["History"].tap()
        XCTAssertTrue(app.staticTexts["No simulated nights yet"].waitForExistence(timeout: 5))
        app.tabBars.buttons["Home"].tap()
        let control = app.buttons["trackingControl"]
        reveal(control, in: app)
        XCTAssertTrue(control.waitForExistence(timeout: 8))
        control.tap()
        XCTAssertTrue(app.buttons["Stop and save"].waitForExistence(timeout: 8))
        Thread.sleep(forTimeInterval: 2)
        attach(app, "Large text Home controls")
        XCUIDevice.shared.press(.home)
        Thread.sleep(forTimeInterval: 2)
        app.activate()
        reveal(control, in: app)
        expectation(for: NSPredicate(format: "label == %@ AND enabled == true", "Start tracking"), evaluatedWith: control)
        waitForExpectations(timeout: 8)
        XCTAssertTrue(app.staticTexts["Session saved. History keeps the longest session for each date."].exists)
        app.tabBars.buttons["History"].tap()
        let average = app.staticTexts.matching(NSPredicate(format: "label BEGINSWITH %@", "Average heart rate")).firstMatch
        reveal(average, in: app)
        XCTAssertTrue(average.waitForExistence(timeout: 8))
        attach(app, "Large text History")
    }

    func testRecoveryBaselineAndChartAccessibilityDescriptions() throws {
        let app = isolatedApp()
        app.launch()
        app.tabBars.buttons["History"].tap()
        XCTAssertTrue(app.staticTexts["No simulated nights yet"].waitForExistence(timeout: 5))
        app.tabBars.buttons["Recovery"].tap()
        assertSimulatedBaseline(in: app, recordedDates: 0)
        try auditVisibleContent(app)
        reveal(app.staticTexts["HRV"], in: app)
        XCTAssertTrue(app.staticTexts["HRV"].isHittable)
        try auditVisibleContent(app)

        // This test owns its chart data; no dependency on a preceding test or installed history.
        app.tabBars.buttons["Home"].tap()
        let control = app.buttons["trackingControl"]
        reveal(control, in: app)
        XCTAssertTrue(control.waitForExistence(timeout: 8))
        XCTAssertTrue(control.isEnabled)
        control.tap()
        XCTAssertTrue(app.buttons["Stop and save"].waitForExistence(timeout: 8))
        let recorded = app.staticTexts["recordedTime"]
        XCTAssertTrue(recorded.waitForExistence(timeout: 5))
        expectation(for: NSPredicate(format: "label != %@", "Recorded time 00:00"), evaluatedWith: recorded)
        waitForExpectations(timeout: 8)
        control.tap()
        expectation(for: NSPredicate(format: "label == %@ AND enabled == true", "Start tracking"), evaluatedWith: control)
        waitForExpectations(timeout: 8)
        app.tabBars.buttons["History"].tap()
        let score = app.descendants(matching: .any).matching(identifier: "sleepScoreChart").firstMatch
        reveal(score, in: app)
        XCTAssertTrue(score.waitForExistence(timeout: 5))
        XCTAssertTrue(score.label.contains("Sleep score chart"))
        let duration = app.descendants(matching: .any).matching(identifier: "recordedDurationChart").firstMatch
        reveal(duration, in: app)
        XCTAssertTrue(duration.waitForExistence(timeout: 5))
        XCTAssertTrue(duration.label.contains("Recorded duration chart"))
        attach(app, "Accessible actual-duration chart")
    }

    private func isolatedApp() -> XCUIApplication {
        let app = XCUIApplication()
        let id = UUID().uuidString
        app.launchEnvironment["SLEEPPULSE_UI_TEST_STORAGE_ID"] = id
        let attachment = XCTAttachment(string: id)
        attachment.name = "Test-owned storage UUID"
        attachment.lifetime = .keepAlways
        add(attachment)
        return app // The same environment/UUID is retained by intentional app.launch() relaunches.
    }

    private func auditVisibleContent(_ app: XCUIApplication) throws {
        // iOS 26's image heuristics flag even 13.65:1 text and an unclipped debt
        // value with a spoken-duration label. Contrast/layout are verified separately.
        try app.performAccessibilityAudit(for: [.sufficientElementDescription])
    }

    private func assertDisconnectedAppleHealth(_ app: XCUIApplication) throws {
        let heading = app.descendants(matching: .any).matching(identifier: "appleHealthHeading").firstMatch
        reveal(heading, in: app)
        XCTAssertTrue(heading.isHittable)
        XCTAssertEqual(heading.label, "From Apple Health")
        let connect = app.buttons["connectAppleHealth"]
        reveal(connect, in: app)
        XCTAssertTrue(connect.isHittable)
        XCTAssertTrue(connect.isEnabled)
        XCTAssertEqual(connect.label, "Connect Apple Health")
        XCTAssertEqual(app.staticTexts["appleHealthStatus"].label, "Connect your sleep history")
        XCTAssertFalse(app.buttons["refreshAppleHealth"].exists)
        assertNoSystemSheet(app)
        try auditVisibleContent(app)
        // Never tap Connect here. Controlled permission outcomes use the fake-backed host tests.
    }

    private func assertSimulatedBaseline(in app: XCUIApplication, recordedDates: Int) {
        XCTAssertTrue(app.staticTexts["Simulated insights"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.staticTexts["Building your baseline"].waitForExistence(timeout: 5))
        let progress = app.staticTexts["\(recordedDates) of 4 dates recorded. \(4 - recordedDates) more dates needed."]
        XCTAssertTrue(progress.waitForExistence(timeout: 5))
        XCTAssertTrue(app.staticTexts["Unavailable until your recovery baseline is ready."].exists)
        if recordedDates == 0 {
            XCTAssertTrue(app.staticTexts["No saved night yet"].exists)
            XCTAssertTrue(app.staticTexts["Start tracking on Home, then stop and save your first session."].exists)
        }
    }

    private func assertNoSystemSheet(_ app: XCUIApplication) {
        XCTAssertEqual(app.state, .runningForeground)
        XCTAssertFalse(app.alerts.firstMatch.exists)
        XCTAssertFalse(app.sheets.firstMatch.exists)
        let system = XCUIApplication(bundleIdentifier: "com.apple.springboard")
        XCTAssertFalse(system.alerts.firstMatch.exists)
        XCTAssertFalse(system.sheets.firstMatch.exists)
        XCTAssertFalse(app.navigationBars["Health Access"].exists)
    }

    private func reveal(_ element: XCUIElement, in app: XCUIApplication) {
        for _ in 0..<8 {
            if element.exists && element.isHittable { return }
            app.swipeUp()
        }
        // History retains its scroll position when returning from another tab.
        // Saved simulated rows precede Apple Health, so they may now be above us.
        for _ in 0..<8 {
            if element.exists && element.isHittable { return }
            app.swipeDown()
        }
    }

    private func attach(_ app: XCUIApplication, _ name: String) {
        let attachment = XCTAttachment(screenshot: app.screenshot())
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
    }
}
