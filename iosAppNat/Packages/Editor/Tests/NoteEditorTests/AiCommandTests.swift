import Foundation
import Testing
@testable import NoteEditor
import Writeopia
import WriteopiaUI
import WrData
import WrModels

/// The free command of the AI dialog: new content is added as an answer, an edit replaces the
/// selected lines or rewrites the document.
@Suite struct AiCommandTests {
    private let image = StoryStep(id: "i", type: .image, url: "pic.png", position: 2)

    private var document: WrDocument {
        WrDocument(id: "d", title: "Plan", workspaceId: "w", content: [
            StoryStep(id: "t", type: .title, text: "Plan", position: 0),
            StoryStep(id: "a", type: .text, text: "First idea", position: 1),
            image,
            StoryStep(id: "b", type: .text, text: "Second idea", position: 3),
        ])
    }

    private func viewModel(_ ai: FakeAi) async -> NoteEditorViewModel {
        let viewModel = NoteEditorViewModel(documentId: "d", repository: OneDocumentRepository(document), aiClient: ai)
        await viewModel.loadDocument()
        return viewModel
    }

    private func waitForAi(_ viewModel: NoteEditorViewModel) async {
        for _ in 0..<200 where viewModel.isAiRunning {
            try? await Task.sleep(for: .milliseconds(10))
        }
    }

    private func texts(_ viewModel: NoteEditorViewModel) -> [String] {
        viewModel.writeopiaManager.currentStory.sortedStories.map { $0.text ?? "" }
    }

    @Test func newContentIsAddedAsAnAnswerAfterTheCursor() async {
        let ai = FakeAi()
        ai.intent = .add
        ai.answers = ["Third", "Third idea"]
        let viewModel = await viewModel(ai)
        viewModel.writeopiaManager.onFocusChange(stepId: "a", hasFocus: true)

        viewModel.runAiCommand("give me one more idea")
        await waitForAi(viewModel)

        let request = ai.commandRequests.first
        #expect(request?.intent == .add)
        #expect(request?.command == "give me one more idea")
        #expect(request?.selectedText == "")
        #expect(request?.documentTitle == "Plan")
        #expect(request?.documentText == "Plan\nFirst idea\nSecond idea")
        #expect(texts(viewModel) == ["Plan", "First idea", "Third idea", "", "Second idea"])
        let answer = viewModel.writeopiaManager.currentStory.sortedStories[2]
        #expect(answer.type.number == StoryType.aiAnswer.number)
    }

    @Test func anEditOfTheSelectedLinesReplacesThem() async {
        let ai = FakeAi()
        ai.intent = .replace
        ai.answers = ["A better", "A better first idea"]
        let viewModel = await viewModel(ai)
        viewModel.writeopiaManager.onSelected(stepId: "a", isSelected: true)

        viewModel.runAiCommand("improve it")
        await waitForAi(viewModel)

        let request = ai.commandRequests.first
        #expect(request?.intent == .replace)
        #expect(request?.selectedText == "First idea")
        #expect(texts(viewModel) == ["Plan", "A better first idea", "", "Second idea"])
        #expect(viewModel.writeopiaManager.hasSelectedLines == false)
        #expect(viewModel.writeopiaManager.documentContent.map(\.id).contains("a") == false)
    }

    @Test func anEditOfTheDocumentRewritesItAroundTheBlocksItKeeps() async {
        let ai = FakeAi()
        ai.intent = .rewrite
        ai.answers = ["First idea, edited", "First idea, edited\n[[writeopia-block-1]]\n- Second idea"]
        let viewModel = await viewModel(ai)

        viewModel.runAiCommand("edit the first line and make the second a list")
        await waitForAi(viewModel)

        let request = ai.commandRequests.first
        #expect(request?.intent == .rewrite)
        #expect(request?.selectedText == "")
        #expect(request?.documentText == "First idea\n[[writeopia-block-1]]\nSecond idea")
        #expect(texts(viewModel) == ["Plan", "First idea, edited", "", "Second idea"])
        let steps = viewModel.writeopiaManager.currentStory.sortedStories
        #expect(steps[0].id == "t")
        #expect(steps[2].id == "i")
        #expect(steps[3].type.number == StoryType.unorderedListItem.number)
        #expect(steps.contains { $0.type.number == StoryType.skeleton.number } == false)
    }

    @Test func aFailedRewriteBringsTheDocumentBack() async {
        let ai = FakeAi()
        ai.intent = .rewrite
        ai.error = AiStreamError(message: "Quota exceeded")
        let viewModel = await viewModel(ai)

        viewModel.runAiCommand("edit the first line")
        await waitForAi(viewModel)

        #expect(texts(viewModel) == ["Plan", "First idea", "", "Second idea"])
        #expect(viewModel.writeopiaManager.currentStory.sortedStories.map(\.id) == ["t", "a", "i", "b"])
    }

    @Test func theRewriteCanBeUndone() async {
        let ai = FakeAi()
        ai.intent = .rewrite
        ai.answers = ["Rewritten\n[[writeopia-block-1]]"]
        let viewModel = await viewModel(ai)

        viewModel.runAiCommand("shorten it")
        await waitForAi(viewModel)
        #expect(texts(viewModel) == ["Plan", "Rewritten", ""])

        viewModel.writeopiaManager.undo()

        #expect(texts(viewModel) == ["Plan", "First idea", "", "Second idea"])
    }

    @Test func anAiWithoutAJudgeAddsTheAnswer() async {
        let ai = FakeAi()
        ai.intent = nil
        ai.answers = ["Added"]
        let viewModel = await viewModel(ai)
        viewModel.writeopiaManager.onSelected(stepId: "b", isSelected: true)

        viewModel.runAiCommand("rewrite it")
        await waitForAi(viewModel)

        #expect(ai.commandRequests.first?.intent == .add)
        #expect(texts(viewModel) == ["Plan", "First idea", "", "Second idea", "Added"])
    }

    @Test func aBlankCommandDoesNothing() async {
        let ai = FakeAi()
        ai.intent = .rewrite
        let viewModel = await viewModel(ai)

        viewModel.runAiCommand("   ")
        await waitForAi(viewModel)

        #expect(ai.commandRequests.isEmpty)
        #expect(texts(viewModel) == ["Plan", "First idea", "", "Second idea"])
    }
}

@Suite struct DocumentRewriteTests {
    private let image = StoryStep(id: "i", type: .image, url: "pic.png", position: 3)
    private let link = StoryStep(id: "k", type: .documentLink, text: "Other", position: 7)

    private var content: [StoryStep] {
        [
            StoryStep(id: "t", type: .title, text: "Title", position: 0),
            StoryStep(id: "h", type: .text, text: "Intro", tags: [TagInfo(tag: "H2")], position: 1),
            StoryStep(id: "p", type: .text, text: "A paragraph.", position: 2),
            image,
            StoryStep(id: "c", type: .checkItem, text: "Done", checked: true, position: 4),
            StoryStep(id: "l", type: .unorderedListItem, text: "Item", position: 5),
            StoryStep(id: "c1", type: .codeBlock, text: "fun a() {}", position: 6),
            StoryStep(id: "c2", type: .codeBlock, text: "fun b() {}", position: 6.5),
            link,
            StoryStep(id: "d", type: .divider, position: 8),
            StoryStep(id: "s", type: .loading, position: 9),
            StoryStep(id: "z", type: .text, text: "Last line.", position: 10),
        ]
    }

    @Test func theDocumentGoesAsMarkdownWithPlaceholdersForTheBlocksTheAiCannotWrite() throws {
        let rewrite = try #require(DocumentRewrite.build(content, cursorStepId: "p"))

        #expect(rewrite.markdown == """
            ## Intro
            A paragraph.
            [[writeopia-block-1]]
            - [x] Done
            - Item
            ```
            fun a() {}
            fun b() {}
            ```
            [[writeopia-block-2]]
            ---
            Last line.
            """)
        #expect(rewrite.stepIds == ["h", "p", "i", "c", "l", "c1", "c2", "k", "d", "z"])
        #expect(rewrite.keptBlocksInOrder.map(\.id) == ["i", "k"])
        #expect(rewrite.originalText == "Intro\nA paragraph.\nDone\nItem\nfun a() {}\nfun b() {}\nOther\nLast line.")
    }

    @Test func theAnswerComesBackAsStepsWithTheBlocksInTheirPlace() throws {
        let rewrite = try #require(DocumentRewrite.build(content, cursorStepId: nil))

        let parsed = rewrite.parse("""
            ## Intro
            A **bold** paragraph.
            [[writeopia-block-1]]
            - [ ] Not done
            ```
            fun c() {}
            ```
            [[writeopia-block-2]]
            The last line, edited.
            """)

        let steps = parsed.steps
        #expect(steps.map { $0.text ?? "" } == ["Intro", "A bold paragraph.", "", "Not done", "fun c() {}", "Other", "The last line, edited."])
        #expect(steps[0].headingLevel == 2)
        #expect(steps[2].id == "i")
        #expect(steps[3].checked == false)
        #expect(steps[4].type.number == StoryType.codeBlock.number)
        #expect(steps[5].id == "k")
        #expect(parsed.pendingBlocks.isEmpty)
        // The generated steps keep their ids from one parse to the next.
        #expect(rewrite.parse("## Intro\nA paragraph.").steps.map(\.id) == Array(steps.prefix(2).map(\.id)))
    }

    @Test func aBlockTheAnswerRepeatsStandsOnce() throws {
        let rewrite = try #require(DocumentRewrite.build(content, cursorStepId: nil))

        let parsed = rewrite.parse("Text\n[[writeopia-block-1]]\n[[writeopia-block-1]]\nMore")

        #expect(parsed.steps.map { $0.text ?? "" } == ["Text", "", "More"])
        #expect(parsed.steps.filter { $0.id == "i" }.count == 1)
        #expect(parsed.pendingBlocks.map(\.id) == ["k"])
    }

    @Test func theBlocksTheAnswerLeavesOutArePending() throws {
        let rewrite = try #require(DocumentRewrite.build(content, cursorStepId: nil))

        let parsed = rewrite.parse("Only text\n[[writeopia-block-2]]")

        #expect(parsed.steps.map { $0.text ?? "" } == ["Only text", "Other"])
        #expect(parsed.pendingBlocks.map(\.id) == ["i"])
    }

    @Test func aLongDocumentIsCutToTheParagraphsAroundTheCursor() throws {
        var long = [StoryStep(id: "t", type: .title, text: "Title", position: 0)]
        for index in 1...100 {
            long.append(StoryStep(id: "p\(index)", type: .text, text: "p\(index)", position: Double(index)))
        }

        let rewrite = try #require(DocumentRewrite.build(long, cursorStepId: "p50", limit: 40))

        // Each line costs 4 characters, so 10 of them fit: the cursor's, then one on each side.
        #expect(rewrite.stepIds == (45...54).map { "p\($0)" })
        #expect(rewrite.markdown == (45...54).map { "p\($0)" }.joined(separator: "\n"))
    }

    @Test func anEmptyDocumentHasNothingToRewrite() {
        #expect(DocumentRewrite.build([StoryStep(id: "t", type: .title, text: "T", position: 0)], cursorStepId: nil) == nil)
    }
}
