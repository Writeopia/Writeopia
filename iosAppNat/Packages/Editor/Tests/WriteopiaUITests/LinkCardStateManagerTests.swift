import Testing
@testable import WriteopiaUI
import Writeopia
import WrModels

private func manager(lines: [String?]) -> WriteopiaStateManager {
    var steps = [StoryStep(id: "t", type: .title, text: "Title", position: 0)]
    for (index, text) in lines.enumerated() {
        steps.append(StoryStep(id: "l\(index)", type: .text, text: text, position: Double(index + 1)))
    }
    let manager = WriteopiaStateManager()
    manager.loadDocument(WrDocument(id: "d", title: "Title", workspaceId: "w", content: steps))
    return manager
}

@Suite struct LinkCardStateManagerTests {
    @Test func pastingALinkInAnEmptyLineCreatesACardAndTellsTheApp() {
        let manager = manager(lines: [nil])
        var created: [StoryStep] = []
        manager.onLinkCardCreated = { created.append($0) }

        manager.handleTextInput("https://writeopia.io", cursor: 20, stepId: "l0")

        let card = manager.step(withId: "l0")!
        #expect(card.type == .linkCard)
        #expect(card.url == "https://writeopia.io")
        #expect(card.text == nil)
        #expect(created.map(\.id) == ["l0"])
        #expect(manager.describingLinkStepIds == ["l0"])

        // The card can not be typed in: an empty line below it has the cursor.
        #expect(manager.documentContent.count == 3)
        #expect(manager.focusRequest?.stepId == manager.documentContent[2].id)
    }

    @Test func pastingALinkInALineWithTextKeepsItAsText() {
        let manager = manager(lines: ["See "])

        manager.handleTextInput("See https://writeopia.io", cursor: 24, stepId: "l0")

        #expect(manager.step(withId: "l0")?.type == .text)
        #expect(manager.step(withId: "l0")?.text == "See https://writeopia.io")
    }

    @Test func pastingALinkInTheTitleKeepsTheTitle() {
        let manager = manager(lines: [])

        manager.handleTextInput("https://writeopia.io", cursor: 20, stepId: "t")

        #expect(manager.step(withId: "t")?.type == .title)
        #expect(manager.step(withId: "t")?.text == "https://writeopia.io")
    }

    @Test func theDescriptionFromTheBackendLandsInTheCard() {
        let manager = manager(lines: [nil])
        manager.handleTextInput("https://writeopia.io", cursor: 20, stepId: "l0")

        manager.linkCardDescribed(stepId: "l0", description: "A text editor for everyone.")

        let card = manager.step(withId: "l0")!
        #expect(card.type == .linkCard)
        #expect(card.text == "A text editor for everyone.")
        #expect(card.url == "https://writeopia.io")
        #expect(manager.describingLinkStepIds.isEmpty)
    }

    @Test func aFailedDescriptionLeavesTheCardAsItIs() {
        let manager = manager(lines: [nil])
        manager.handleTextInput("https://writeopia.io", cursor: 20, stepId: "l0")

        manager.linkCardDescriptionFailed(stepId: "l0")

        #expect(manager.step(withId: "l0")?.text == nil)
        #expect(manager.describingLinkStepIds.isEmpty)
    }
}
