import XCTest

/// The iPad in landscape: the app must come up and keep running there, and survive a rotation
/// while a document is open.
@MainActor
final class LandscapeUITests: XCTestCase {
    private func launch() -> XCUIApplication {
        let app = XCUIApplication()
        app.launchArguments = ["-wr.spaceType", "offline", "-wr.colorTheme", "light"]
        app.launch()
        return app
    }

    func testStartsInLandscape() {
        XCUIDevice.shared.orientation = .landscapeLeft
        let app = launch()

        let welcome = app.staticTexts["Welcome!"].firstMatch
        XCTAssertTrue(welcome.waitForExistence(timeout: 15), "the documents never showed: \(app.state.rawValue)")
        sleep(3)
        XCTAssertEqual(app.state, .runningForeground)
    }

    func testRotatesToLandscapeWithADocumentOpen() {
        XCUIDevice.shared.orientation = .portrait
        let app = launch()

        let welcome = app.buttons.containing(.staticText, identifier: "Welcome!").firstMatch
        XCTAssertTrue(welcome.waitForExistence(timeout: 15), "the documents never showed: \(app.state.rawValue)")
        welcome.tap()
        XCTAssertTrue(app.textViews.firstMatch.waitForExistence(timeout: 10), "the editor never opened")

        XCUIDevice.shared.orientation = .landscapeLeft
        sleep(3)
        XCTAssertEqual(app.state, .runningForeground)
        XCTAssertTrue(app.buttons["editor.side.page"].waitForExistence(timeout: 5))
    }
}
