import XCTest

/// End to end walk through the screens that don't need a backend.
@MainActor
final class SmokeUITests: XCTestCase {
    private var app: XCUIApplication!

    override func setUp() async throws {
        continueAfterFailure = false
        app = XCUIApplication()
        // Argument domain defaults: always start from the space choice, in light mode.
        app.launchArguments = ["-wr.spaceType", "", "-wr.colorTheme", "light"]
        app.launch()
    }

    func testPrivateSpaceDocumentsSearchAndSettings() {
        app.buttons["space.private"].tap()

        // Documents: the seeded welcome document opens and renders its steps.
        let welcome = app.buttons.containing(.staticText, identifier: "Welcome to Writeopia").firstMatch
        XCTAssertTrue(welcome.waitForExistence(timeout: 5))
        welcome.tap()
        // The document opens in the editor: its steps are editable text views.
        let heading = app.textViews.matching(NSPredicate(format: "value == %@", "Getting started")).firstMatch
        XCTAssertTrue(heading.waitForExistence(timeout: 5))
        heading.tap()
        heading.typeText(" now")
        let edited = app.textViews.matching(NSPredicate(format: "value == %@", "Getting started now")).firstMatch
        XCTAssertTrue(edited.waitForExistence(timeout: 3))
        goBack()

        // Create a folder.
        app.buttons["documents.add"].tap()
        app.buttons["New folder"].tap()
        let title = app.textFields["folderEdit.name"]
        XCTAssertTrue(title.waitForExistence(timeout: 3))
        title.typeText("Ideas")
        app.buttons["folderEdit.save"].tap()
        XCTAssertTrue(app.staticTexts["Ideas"].waitForExistence(timeout: 5))

        // Drag the welcome document onto the folder.
        let folderCard = app.buttons.containing(.staticText, identifier: "Ideas").firstMatch
        let documentCard = app.buttons.containing(.staticText, identifier: "Welcome to Writeopia").firstMatch
        // A slow drag with a hold over the folder, so the drop session picks it up.
        documentCard.press(forDuration: 1.5, thenDragTo: folderCard, withVelocity: .slow, thenHoldForDuration: 1)
        XCTAssertTrue(documentCard.waitForNonExistence(timeout: 5))
        XCTAssertTrue(app.staticTexts["1 item"].waitForExistence(timeout: 5))

        // The document is now inside the folder.
        folderCard.tap()
        XCTAssertTrue(app.staticTexts["Welcome to Writeopia"].waitForExistence(timeout: 5))
        goBack()

        // Search.
        app.tabBars.buttons["Search"].tap()
        let searchField = app.searchFields.firstMatch
        XCTAssertTrue(searchField.waitForExistence(timeout: 3))
        searchField.tap()
        searchField.typeText("device")
        XCTAssertTrue(app.staticTexts["Welcome to Writeopia"].waitForExistence(timeout: 5))

        // Settings > General > color theme.
        app.tabBars.buttons["Settings"].tap()
        app.buttons["General"].tap()
        app.buttons["theme.dark"].tap()
        XCTAssertTrue(app.buttons["theme.dark"].isSelected)
        app.buttons["theme.light"].tap()
        goBack()

        // Teams and AI in the private space.
        app.buttons["Teams"].tap()
        XCTAssertTrue(app.staticTexts["Teams need an account"].waitForExistence(timeout: 3))
        goBack()
        app.buttons["AI"].tap()
        XCTAssertTrue(app.staticTexts["AI needs an account"].waitForExistence(timeout: 3))
        goBack()

        // Account: switch space goes back to the space choice.
        app.buttons["settings.account"].tap()
        XCTAssertTrue(app.buttons["account.signIn"].waitForExistence(timeout: 3))
        app.buttons["account.switchSpace"].tap()
        XCTAssertTrue(app.buttons["space.open"].waitForExistence(timeout: 3))
    }

    /// Taps the system back button of the top navigation bar. Taps made while a push
    /// transition is still running are dropped, so it retries until the bar is gone.
    private func goBack(file: StaticString = #filePath, line: UInt = #line) {
        let bar = app.navigationBars.firstMatch
        let title = bar.identifier
        let back = bar.buttons["BackButton"].exists ? bar.buttons["BackButton"] : bar.buttons.element(boundBy: 0)
        XCTAssertTrue(back.waitForExistence(timeout: 3), "No back button", file: file, line: line)

        for _ in 0..<3 {
            back.tap()
            if app.navigationBars[title].waitForNonExistence(timeout: 2) {
                return
            }
        }
        XCTFail("Could not leave \(title)", file: file, line: line)
    }

    func testOpenSpaceAuthScreens() {
        app.buttons["space.open"].tap()

        XCTAssertTrue(app.textFields["login.email"].waitForExistence(timeout: 3))
        XCTAssertFalse(app.buttons["login.submit"].isEnabled)

        app.textFields["login.email"].tap()
        app.textFields["login.email"].typeText("ana@writeopia.io")
        app.secureTextFields["login.password"].tap()
        app.secureTextFields["login.password"].typeText("secret")
        XCTAssertTrue(app.buttons["login.submit"].isEnabled)

        app.buttons["Create an account"].tap()
        // Sign-up starts by verifying the email, before any account details are asked for.
        XCTAssertTrue(app.textFields["register.email"].waitForExistence(timeout: 3))
        XCTAssertFalse(app.buttons["register.sendCode"].isEnabled)
        goBack()

        let forgot = app.buttons["Forgot password?"]
        XCTAssertTrue(forgot.waitForExistence(timeout: 3))
        forgot.tap()
        XCTAssertTrue(app.textFields["forgot.email"].waitForExistence(timeout: 3))
        goBack()

        app.buttons["Choose space"].tap()
        XCTAssertTrue(app.buttons["space.private"].waitForExistence(timeout: 3))
    }
}
