import Foundation
import Testing
@testable import WriteopiaUI
import Writeopia
import WrModels

/// The "/" commands of the editor, like the slash command popup of the Compose `TextDrawer`.
@Suite struct SlashCommandDetectorTests {
    private func typed(_ current: SlashCommandInput?, from old: String, to new: String, cursor: Int? = nil, step: String = "a") -> SlashCommandInput? {
        SlashCommandDetector.update(current, stepId: step, oldText: old, newText: new, cursor: cursor ?? new.utf16.count)
    }

    @Test func slashAtTheStartOrAfterASpaceOpensTheCommand() {
        #expect(typed(nil, from: "", to: "/") == SlashCommandInput(stepId: "a", slashIndex: 0, filter: ""))
        #expect(typed(nil, from: "Hello ", to: "Hello /") == SlashCommandInput(stepId: "a", slashIndex: 6, filter: ""))
    }

    @Test func slashInsideAWordIsNotACommand() {
        #expect(typed(nil, from: "a", to: "a/") == nil)
        #expect(typed(nil, from: "km", to: "km/h", cursor: 3) == nil)
    }

    @Test func lettersAfterTheSlashFilterAndASpaceCloses() {
        let open = typed(nil, from: "", to: "/")
        let filtered = typed(open, from: "/", to: "/to")
        #expect(filtered == SlashCommandInput(stepId: "a", slashIndex: 0, filter: "to"))
        #expect(filtered?.range == NSRange(location: 0, length: 3))

        #expect(typed(filtered, from: "/to", to: "/to ") == nil)
        #expect(typed(filtered, from: "/to", to: "/to\n") == nil)
    }

    @Test func deletingBackToTheSlashKeepsItAndDeletingTheSlashCloses() {
        let filtered = SlashCommandInput(stepId: "a", slashIndex: 6, filter: "li")
        #expect(typed(filtered, from: "Hello /li", to: "Hello /l") == SlashCommandInput(stepId: "a", slashIndex: 6, filter: "l"))
        #expect(typed(filtered, from: "Hello /li", to: "Hello /") == SlashCommandInput(stepId: "a", slashIndex: 6, filter: ""))
        #expect(typed(filtered, from: "Hello /li", to: "Hello li", cursor: 6) == nil)
    }

    @Test func typingInAnotherStepEndsTheCommand() {
        let open = SlashCommandInput(stepId: "a", slashIndex: 0, filter: "")
        #expect(typed(open, from: "x", to: "xy", step: "b") == nil)
    }

    @Test func aReplacedCharacterKeepsTheCommandWhileTheSlashStays() {
        let open = SlashCommandInput(stepId: "a", slashIndex: 0, filter: "bx")
        #expect(typed(open, from: "/bx", to: "/bo") == SlashCommandInput(stepId: "a", slashIndex: 0, filter: "bo"))
        #expect(typed(open, from: "/bx", to: "-bx") == nil)
    }
}

@Suite struct SlashCommandManagerTests {
    private func manager(text: String = "") -> WriteopiaStateManager {
        let manager = WriteopiaStateManager()
        manager.loadDocument(WrDocument(id: "d", title: "Doc", workspaceId: "w", content: [
            StoryStep(id: "t", type: .title, text: "Doc", position: Rank.at(0)),
            StoryStep(id: "a", type: .text, text: text, position: Rank.at(1)),
            StoryStep(id: "b", type: .text, text: "Second", position: Rank.at(2)),
        ]))
        return manager
    }

    private func command(_ text: String, in manager: WriteopiaStateManager) -> SlashCommand {
        manager.slashCommands.first { $0.commandText == text }!
    }

    @Test func typingASlashOpensThePopupWithEveryCommand() {
        let manager = manager()
        manager.handleTextInput("/", cursor: 1, stepId: "a")

        #expect(manager.slashCommand == SlashCommandInput(stepId: "a", slashIndex: 0, filter: ""))
        #expect(manager.showsSlashCommands(stepId: "a"))
        #expect(!manager.showsSlashCommands(stepId: "b"))
        #expect(manager.filteredSlashCommands.map(\.commandText) == ["/today", "/now", "/checkbox", "/list", "/box", "/card", "/divider"])
        // The text keeps the slash until a command runs.
        #expect(manager.step(withId: "a")?.text == "/")
    }

    @Test func theTitleHasNoCommands() {
        let manager = manager()
        manager.handleTextInput("Doc /", cursor: 5, stepId: "t")
        #expect(manager.slashCommand == nil)
    }

    @Test func theLettersTypedFilterTheCommandsAndNoMatchHidesThePopup() {
        let manager = manager()
        manager.handleTextInput("/", cursor: 1, stepId: "a")
        manager.handleTextInput("/c", cursor: 2, stepId: "a")
        #expect(manager.filteredSlashCommands.map(\.name) == ["Checkbox", "Card"])

        manager.handleTextInput("/ca", cursor: 3, stepId: "a")
        #expect(manager.filteredSlashCommands.map(\.name) == ["Card"])

        manager.handleTextInput("/caz", cursor: 4, stepId: "a")
        #expect(manager.slashCommand != nil)
        #expect(!manager.showsSlashCommands(stepId: "a"))
    }

    @Test func aLockedDocumentShowsNoPopup() {
        let manager = manager()
        manager.handleTextInput("/", cursor: 1, stepId: "a")
        manager.isEditable = false
        #expect(!manager.showsSlashCommands(stepId: "a"))
        #expect(!manager.selectHighlightedSlashCommand(stepId: "a"))
    }

    @Test func aCommandThatChangesTheTypeRemovesTheTypedCommandAndKeepsTheRest() {
        let manager = manager(text: "Buy milk ")
        manager.handleTextInput("Buy milk /", cursor: 10, stepId: "a")
        manager.handleTextInput("Buy milk /che", cursor: 13, stepId: "a")

        manager.selectSlashCommand(command("/checkbox", in: manager))

        let step = manager.step(withId: "a")
        #expect(step?.type.number == StoryType.checkItem.number)
        #expect(step?.text == "Buy milk ")
        #expect(step?.checked == false)
        #expect(manager.slashCommand == nil)
        #expect(manager.focusRequest?.stepId == "a")
        #expect(manager.focusRequest?.cursor == 9)
    }

    @Test func theDividerCommandMakesTheLineADivider() {
        let manager = manager()
        manager.handleTextInput("/", cursor: 1, stepId: "a")
        manager.handleTextInput("/div", cursor: 4, stepId: "a")
        #expect(manager.filteredSlashCommands.map(\.name) == ["Divider"])

        manager.selectSlashCommand(command("/divider", in: manager))

        let step = manager.step(withId: "a")
        #expect(step?.type.number == StoryType.divider.number)
        #expect(step?.text == "")
        #expect(manager.slashCommand == nil)
    }

    @Test func aCommandThatTogglesATagTogglesIt() {
        let manager = manager()
        manager.handleTextInput("/", cursor: 1, stepId: "a")
        manager.selectSlashCommand(command("/box", in: manager))
        #expect(manager.step(withId: "a")?.hasTag(BlockTag.box.rawValue) == true)
        #expect(manager.step(withId: "a")?.text == "")

        manager.handleTextInput("/", cursor: 1, stepId: "a")
        manager.selectSlashCommand(command("/box", in: manager))
        #expect(manager.step(withId: "a")?.hasTag(BlockTag.box.rawValue) == false)
    }

    @Test func aCommandThatInsertsTextReplacesTheTypedCommandAndPutsTheCursorAfterIt() {
        let manager = manager(text: "Done ")
        manager.handleTextInput("Done /", cursor: 6, stepId: "a")
        manager.handleTextInput("Done /tod", cursor: 9, stepId: "a")

        manager.selectSlashCommand(command("/today", in: manager))

        let today = SlashCommandDates.today()
        #expect(manager.step(withId: "a")?.text == "Done \(today)")
        #expect(manager.focusRequest?.cursor == 5 + today.utf16.count)
        #expect(manager.slashCommand == nil)
        #expect(manager.canUndo)
    }

    @Test func returnRunsTheHighlightedCommandAndTheArrowsMoveTheHighlightAroundTheEnds() {
        let manager = manager()
        manager.handleTextInput("/", cursor: 1, stepId: "a")
        #expect(manager.slashCommandHighlight == 0)

        manager.moveSlashCommandHighlight(by: -1)
        #expect(manager.slashCommandHighlight == 6)
        manager.moveSlashCommandHighlight(by: 1)
        #expect(manager.slashCommandHighlight == 0)
        manager.moveSlashCommandHighlight(by: 3)
        #expect(manager.slashCommandHighlight == 3)

        // Typing resets the highlight to the first match.
        manager.handleTextInput("/l", cursor: 2, stepId: "a")
        #expect(manager.slashCommandHighlight == 0)
        #expect(manager.filteredSlashCommands.map(\.name) == ["List"])

        #expect(manager.selectHighlightedSlashCommand(stepId: "a"))
        #expect(manager.step(withId: "a")?.type.number == StoryType.unorderedListItem.number)
        #expect(manager.step(withId: "a")?.text == "")
        // Nothing open any more: Return makes its line break.
        #expect(!manager.selectHighlightedSlashCommand(stepId: "a"))
    }

    @Test func returnOnAnotherStepIsNotTheCommand() {
        let manager = manager()
        manager.handleTextInput("/", cursor: 1, stepId: "a")
        #expect(!manager.selectHighlightedSlashCommand(stepId: "b"))
        #expect(manager.slashCommand != nil)
    }

    @Test func aLineBreakOrTheFocusOnAnotherStepClosesThePopup() {
        let manager = manager()
        manager.handleTextInput("/", cursor: 1, stepId: "a")
        manager.onFocusChange(stepId: "a", hasFocus: true)
        #expect(manager.slashCommand != nil)

        manager.onFocusChange(stepId: "b", hasFocus: true)
        #expect(manager.slashCommand == nil)

        manager.handleTextInput("/", cursor: 1, stepId: "a")
        manager.handleTextInput("/\n", cursor: 2, stepId: "a")
        #expect(manager.slashCommand == nil)
    }

    @Test func dismissingKeepsTheTypedText() {
        let manager = manager()
        manager.handleTextInput("/", cursor: 1, stepId: "a")
        manager.dismissSlashCommands()
        #expect(manager.slashCommand == nil)
        #expect(manager.step(withId: "a")?.text == "/")
    }

    @Test func theTypedCommandsOfTheLineStillWorkWithASpace() {
        // "/box " at the start of the line toggles the box as before; the popup closes with the space.
        let manager = manager()
        manager.handleTextInput("/", cursor: 1, stepId: "a")
        manager.handleTextInput("/box", cursor: 4, stepId: "a")
        #expect(manager.filteredSlashCommands.map(\.name) == ["Checkbox", "Box"])
        manager.handleTextInput("/box ", cursor: 5, stepId: "a")
        #expect(manager.slashCommand == nil)
        #expect(manager.step(withId: "a")?.hasTag(BlockTag.box.rawValue) == true)
    }

    @Test func theCaretIsRememberedForThePopup() {
        let manager = manager()
        manager.onSelectionChange(stepId: "a", start: 1, end: 1, caret: CGRect(x: 10, y: 0, width: 1, height: 20))
        #expect(manager.caretRect == CGRect(x: 10, y: 0, width: 1, height: 20))
        // No rect reported: the last one stays.
        manager.onSelectionChange(stepId: "a", start: 2, end: 2)
        #expect(manager.caretRect == CGRect(x: 10, y: 0, width: 1, height: 20))
    }

    @Test func datesAreFormattedLikeTheComposeApp() {
        let date = Date(timeIntervalSince1970: 1_700_000_000) // 2023-11-14 22:13:20 UTC
        let today = SlashCommandDates.today(date)
        #expect(!today.isEmpty)
        let now = SlashCommandDates.now(date)
        #expect(now.hasPrefix(today + " "))
        #expect(now.dropFirst(today.count + 1).count == 5) // HH:mm
    }
}
