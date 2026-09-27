import XCTest

@MainActor
final class SwipeScratchUITests: XCTestCase {
    func testScrollAndSwipe() {
        let app = XCUIApplication()
        app.launchArguments = ["-wr.spaceType", "offline", "-wr.colorTheme", "light"]
        app.launch()
        app.buttons.containing(.staticText, identifier: "Welcome to Writeopia").firstMatch.tap()
        let last = app.textViews.matching(identifier: "step.unordered_list_item").firstMatch
        XCTAssertTrue(last.waitForExistence(timeout: 5))
        last.tap()
        for i in 0..<25 { app.typeText("\\nLine \(i)") }
        app.buttons["editor.menu.bold"].firstMatch.exists ? () : ()
        // Dismiss keyboard by scrolling, then scroll to the top.
        let title = app.textViews.matching(identifier: "step.title").firstMatch
        let scroll = app.scrollViews.firstMatch
        scroll.swipeDown(); scroll.swipeDown(); sleep(1)
        let titleYBefore = title.frame.minY
        scroll.swipeUp(); sleep(1)
        let titleYAfter = title.frame.minY
        var out = "title y before=\(titleYBefore) after=\(titleYAfter)\n"

        // Swipe a paragraph sideways to select it.
        scroll.swipeDown(); scroll.swipeDown(); sleep(1)
        let paragraph = app.textViews.matching(identifier: "step.message").element(boundBy: 0)
        paragraph.swipeRight()
        sleep(1)
        out += "chip exists: \(app.buttons["editor.menu.clearSelection"].exists)\n"
        try? XCUIScreen.main.screenshot().pngRepresentation.write(to: URL(fileURLWithPath: "/private/tmp/claude-501/-Users-leandroferreira-WriteopiaProject-Writeopia/34fd5067-8e55-42b7-b858-f352dad00351/scratchpad/swipe.png"))
        try? out.write(toFile: "/private/tmp/claude-501/-Users-leandroferreira-WriteopiaProject-Writeopia/34fd5067-8e55-42b7-b858-f352dad00351/scratchpad/swipe.txt", atomically: true, encoding: .utf8)
    }
}
