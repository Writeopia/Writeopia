import Testing
@testable import WriteopiaUI
import Writeopia
import WrModels

private func loadedManager() -> WriteopiaStateManager {
    let manager = WriteopiaStateManager()
    manager.loadDocument(WrDocument(id: "d", title: "Doc", workspaceId: "w", content: [
        StoryStep(id: "t", type: .title, text: "Doc", position: 0),
        StoryStep(id: "a", type: .text, text: "Hello", position: 1),
        StoryStep(id: "b", type: .checkItem, text: "Task", checked: false, position: 2),
    ]))
    return manager
}

/// Undo and redo through the state manager, like the undo tests of `WriteopiaStateManagerTest`
/// in the Kotlin SDK.
@Suite struct UndoRedoStateManagerTests {
    @Test func aLoadedDocumentHasNoHistory() {
        let manager = loadedManager()
        #expect(!manager.canUndo)
        #expect(!manager.canRedo)

        manager.undo()
        #expect(manager.step(withId: "a")?.text == "Hello")
    }

    @Test func aCheckCanBeUndoneAndRedone() {
        let manager = loadedManager()
        let changes = manager.changeCount

        manager.onCheckedChange(stepId: "b", checked: true)
        #expect(manager.canUndo)
        #expect(!manager.canRedo)

        manager.undo()
        #expect(manager.step(withId: "b")?.checked == false)
        #expect(!manager.canUndo)
        #expect(manager.canRedo)
        #expect(manager.changeCount == changes + 2, "an undo is a change to save")

        manager.redo()
        #expect(manager.step(withId: "b")?.checked == true)
        #expect(manager.canUndo)
        #expect(!manager.canRedo)
    }

    @Test func typingIsUndoneInGroupsAndTheCursorGoesBack() {
        let manager = loadedManager()
        var text = "Hello"
        for character in " world, how are you" {
            text.append(character)
            manager.handleTextInput(text, cursor: text.utf16.count, stepId: "a")
        }
        #expect(manager.step(withId: "a")?.text == "Hello world, how are you")

        manager.undo()
        let afterUndo = manager.step(withId: "a")?.text ?? ""
        #expect(afterUndo.hasPrefix("Hello"))
        #expect(afterUndo.count < "Hello world, how are you".count)
        #expect(afterUndo.count > "Hello".count, "typing is undone a few characters at a time")
        #expect(manager.focusRequest?.stepId == "a")
        #expect(manager.focusRequest?.cursor == afterUndo.utf16.count)

        manager.undo()
        manager.undo()
        manager.undo()
        #expect(manager.step(withId: "a")?.text == "Hello")
        #expect(!manager.canUndo)

        manager.redo()
        #expect(manager.step(withId: "a")?.text.map { $0.count > "Hello".count } == true)
    }

    @Test func aDeletedLineComesBackWithUndo() {
        let manager = loadedManager()
        manager.onSelected(stepId: "a", isSelected: true)
        manager.deleteSelectedLines()
        #expect(manager.step(withId: "a") == nil)

        manager.undo()

        #expect(manager.step(withId: "a")?.text == "Hello")
        #expect(manager.documentContent.map(\.id) == ["t", "a", "b"])
        #expect(!manager.hasSelectedLines)
    }

    @Test func aLineBreakAndItsUndo() {
        let manager = loadedManager()
        manager.handleTextInput("Hel\nlo", cursor: 4, stepId: "a")
        #expect(manager.documentContent.count == 4)

        manager.undo()
        #expect(manager.documentContent.count == 3)
        #expect(manager.step(withId: "a")?.text == "Hello")

        manager.redo()
        #expect(manager.documentContent.count == 4)
        #expect(manager.step(withId: "a")?.text == "Hel")
    }

    @Test func aNewEditAfterUndoDropsTheRedo() {
        let manager = loadedManager()
        manager.onCheckedChange(stepId: "b", checked: true)
        manager.undo()
        #expect(manager.canRedo)

        manager.toggleType(.unorderedListItem)
        manager.handleTextInput("Hello!", cursor: 6, stepId: "a")

        #expect(!manager.canRedo)
        manager.undo()
        #expect(manager.step(withId: "a")?.text == "Hello")
    }

    @Test func aLockedDocumentDoesNotUndo() {
        let manager = loadedManager()
        manager.onCheckedChange(stepId: "b", checked: true)
        manager.isEditable = false

        manager.undo()

        #expect(manager.step(withId: "b")?.checked == true)
    }

    @Test func loadingAnotherDocumentForgetsTheHistory() {
        let manager = loadedManager()
        manager.onCheckedChange(stepId: "b", checked: true)
        #expect(manager.canUndo)

        let other = WriteopiaStateManager()
        other.loadDocument(WrDocument(id: "e", title: "Other", workspaceId: "w", content: [
            StoryStep(id: "t2", type: .title, text: "Other", position: 0),
        ]))
        #expect(!other.canUndo)

        manager.newDocument()
        #expect(!manager.canUndo)
    }

    @Test func undoAfterARejectedAiAnswerGoesBackToTheEditBefore() {
        let manager = loadedManager()
        manager.onCheckedChange(stepId: "b", checked: true)
        let loadingId = manager.loadingAtPosition(2)
        manager.showAiAnswer("An answer", stepId: loadingId)
        manager.removeStep(stepId: loadingId)
        #expect(manager.documentContent.count == 3)

        manager.undo()

        #expect(manager.step(withId: "b")?.checked == false)
        #expect(manager.documentContent.count == 3)
    }

    @Test func undoAfterAnAcceptedAiAnswerRemovesIt() {
        let manager = loadedManager()
        let loadingId = manager.loadingAtPosition(2)
        manager.showAiAnswer("Line one\nLine two", stepId: loadingId)
        manager.aiAnswerFinished(stepId: loadingId)
        manager.acceptAiAnswer(stepId: loadingId)
        #expect(manager.documentContent.count == 5)

        manager.undo()

        #expect(manager.documentContent.map(\.id) == ["t", "a", "b"])
    }
}
