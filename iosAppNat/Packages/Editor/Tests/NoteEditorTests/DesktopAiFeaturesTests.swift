import Foundation
import Testing
@testable import NoteEditor
import Writeopia
import WriteopiaUI
import WrData
import WrModels

/// AI features of the desktop app: accepting answers, writing sections and list suggestions.
@Suite struct DesktopAiFeaturesTests {
    private let document = WrDocument(id: "d", title: "Trip", workspaceId: "w", content: [
        StoryStep(id: "t", type: .title, text: "Trip", position: 0),
        StoryStep(id: "h", type: .text, text: "Packing", tags: [TagInfo(tag: "H2")], position: 1),
        StoryStep(id: "l", type: .text, text: "Passport", position: 2),
        StoryStep(id: "e", type: .text, text: "End", position: 3),
    ])

    private func viewModel(_ ai: FakeAi?) async -> NoteEditorViewModel {
        let viewModel = NoteEditorViewModel(documentId: "d", repository: OneDocumentRepository(document), aiClient: ai)
        await viewModel.loadDocument()
        return viewModel
    }

    private func texts(_ viewModel: NoteEditorViewModel) -> [String] {
        viewModel.writeopiaManager.currentStory.sortedStories.map { $0.text ?? "" }
    }

    private func waitUntil(_ condition: () -> Bool) async {
        for _ in 0..<200 where !condition() {
            try? await Task.sleep(for: .milliseconds(10))
        }
    }

    // MARK: - Accept, copy and discard answers

    @Test func acceptedAnswerBecomesRegularLines() async {
        let ai = FakeAi()
        ai.answers = ["## Tasks\n- Buy tickets\n- [ ] Book hotel"]
        let viewModel = await viewModel(ai)
        let manager = viewModel.writeopiaManager

        viewModel.runAi(.actionPoints, mode: .document)
        await waitUntil { !viewModel.isAiRunning }
        let answer = manager.currentStory.sortedStories.last!
        #expect(answer.type.number == StoryType.aiAnswer.number)
        #expect(manager.streamingAnswerId == nil)

        manager.acceptAiAnswer(stepId: answer.id)

        let steps = manager.currentStory.sortedStories
        #expect(texts(viewModel) == ["Trip", "Packing", "Passport", "End", "Tasks", "Buy tickets", "Book hotel"])
        #expect(steps[4].hasTag("H2"))
        #expect(steps[5].type == .unorderedListItem)
        #expect(steps[6].type == .checkItem && steps[6].checked == false)
        #expect(!steps.contains { $0.type.number == StoryType.aiAnswer.number })
        #expect(manager.documentContent.count == 7)
    }

    @Test func answerCantBeAcceptedWhileStreaming() async {
        let viewModel = await viewModel(FakeAi())
        let manager = viewModel.writeopiaManager

        let loadingId = manager.loadingAtPosition(manager.lastPosition)
        #expect(manager.streamingAnswerId == loadingId)
        manager.aiAnswerFinished(stepId: loadingId)
        #expect(manager.streamingAnswerId == nil)
    }

    // MARK: - Generate section

    @Test func sectionIsWrittenBelowTheHeadingWithTheDesktopPrompt() async {
        let ai = FakeAi()
        ai.answers = ["Bring clothes for a week."]
        let viewModel = await viewModel(ai)

        viewModel.generateSection(stepId: "h")
        await waitUntil { !viewModel.isAiRunning }

        let prompt = ai.requests.first?.1 ?? ""
        #expect(ai.requests.first?.0 == .prompt)
        #expect(prompt.hasPrefix("Create a document section for a document."))
        #expect(prompt.contains("Trip\nPacking\nPassport\nEnd"))
        #expect(prompt.hasSuffix("Create content for this section: Packing"))
        #expect(texts(viewModel) == ["Trip", "Packing", "Bring clothes for a week.", "Passport", "End"])
    }

    @Test func sectionIsOnlyForHeadings() async {
        let ai = FakeAi()
        let viewModel = await viewModel(ai)

        viewModel.generateSection(stepId: "l")
        viewModel.generateSection(stepId: "t")

        #expect(ai.requests.isEmpty)
        #expect(viewModel.writeopiaManager.headingText(stepId: "h") == "Packing")
    }

    @Test func sectionWandIsOnlyOfferedWithAnAi() async {
        #expect(await viewModel(nil).writeopiaManager.onGenerateSection == nil)
        #expect(await viewModel(FakeAi()).writeopiaManager.onGenerateSection != nil)
    }

    // MARK: - List suggestions

    @Test func turningLinesIntoAListSuggestsTheNextItems() async {
        let ai = FakeAi()
        ai.answers = ["Sure:\n- Tickets\n- Charger\n- Sunscreen"]
        let viewModel = await viewModel(ai)
        let manager = viewModel.writeopiaManager

        manager.onSelected(stepId: "l", isSelected: true)
        manager.toggleTypeOfSelectedLines(.checkItem)
        await waitUntil { manager.hasAiSuggestions }

        #expect(ai.requests.first?.1.hasPrefix("Generate a list of options.") == true)
        #expect(texts(viewModel) == ["Trip", "Packing", "Passport", "Tickets", "Charger", "Sunscreen", "End"])
        let suggestions = manager.currentStory.sortedStories.filter(\.isAiSuggestion)
        #expect(suggestions.count == 3)
        #expect(suggestions.allSatisfy { $0.type == .checkItem && $0.checked == false })
        #expect(suggestions.first?.isFirstAiSuggestion == true)
        // Not saved until accepted.
        #expect(manager.documentContent.map { $0.text ?? "" } == ["Trip", "Packing", "Passport", "End"])
        #expect(!manager.documentText.contains("Tickets"))

        manager.acceptAiSuggestions()

        #expect(!manager.hasAiSuggestions)
        #expect(manager.documentContent.map { $0.text ?? "" } == ["Trip", "Packing", "Passport", "Tickets", "Charger", "Sunscreen", "End"])
        #expect(manager.documentContent.allSatisfy { $0.tags.isEmpty || $0.id == "h" })
    }

    @Test func dismissedSuggestionsAreRemoved() async {
        let viewModel = await viewModel(FakeAi())
        let manager = viewModel.writeopiaManager

        manager.showAiSuggestions(["One", "Two"], after: "l")
        #expect(texts(viewModel) == ["Trip", "Packing", "Passport", "One", "Two", "End"])

        viewModel.dismissAiSuggestions()

        #expect(texts(viewModel) == ["Trip", "Packing", "Passport", "End"])
    }

    @Test func typingANewLineDismissesTheSuggestions() async {
        let viewModel = await viewModel(FakeAi())
        let manager = viewModel.writeopiaManager

        manager.showAiSuggestions(["One"], after: "l")
        manager.handleTextInput("Passport\n", cursor: 9, stepId: "l")

        #expect(!manager.hasAiSuggestions)
        #expect(texts(viewModel) == ["Trip", "Packing", "Passport", "", "End"])
    }

    @Test func turningLinesIntoAParagraphDoesntSuggest() async {
        let ai = FakeAi()
        let viewModel = await viewModel(ai)
        let manager = viewModel.writeopiaManager

        manager.onSelected(stepId: "l", isSelected: true)
        manager.toggleTypeOfSelectedLines(.codeBlock)
        try? await Task.sleep(for: .milliseconds(50))

        #expect(ai.requests.isEmpty)
        #expect(!manager.hasAiSuggestions)
    }
}
