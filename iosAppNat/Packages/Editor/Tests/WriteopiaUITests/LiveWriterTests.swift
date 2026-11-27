import Foundation
import Testing
@testable import WriteopiaUI
import Writeopia
import WrModels

@MainActor
@Suite struct LiveWriterTests {
    private func manager(_ steps: [StoryStep]) -> WriteopiaStateManager {
        let manager = WriteopiaStateManager()
        manager.loadDocument(WrDocument(id: "d", title: "", workspaceId: "w", content: steps))
        return manager
    }

    private let writer = LiveWriter(tick: .milliseconds(1))

    @Test func writesTheTitleAndTheBlocksAfterEverythingThere() async {
        let manager = manager([
            StoryStep(id: "title", type: .title, text: "", position: Rank.at(0)),
            StoryStep(id: "p1", type: .text, text: "Already here.", position: Rank.at(1)),
        ])
        let before = manager.changeCount

        await writer.write(manager, title: "Trip", blocks: LiveMarkdown.blocks("## Day one\nWe **left** early.\n---"))

        let written = manager.documentContent
        #expect(written.map(\.text) == ["Trip", "Already here.", "Day one", "We left early.", nil])
        #expect(written[0].id == "title")
        #expect(written.last?.type == .divider)
        #expect(written[3].spans == [SpanInfo(start: 3, end: 7, span: "BOLD")])
        // Saved once per block (and the title), not at every character.
        #expect(manager.changeCount - before == 4)
    }

    @Test func fillsTheEmptyParagraphAtTheEndKeepingItsId() async {
        let manager = manager([
            StoryStep(id: "title", type: .title, text: "Notes", position: Rank.at(0)),
            StoryStep(id: "empty", type: .text, text: "", position: Rank.at(1)),
        ])

        await writer.write(manager, title: nil, blocks: LiveMarkdown.blocks("First\nSecond"))

        let written = manager.documentContent
        #expect(written.map(\.text) == ["Notes", "First", "Second"])
        #expect(written[1].id == "empty")
        #expect(written[1].position == Rank.at(1))
    }

    @Test func partialStepsCutTheSpans() {
        let step = LiveMarkdown.blocks("Hello **world**")[0]

        #expect(step.partial(8).text == "Hello wo")
        #expect(step.partial(8).spans == [SpanInfo(start: 6, end: 8, span: "BOLD")])
        #expect(step.partial(3).spans.isEmpty)
    }
}
