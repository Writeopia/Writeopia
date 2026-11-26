import Foundation
import Testing
@testable import WriteopiaUI
import Writeopia
import WrModels

@MainActor
@Suite struct AiModeTests {
    private func manager(_ steps: [StoryStep]) -> WriteopiaStateManager {
        let manager = WriteopiaStateManager()
        manager.loadDocument(WrDocument(id: "d", title: "", workspaceId: "w", content: steps))
        return manager
    }

    @Test func aiModeIsOnWhileWritingWaitsBetweenCallsThenEnds() async throws {
        let editors = LiveEditors()
        editors.idleTimeout = .milliseconds(50)

        let ticket = try #require(editors.beginWriting())
        #expect(editors.mode == .writing)

        editors.endWriting(ticket)
        #expect(editors.mode == .waiting)

        // Polled rather than one fixed sleep, so a busy machine doesn't fail the test.
        let deadline = ContinuousClock.now + .seconds(3)
        while editors.mode != .off, ContinuousClock.now < deadline {
            try await Task.sleep(for: .milliseconds(20))
        }
        #expect(editors.mode == .off)
    }

    @Test func stoppingEndsAiModeAndRefusesTheNextCallsForAWhile() throws {
        let editors = LiveEditors()
        let ticket = try #require(editors.beginWriting())

        editors.stop()

        #expect(editors.mode == .off)
        #expect(editors.isStopped(ticket))
        editors.endWriting(ticket)
        #expect(editors.mode == .off)
        #expect(editors.beginWriting() == nil)

        editors.stopCooldown = .zero
        #expect(editors.beginWriting() != nil)
    }

    @Test func editorsTakeNoInputInAiMode() throws {
        let editors = LiveEditors()
        let manager = manager([StoryStep(id: "t", type: .title, text: "Notes", position: Rank.at(0))])
        editors.attach(documentId: "d", manager: manager)

        let ticket = try #require(editors.beginWriting())
        #expect(manager.isInputLocked)
        editors.endWriting(ticket)
        #expect(manager.isInputLocked)

        editors.stop()
        #expect(!manager.isInputLocked)
    }

    @Test func aStopInTheMiddleOfTypingKeepsAndSavesWhatWasTyped() async {
        let manager = manager([StoryStep(id: "t", type: .title, text: "Notes", position: Rank.at(0))])
        var checks = 0
        let before = manager.changeCount

        let done = await LiveWriter(tick: .milliseconds(1), minCharsPerTick: 3).write(
            manager,
            title: nil,
            blocks: LiveMarkdown.blocks("Hello world, this is long\nNever written")
        ) {
            checks += 1
            return checks > 3
        }

        #expect(!done)
        // One check before the block, then one per tick of 3 characters: the 4th stops at "Hello ".
        #expect(manager.documentContent.map(\.text) == ["Notes", "Hello "])
        #expect(manager.changeCount > before)
    }

    @Test func finishEndsAiModeAtOnceAndTheNextCallCanWrite() throws {
        let editors = LiveEditors()
        let manager = manager([StoryStep(id: "t", type: .title, text: "Notes", position: Rank.at(0))])
        editors.attach(documentId: "d", manager: manager)
        let ticket = try #require(editors.beginWriting())
        editors.endWriting(ticket)

        editors.finish()

        #expect(editors.mode == .off)
        #expect(!manager.isInputLocked)
        #expect(editors.beginWriting() != nil)
    }
}
