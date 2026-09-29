import Foundation
import Testing
@testable import WriteopiaUI
import Writeopia
import WrModels

private func document(_ steps: [StoryStep]) -> WrDocument {
    WrDocument(id: "d", title: "Doc", workspaceId: "w", content: steps)
}

private let sample = document([
    StoryStep(id: "t", type: .title, text: "Doc", position: 0),
    StoryStep(id: "a", type: .text, text: "Hello", position: 1),
    StoryStep(id: "b", type: .checkItem, text: "Task", checked: false, position: 2),
    StoryStep(id: "c", type: .divider, position: 3),
])

@Suite struct StepsModifierTests {
    @Test func interleavesSpacesAndEndsWithLastSpace() {
        let manager = WriteopiaStateManager()
        manager.loadDocument(sample)

        let types = manager.toDraw.map(\.storyStep.type.number)
        #expect(types == [
            StoryType.title.number,
            StoryType.space.number, StoryType.text.number,
            StoryType.space.number, StoryType.checkItem.number,
            StoryType.space.number, StoryType.divider.number,
            StoryType.lastSpace.number,
        ])
        // A space points to the step before it: that's where a drop lands.
        #expect(manager.toDraw[1].position == 0)
        #expect(manager.toDraw.last?.position == 3)
    }

    @Test func hoveredSpaceBecomesOnDragSpace() {
        let manager = WriteopiaStateManager()
        manager.loadDocument(sample)

        manager.onDragHover(1)

        let dragSpaces = manager.toDraw.filter { $0.storyStep.type.number == StoryType.onDragSpace.number }
        #expect(dragSpaces.map(\.position) == [1])
        // Same identity as the plain space, so SwiftUI keeps the view.
        #expect(dragSpaces.first?.id == "space-b")
    }

    @Test func unsupportedStepsAreKeptButNotDrawn() {
        let manager = WriteopiaStateManager()
        manager.loadDocument(document([
            StoryStep(id: "t", type: .title, text: "Doc", position: 0),
            StoryStep(id: "sheet", type: StoryType(name: "spreadsheet", number: 101), position: 1),
        ]))

        #expect(!manager.toDraw.contains { $0.id == "sheet" })
        #expect(manager.documentContent.map(\.id) == ["t", "sheet"])
    }
}

@Suite struct WriteopiaStateManagerTests {
    @Test func typingUpdatesTheStepAndKeepsSpans() {
        let manager = WriteopiaStateManager()
        manager.loadDocument(document([
            StoryStep(id: "t", type: .title, text: "Doc", position: 0),
            StoryStep(id: "a", type: .text, text: "Hello world", spans: [SpanInfo(start: 6, end: 11, span: "BOLD")], position: 1),
        ]))

        manager.handleTextInput("Hi, Hello world", cursor: 4, stepId: "a")

        let step = manager.currentStory.stories[1]
        #expect(step?.text == "Hi, Hello world")
        #expect(step?.spans == [SpanInfo(start: 10, end: 15, span: "BOLD")])
        #expect(manager.focusRequest == nil)
    }

    @Test func returnSplitsAndRequestsFocusOnNewLine() throws {
        let manager = WriteopiaStateManager()
        manager.loadDocument(sample)

        manager.handleTextInput("Hel\nlo", cursor: 4, stepId: "a")

        #expect(manager.currentStory.sortedStories.map { $0.text ?? "-" } == ["Doc", "Hel", "lo", "Task", "-"])
        let request = try #require(manager.focusRequest)
        #expect(request.stepId == manager.currentStory.stories[2]?.id)
        #expect(request.cursor == 0)
    }

    @Test func backspaceAtStartMergesWithPreviousLine() throws {
        let manager = WriteopiaStateManager()
        manager.loadDocument(document([
            StoryStep(id: "t", type: .title, text: "Doc", position: 0),
            StoryStep(id: "a", type: .text, text: "One", position: 1),
            StoryStep(id: "b", type: .text, text: "Two", position: 2),
        ]))

        manager.onErase(stepId: "b")

        #expect(manager.currentStory.sortedStories.map(\.text) == ["Doc", "OneTwo"])
        let request = try #require(manager.focusRequest)
        #expect(request.stepId == "a")
        #expect(request.cursor == 3)
    }

    @Test func checkboxToggles() {
        let manager = WriteopiaStateManager()
        manager.loadDocument(sample)

        manager.onCheckedChange(stepId: "b", checked: true)

        #expect(manager.currentStory.stories[2]?.checked == true)
    }

    @Test func dropMovesStepAndClearsDrag() {
        let manager = WriteopiaStateManager()
        manager.loadDocument(sample)
        let payload = manager.dragPayload(for: manager.currentStory.stories[3]!)
        manager.onDragHover(0)

        #expect(manager.moveRequest(payload: payload, after: 0))

        #expect(manager.currentStory.sortedStories.map(\.id) == ["t", "c", "a", "b"])
        #expect(manager.dragPosition == nil)
        #expect(manager.currentStory.focus == nil)
        #expect(!manager.moveRequest(payload: "text dragged from another app", after: 0))
    }

    @Test func tapAtTheEndAddsAParagraph() {
        let manager = WriteopiaStateManager()
        manager.loadDocument(sample)

        manager.clickAtTheEnd()

        let last = manager.currentStory.sortedStories.last
        #expect(last?.type.number == StoryType.text.number)
        #expect(manager.focusRequest?.stepId == last?.id)
    }

    @Test func notEditableIgnoresInput() {
        let manager = WriteopiaStateManager()
        manager.loadDocument(sample)
        manager.isEditable = false

        manager.handleTextInput("Changed", cursor: 7, stepId: "a")
        manager.onErase(stepId: "a")

        #expect(manager.currentStory.stories[1]?.text == "Hello")
    }
}

@Suite struct BoldTests {
    @Test func boldTogglesOnTheSelection() {
        let manager = WriteopiaStateManager()
        manager.loadDocument(sample)

        manager.onSelectionChange(stepId: "a", start: 1, end: 4)
        #expect(!manager.isSpanActive(.bold))

        manager.toggleSpan(.bold)
        #expect(manager.currentStory.stories[1]?.spans == [SpanInfo(start: 1, end: 4, span: "BOLD")])
        #expect(manager.isSpanActive(.bold))

        manager.toggleSpan(.bold)
        #expect(manager.currentStory.stories[1]?.spans.isEmpty == true)
    }

    @Test func boldWithoutSelectionDoesNothing() {
        let manager = WriteopiaStateManager()
        manager.loadDocument(sample)

        manager.onSelectionChange(stepId: "a", start: 2, end: 2)
        manager.toggleSpan(.bold)

        #expect(manager.currentStory.stories[1]?.spans.isEmpty == true)
    }

    @Test func selectionIsClearedWhenTheStepLosesFocus() {
        let manager = WriteopiaStateManager()
        manager.loadDocument(sample)
        manager.onFocusChange(stepId: "a", hasFocus: true)
        manager.onSelectionChange(stepId: "a", start: 0, end: 5)

        manager.onFocusChange(stepId: "a", hasFocus: false)

        #expect(manager.textSelection == nil)
    }
}

@Suite struct LineSelectionTests {
    private func manager() -> WriteopiaStateManager {
        let manager = WriteopiaStateManager()
        manager.loadDocument(document([
            StoryStep(id: "t", type: .title, text: "Doc", position: 0),
            StoryStep(id: "a", type: .text, text: "One", position: 1),
            StoryStep(id: "b", type: .text, text: "Two", spans: [SpanInfo(start: 0, end: 3, span: "BOLD")], position: 2),
            StoryStep(id: "c", type: .divider, position: 3),
        ]))
        return manager
    }

    @Test func slidingSelectsSeveralLinesButNotTheTitle() {
        let manager = manager()

        manager.toggleLineSelection(stepId: "a")
        manager.toggleLineSelection(stepId: "b")
        manager.toggleLineSelection(stepId: "t")

        #expect(manager.selectedPositions == [1, 2])
        manager.toggleLineSelection(stepId: "a")
        #expect(manager.selectedPositions == [2])
    }

    @Test func spansApplyToAllSelectedLines() {
        let manager = manager()
        manager.onSelected(stepId: "a", isSelected: true)
        manager.onSelected(stepId: "b", isSelected: true)
        manager.onSelected(stepId: "c", isSelected: true)

        // "Two" is already bold but "One" isn't, so bold is added to both.
        #expect(!manager.isSpanActive(.bold))
        manager.toggleSpan(.bold)
        #expect(manager.currentStory.stories[1]?.spans == [SpanInfo(start: 0, end: 3, span: "BOLD")])
        #expect(manager.currentStory.stories[2]?.spans == [SpanInfo(start: 0, end: 3, span: "BOLD")])
        #expect(manager.isSpanActive(.bold))

        manager.toggleSpan(.bold)
        #expect(manager.currentStory.stories[1]?.spans.isEmpty == true)
        #expect(manager.currentStory.stories[2]?.spans.isEmpty == true)
    }

    @Test func selectionFollowsTheStepsWhenTheyMove() {
        let manager = manager()
        manager.onSelected(stepId: "b", isSelected: true)

        manager.moveRequest(payload: manager.dragPayload(for: manager.currentStory.stories[2]!), after: 0)

        #expect(manager.selectedPositions == [1])
        manager.clearLineSelection()
        #expect(!manager.hasSelectedLines)
    }
}

@Suite struct BlockOptionsTests {
    private func manager() -> WriteopiaStateManager {
        let manager = WriteopiaStateManager()
        manager.loadDocument(document([
            StoryStep(id: "t", type: .title, text: "Doc", position: 0),
            StoryStep(id: "a", type: .text, text: "One", position: 1),
            StoryStep(id: "b", type: .text, text: "Two", position: 2),
        ]))
        return manager
    }

    @Test func actOnTheFocusedLineWithoutSelection() {
        let manager = manager()
        manager.onFocusChange(stepId: "b", hasFocus: true)

        manager.toggleType(.checkItem)
        #expect(manager.currentStory.stories[2]?.type.number == StoryType.checkItem.number)
        #expect(manager.currentStory.stories[1]?.type.number == StoryType.text.number)
        #expect(manager.blocksAre(.checkItem))

        manager.toggleHeading(.h1)
        #expect(manager.blocksHave(.h1))
    }

    @Test func selectedLinesWinOverTheFocusedLine() {
        let manager = manager()
        manager.onFocusChange(stepId: "b", hasFocus: true)
        manager.onSelected(stepId: "a", isSelected: true)

        manager.toggleType(.unorderedListItem)
        #expect(manager.currentStory.stories[1]?.type.number == StoryType.unorderedListItem.number)
        #expect(manager.currentStory.stories[2]?.type.number == StoryType.text.number)
    }

    @Test func nothingHappensOnTheTitleOrWithoutFocus() {
        let manager = manager()
        manager.toggleType(.checkItem)
        #expect(manager.changeCount == 0)

        manager.onFocusChange(stepId: "t", hasFocus: true)
        manager.toggleType(.checkItem)
        #expect(manager.changeCount == 0)
        #expect(!manager.blocksAre(.checkItem))
    }
}

@Suite struct ImageStepTests {
    private func manager() -> WriteopiaStateManager {
        let manager = WriteopiaStateManager()
        manager.loadDocument(document([
            StoryStep(id: "t", type: .title, text: "Doc", position: 0),
            StoryStep(id: "a", type: .text, text: "Text", position: 1),
            StoryStep(id: "e", type: .text, text: "", position: 2),
        ]))
        return manager
    }

    @Test func imageGoesAfterTheTitle() {
        let manager = manager()
        manager.onFocusChange(stepId: "t", hasFocus: true)

        manager.addImage(path: "/tmp/a.jpg")

        #expect(manager.currentStory.sortedStories.map(\.type.number) == [11, 2, 0, 0])
    }

    @Test func imageReplacesAnEmptyLine() {
        let manager = manager()
        manager.onFocusChange(stepId: "e", hasFocus: true)

        manager.addImage(path: "/tmp/a.jpg")

        #expect(manager.currentStory.sortedStories.map(\.type.number) == [11, 0, 2])
        #expect(manager.currentStory.stories[2]?.path == "/tmp/a.jpg")
    }

    @Test func imageGoesAfterALineWithText() {
        let manager = manager()
        manager.onFocusChange(stepId: "a", hasFocus: true)

        manager.addImage(path: "/tmp/a.jpg")

        #expect(manager.currentStory.sortedStories.map(\.id)[1] == "a")
        #expect(manager.currentStory.stories[2]?.type.number == StoryType.image.number)
    }

    @Test func withoutFocusTheImageGoesAtTheEnd() {
        let manager = manager()

        manager.addImage(path: "/tmp/a.jpg")

        #expect(manager.currentStory.sortedStories.last?.type.number == StoryType.image.number)
        #expect(manager.toDraw.contains { $0.storyStep.type.number == StoryType.image.number })
    }

    @Test func uploadSwapsThePathForTheUrl() throws {
        let manager = manager()
        let id = try #require(manager.addImage(path: "/tmp/a.jpg", uploading: true))
        #expect(manager.uploadingStepIds.contains(id))

        manager.imageUploadFinished(stepId: id, url: "https://cdn/x.jpg")

        #expect(manager.step(withId: id)?.url == "https://cdn/x.jpg")
        #expect(manager.step(withId: id)?.path == nil)
        #expect(manager.uploadingStepIds.isEmpty)
    }

    @Test func failedUploadKeepsTheLocalImage() throws {
        let manager = manager()
        let id = try #require(manager.addImage(path: "/tmp/a.jpg", uploading: true))

        manager.imageUploadFinished(stepId: id, url: nil)

        #expect(manager.step(withId: id)?.path == "/tmp/a.jpg")
        #expect(manager.uploadingStepIds.isEmpty)
    }
}

@Suite struct TextCommandTests {
    private func manager() -> WriteopiaStateManager {
        let manager = WriteopiaStateManager()
        manager.loadDocument(document([
            StoryStep(id: "t", type: .title, text: "Doc", position: 0),
            StoryStep(id: "a", type: .text, text: "", position: 1),
        ]))
        return manager
    }

    @Test func headingCommandsTagTheLineAndVanish() {
        let manager = manager()
        manager.handleTextInput("### Heading", cursor: 11, stepId: "a")

        let step = manager.step(withId: "a")
        #expect(step?.text == "Heading")
        #expect(step?.type == .text)
        #expect(step?.headingLevel == 3)
        #expect(manager.focusRequest?.stepId == "a")
        #expect(manager.focusRequest?.cursor == 7)

        // Another level replaces the first.
        manager.handleTextInput("# Heading", cursor: 2, stepId: "a")
        #expect(manager.step(withId: "a")?.headingLevel == 1)
        #expect(manager.step(withId: "a")?.tags.count == 1)
    }

    @Test func typeCommandsChangeTheStep() {
        let manager = manager()
        manager.handleTextInput("[] Buy milk", cursor: 3, stepId: "a")
        #expect(manager.step(withId: "a")?.type == .checkItem)
        #expect(manager.step(withId: "a")?.checked == false)
        #expect(manager.step(withId: "a")?.text == "Buy milk")
        #expect(manager.focusRequest?.cursor == 0)

        manager.handleTextInput("- item", cursor: 6, stepId: "a")
        #expect(manager.step(withId: "a")?.type == .unorderedListItem)
        #expect(manager.step(withId: "a")?.checked == nil)
        #expect(manager.step(withId: "a")?.text == "item")

        manager.handleTextInput("``` ", cursor: 4, stepId: "a")
        #expect(manager.step(withId: "a")?.type == .codeBlock)
        #expect(manager.step(withId: "a")?.text == "")

        manager.handleTextInput("--- ", cursor: 4, stepId: "a")
        #expect(manager.step(withId: "a")?.type == .divider)
    }

    @Test func boxAndCardToggleTheirTags() {
        let manager = manager()
        manager.handleTextInput("/box text", cursor: 4, stepId: "a")
        #expect(manager.step(withId: "a")?.hasTag(BlockTag.box.rawValue) == true)
        #expect(manager.step(withId: "a")?.text == "text")

        manager.handleTextInput("/box text", cursor: 4, stepId: "a")
        #expect(manager.step(withId: "a")?.hasTag(BlockTag.box.rawValue) == false)

        manager.handleTextInput("/card ", cursor: 6, stepId: "a")
        #expect(manager.step(withId: "a")?.hasTag(BlockTag.card.rawValue) == true)
    }

    @Test func commandsNeedTheSpaceAndTheStartOfTheLineAndSkipTheTitle() {
        let manager = manager()
        // Without the space nothing happens, so longer commands can still be typed.
        for typed in ["#", "##", "-", "--", "---", "[]", "```", "/box"] {
            manager.handleTextInput(typed, cursor: typed.count, stepId: "a")
            #expect(manager.step(withId: "a")?.type == .text, "\(typed) alone is plain text")
            #expect(manager.step(withId: "a")?.text == typed)
            #expect(manager.step(withId: "a")?.tags.isEmpty == true)
        }

        manager.handleTextInput("a - b", cursor: 5, stepId: "a")
        #expect(manager.step(withId: "a")?.type == .text)
        #expect(manager.step(withId: "a")?.text == "a - b")

        manager.handleTextInput("#hashtag ", cursor: 9, stepId: "a")
        #expect(manager.step(withId: "a")?.headingLevel == nil)

        manager.handleTextInput("# Doc", cursor: 2, stepId: "t")
        #expect(manager.step(withId: "t")?.text == "# Doc")
        #expect(manager.step(withId: "t")?.type == .title)
    }
}

@Suite struct KeyboardNavigationTests {
    private func manager() -> WriteopiaStateManager {
        let manager = WriteopiaStateManager()
        manager.loadDocument(document([
            StoryStep(id: "t", type: .title, text: "Doc", position: 0),
            StoryStep(id: "a", type: .text, text: "First line", position: 1),
            StoryStep(id: "d", type: .divider, position: 2),
            StoryStep(id: "b", type: .checkItem, text: "Second", position: 3),
            StoryStep(id: "c", type: .text, text: "Third line here", position: 4),
        ]))
        return manager
    }

    @Test func arrowsMoveTheCursorToTheTextStepsAroundKeepingTheColumn() {
        let manager = manager()

        manager.focusNext(stepId: "a", cursor: 4)
        #expect(manager.focusRequest?.stepId == "b")
        #expect(manager.focusRequest?.cursor == 4)

        manager.focusNext(stepId: "c", cursor: 12)
        #expect(manager.focusRequest?.stepId == "b") // nothing below: unchanged

        // The column is capped at the length of the shorter line.
        manager.focusPrevious(stepId: "c", cursor: 12)
        #expect(manager.focusRequest?.stepId == "b")
        #expect(manager.focusRequest?.cursor == 6)

        // The divider is skipped; the title is a text step and gets the focus.
        manager.focusPrevious(stepId: "a", cursor: 0)
        #expect(manager.focusRequest?.stepId == "t")
    }

    @Test func selectionGrowsWithShiftArrowsAndSelectAllSkipsTheTitle() {
        let manager = manager()
        manager.extendLineSelection(up: false)
        #expect(!manager.hasSelectedLines) // needs a selection to grow

        manager.toggleLineSelection(stepId: "b")
        manager.extendLineSelection(up: false)
        #expect(manager.selectedStepIds == ["b", "c"])
        manager.extendLineSelection(up: false)
        #expect(manager.selectedStepIds == ["b", "c"]) // nothing below
        manager.extendLineSelection(up: true)
        #expect(manager.selectedStepIds == ["b", "c", "d"])
        manager.extendLineSelection(up: true)
        #expect(manager.selectedStepIds == ["a", "b", "c", "d"])
        manager.extendLineSelection(up: true)
        #expect(manager.selectedStepIds == ["a", "b", "c", "d"]) // the title is never selected

        manager.clearLineSelection()
        manager.selectAllLines()
        #expect(manager.selectedStepIds == ["a", "b", "c", "d"])
    }
}

#if canImport(UIKit)
@Suite struct ReorderDragTests {
    /// Title and three lines, each 40pt tall, stacked from y = 0.
    private func setUp() -> (WriteopiaStateManager, ReorderCoordinator) {
        let manager = WriteopiaStateManager()
        manager.loadDocument(document([
            StoryStep(id: "t", type: .title, text: "Doc", position: 0),
            StoryStep(id: "a", type: .text, text: "A", position: 1),
            StoryStep(id: "b", type: .text, text: "B", position: 2),
            StoryStep(id: "c", type: .text, text: "C", position: 3),
        ]))
        let reorder = ReorderCoordinator()
        reorder.manager = manager
        for (index, id) in ["t", "a", "b", "c"].enumerated() {
            reorder.setFrame(CGRect(x: 0, y: Double(index) * 40, width: 300, height: 40), for: id)
        }
        return (manager, reorder)
    }

    private func drag(_ id: String, y: Double) -> ReorderCoordinator.ActiveDrag {
        ReorderCoordinator.ActiveDrag(stepId: id, location: CGPoint(x: 100, y: y))
    }

    @Test func lowerHalfDropsBelowTheStep() {
        let (manager, reorder) = setUp()
        defer { withExtendedLifetime(manager) {} }
        // Over the lower half of "b": after b.
        #expect(reorder.dropPosition(for: drag("a", y: 110)) == 2)
    }

    @Test func upperHalfDropsAboveTheStep() {
        let (manager, reorder) = setUp()
        defer { withExtendedLifetime(manager) {} }
        // Over the upper half of "c": after b, so above c.
        #expect(reorder.dropPosition(for: drag("a", y: 125)) == 2)
        // Over the upper half of "b" while dragging "c": after a.
        #expect(reorder.dropPosition(for: drag("c", y: 85)) == 1)
    }

    @Test func nothingGoesAboveTheTitle() {
        let (manager, reorder) = setUp()
        defer { withExtendedLifetime(manager) {} }
        #expect(reorder.dropPosition(for: drag("c", y: 5)) == 0)
    }

    @Test func belowEverythingDropsAtTheEnd() {
        let (manager, reorder) = setUp()
        defer { withExtendedLifetime(manager) {} }
        #expect(reorder.dropPosition(for: drag("a", y: 500)) == 3)
    }

    @Test func dropsThatWouldNotMoveShowNoFeedback() {
        let (manager, reorder) = setUp()
        defer { withExtendedLifetime(manager) {} }
        // "b" over the lower half of the step above it, its own lower half, or the upper
        // half of the step below it: it would land where it already is.
        #expect(reorder.dropPosition(for: drag("b", y: 70)) == nil)
        #expect(reorder.dropPosition(for: drag("b", y: 110)) == nil)
        #expect(reorder.dropPosition(for: drag("b", y: 125)) == nil)
        // Over the upper half of "a" it really moves above "a".
        #expect(reorder.dropPosition(for: drag("b", y: 50)) == 0)
    }

    @Test func releasingMovesTheStepAndClearsTheFeedback() {
        let (manager, reorder) = setUp()

        reorder.dragChanged(stepId: "a", location: CGPoint(x: 100, y: 150))
        #expect(manager.dragPosition == 3)
        #expect(manager.toDraw.contains { $0.storyStep.type.number == StoryType.onDragSpace.number })

        reorder.dragEnded()

        #expect(manager.currentStory.sortedStories.map(\.id) == ["t", "b", "c", "a"])
        #expect(manager.dragPosition == nil)
        #expect(reorder.active == nil)
    }
}
#endif
