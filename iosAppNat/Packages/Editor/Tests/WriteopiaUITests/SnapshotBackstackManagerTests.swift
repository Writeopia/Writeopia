import Testing
@testable import WriteopiaUI
import Writeopia
import WrModels

/// The history by itself, like `SnapshotBackstackManagerTest` of the Kotlin SDK.
@Suite struct SnapshotBackstackManagerTests {
    private func state(_ texts: String...) -> StoryState {
        var stories: [Int: StoryStep] = [:]
        for (index, text) in texts.enumerated() {
            stories[index] = StoryStep(id: "s\(index)", type: .text, text: text, position: Int(index))
        }
        return StoryState(stories: stories)
    }

    @Test func theFirstStateMakesUndoPossible() {
        let backstack = SnapshotBackstackManager()
        #expect(!backstack.canUndo)

        backstack.addTextState(state("a"), position: 0)

        #expect(backstack.canUndo)
        #expect(!backstack.canRedo)
    }

    @Test func undoAndRedoWalkTheHistoryBothWays() {
        let backstack = SnapshotBackstackManager()
        let states = (0..<3).map { state("\($0)") }
        states.forEach(backstack.addState)
        let current = state("current")

        #expect(backstack.previousState(current: current) == states[2])
        #expect(backstack.canUndo && backstack.canRedo)
        #expect(backstack.previousState(current: states[2]) == states[1])
        #expect(backstack.previousState(current: states[1]) == states[0])
        #expect(!backstack.canUndo && backstack.canRedo)
        #expect(backstack.previousState(current: states[0]) == nil)

        #expect(backstack.nextState(current: states[0]) == states[1])
        #expect(backstack.nextState(current: states[1]) == states[2])
        #expect(backstack.nextState(current: states[2]) == current)
        #expect(backstack.canUndo && !backstack.canRedo)
        #expect(backstack.nextState(current: current) == nil)
    }

    @Test func aNewEditDiscardsWhatCouldBeRedone() {
        let backstack = SnapshotBackstackManager()
        backstack.addState(state("0"))
        backstack.addState(state("1"))
        _ = backstack.previousState(current: state("2"))
        #expect(backstack.canRedo)

        backstack.addState(state("3"))

        #expect(backstack.canUndo)
        #expect(!backstack.canRedo)
    }

    @Test func typingInTheSameLineIsGrouped() {
        let backstack = SnapshotBackstackManager(textEditLimit: 3)
        let typed = (0..<8).map { state(String(repeating: "a", count: $0)) }

        // Edits 1, 4 and 7 are remembered: the first, then one in every three.
        for state in typed {
            backstack.addTextState(state, position: 0)
        }

        #expect(backstack.previousState(current: typed[7]) == typed[6])
        #expect(backstack.previousState(current: typed[6]) == typed[3])
        #expect(backstack.previousState(current: typed[3]) == typed[0])
        #expect(backstack.previousState(current: typed[0]) == nil)
    }

    @Test func typingInAnotherLineStartsANewGroup() {
        let backstack = SnapshotBackstackManager()
        backstack.addTextState(state("a", ""), position: 0)
        backstack.addTextState(state("ab", ""), position: 0)
        backstack.addTextState(state("ab", "c"), position: 1)

        #expect(backstack.previousState(current: state("ab", "cd")) == state("ab", "c"))
        #expect(backstack.previousState(current: state("ab", "c")) == state("a", ""))
    }

    @Test func anEditAfterAnUndoStartsANewGroup() {
        let backstack = SnapshotBackstackManager()
        backstack.addTextState(state("a"), position: 0)
        backstack.addTextState(state("ab"), position: 0)
        let restored = backstack.previousState(current: state("abc"))
        #expect(restored == state("a"))

        backstack.addTextState(state("a"), position: 0)

        #expect(backstack.previousState(current: state("ax")) == state("a"))
    }

    @Test func statesEqualToTheLastOneAreNotRepeated() {
        let backstack = SnapshotBackstackManager()
        backstack.addState(state("a"))
        backstack.addState(state("a"))

        #expect(backstack.previousState(current: state("b")) == state("a"))
        #expect(!backstack.canUndo)
    }

    @Test func snapshotsEqualToTheCurrentDocumentAreSkipped() {
        let backstack = SnapshotBackstackManager()
        backstack.addState(state("a"))
        backstack.addState(state("b"))

        // The last edit was reverted by other means; undo goes straight to the one before.
        #expect(backstack.previousState(current: state("b")) == state("a"))
        #expect(backstack.nextState(current: state("a")) == state("b"))
    }

    @Test func theHistoryIsCapped() {
        let backstack = SnapshotBackstackManager(limit: 2)
        backstack.addState(state("0"))
        backstack.addState(state("1"))
        backstack.addState(state("2"))

        #expect(backstack.previousState(current: state("3")) == state("2"))
        #expect(backstack.previousState(current: state("2")) == state("1"))
        #expect(!backstack.canUndo)
    }

    @Test func clearForgetsEverything() {
        let backstack = SnapshotBackstackManager()
        backstack.addState(state("0"))
        _ = backstack.previousState(current: state("1"))

        backstack.clear()

        #expect(!backstack.canUndo)
        #expect(!backstack.canRedo)
    }
}
