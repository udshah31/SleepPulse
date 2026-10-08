import XCTest

@MainActor
final class TrackingFlowTests: XCTestCase {
    override func setUpWithError() throws { continueAfterFailure = false }

    func testRealTimeTrackingAcrossTabsSaveAndRelaunch() throws {
        let app = XCUIApplication()
        app.launch()
        attach(app, "Home idle")
        let control = app.buttons["trackingControl"]
        reveal(control, in: app)
        XCTAssertTrue(control.waitForExistence(timeout: 8))
        XCTAssertTrue(control.isEnabled)
        control.tap()
        XCTAssertTrue(app.buttons["Stop and save"].waitForExistence(timeout: 8))
        let started = Date()
        app.tabBars.buttons["History"].tap()
        XCTAssertTrue(app.staticTexts["Simulated nights · latest 30 dates"].waitForExistence(timeout: 5))
        app.tabBars.buttons["Home"].tap()
        reveal(control, in: app)
        XCTAssertEqual(control.label, "Stop and save")
        Thread.sleep(forTimeInterval: 66)
        let elapsed = app.staticTexts["recordedTime"].label
        XCTAssertTrue(elapsed.hasPrefix("Recorded time 01:"), "Expected real recorded minute, got \(elapsed)")
        XCTAssertGreaterThanOrEqual(Date().timeIntervalSince(started), 65)
        attach(app, "Home after real-time minute")
        control.tap()
        expectation(for: NSPredicate(format: "label == %@ AND enabled == true", "Start tracking"), evaluatedWith: control)
        waitForExpectations(timeout: 8)
        app.tabBars.buttons["History"].tap()
        XCTAssertTrue(app.staticTexts["1 min"].waitForExistence(timeout: 8))
        attach(app, "Saved History")
        app.terminate()
        app.launch()
        app.tabBars.buttons["History"].tap()
        XCTAssertTrue(app.staticTexts["1 min"].waitForExistence(timeout: 8))
        attach(app, "History after relaunch")
    }

    func testBackgroundSaveAndLargeTextLayout() throws {
        let app = XCUIApplication()
        app.launchArguments = ["-UIPreferredContentSizeCategoryName", "UICTContentSizeCategoryAccessibilityXXXL"]
        app.launch()
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
        XCTAssertTrue(average.waitForExistence(timeout: 8))
        attach(app, "Large text History")
    }

    private func reveal(_ element: XCUIElement, in app: XCUIApplication) {
        for _ in 0..<8 {
            if element.exists && element.isHittable { return }
            app.swipeUp()
        }
    }

    private func attach(_ app: XCUIApplication, _ name: String) {
        let attachment = XCTAttachment(screenshot: app.screenshot())
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
    }
}
