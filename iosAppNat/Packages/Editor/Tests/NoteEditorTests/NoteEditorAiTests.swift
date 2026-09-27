import Foundation
import Testing
@testable import NoteEditor
import Writeopia
import WriteopiaUI
import WrData
import WrModels
import WrNetwork

final class FakeAi: AiStreaming {
    var answers: [String] = []
    var error: Error?
    private(set) var requests: [(AiCommand, String)] = []

    func stream(_ command: AiCommand, prompt: String) -> AsyncThrowingStream<String, Error> {
        requests.append((command, prompt))
        let answers = answers
        let error = error
        return AsyncThrowingStream { continuation in
            for answer in answers { continuation.yield(answer) }
            continuation.finish(throwing: error)
        }
    }
}

final class OneDocumentRepository: DocumentsRepository {
    let document: WrDocument
    init(_ document: WrDocument) { self.document = document }

    func folderContents(folderId: String) async throws -> FolderContents { FolderContents() }
    func document(id: String) async throws -> WrDocument { document }
    func search(query: String) async throws -> [WrDocument] { [] }
    func createFolder(title: String, parentId: String) async throws -> Folder { throw APIError.notFound }
    func createDocument(title: String, parentId: String) async throws -> WrDocument { throw APIError.notFound }
    func moveDocument(id: String, toFolder folderId: String) async throws {}
    func moveFolder(id: String, toFolder folderId: String) async throws {}
}

private let document = WrDocument(id: "d", title: "Plan", workspaceId: "w", content: [
    StoryStep(id: "t", type: .title, text: "Plan", position: 0),
    StoryStep(id: "a", type: .text, text: "First idea", position: 1),
    StoryStep(id: "b", type: .text, text: "Second idea", position: 2),
])

private func loadedViewModel(_ ai: FakeAi?) async -> NoteEditorViewModel {
    let viewModel = NoteEditorViewModel(documentId: "d", repository: OneDocumentRepository(document), aiClient: ai)
    await viewModel.loadDocument()
    return viewModel
}

private func waitForAi(_ viewModel: NoteEditorViewModel) async {
    for _ in 0..<100 where viewModel.isAiRunning {
        try? await Task.sleep(for: .milliseconds(10))
    }
}

@Suite struct NoteEditorAiTests {
    @Test func aiIsOnlyAvailableWithAClient() async {
        #expect(await loadedViewModel(nil).isAiAvailable == false)
        #expect(await loadedViewModel(FakeAi()).isAiAvailable)
        #expect(NoteEditorViewModel.commands(for: .cursor) == [.prompt])
        #expect(NoteEditorViewModel.commands(for: .document) == AiCommand.allCases)
    }

    @Test func summaryOfDocumentStreamsIntoAnAnswerAtTheEnd() async {
        let ai = FakeAi()
        ai.answers = ["A short", "A short summary"]
        let viewModel = await loadedViewModel(ai)

        viewModel.runAi(.summary, mode: .document)
        // The loading step shows right away, before any answer.
        #expect(viewModel.writeopiaManager.currentStory.sortedStories.last?.type.number == StoryType.loading.number)
        await waitForAi(viewModel)

        #expect(ai.requests.first?.0 == .summary)
        #expect(ai.requests.first?.1 == "Plan\nFirst idea\nSecond idea")
        let last = viewModel.writeopiaManager.currentStory.sortedStories.last
        #expect(last?.type.number == StoryType.aiAnswer.number)
        #expect(last?.text == "A short summary")
        #expect(viewModel.writeopiaManager.documentContent.count == 4)
    }

    @Test func promptAtCursorAnswersBelowTheFocusedLine() async {
        let ai = FakeAi()
        ai.answers = ["Expanded"]
        let viewModel = await loadedViewModel(ai)
        viewModel.writeopiaManager.onFocusChange(stepId: "a", hasFocus: true)

        viewModel.runAi(.prompt, mode: .cursor)
        await waitForAi(viewModel)

        #expect(ai.requests.first?.1 == "First idea")
        let texts = viewModel.writeopiaManager.currentStory.sortedStories.map { $0.text ?? "" }
        #expect(texts == ["Plan", "First idea", "Expanded", "Second idea"])
    }

    @Test func errorsAreShownInTheAnswer() async {
        let ai = FakeAi()
        ai.error = AiStreamError(message: "Quota exceeded")
        let viewModel = await loadedViewModel(ai)

        viewModel.runAi(.faq, mode: .document)
        await waitForAi(viewModel)

        let last = viewModel.writeopiaManager.currentStory.sortedStories.last
        #expect(last?.type.number == StoryType.aiAnswer.number)
        #expect(last?.text == "Error. Message: Quota exceeded")
    }

    @Test func emptyAnswerRemovesTheLoadingStep() async {
        let viewModel = await loadedViewModel(FakeAi())

        viewModel.runAi(.tags, mode: .document)
        await waitForAi(viewModel)

        #expect(viewModel.writeopiaManager.currentStory.stories.count == 3)
    }
}

@Suite struct LinkInputTests {
    @Test func addsSchemeWhenMissing() {
        #expect(NoteEditorView.normalizedURL(" writeopia.io ") == "https://writeopia.io")
        #expect(NoteEditorView.normalizedURL("http://x.io") == "http://x.io")
        #expect(NoteEditorView.normalizedURL("   ") == nil)
    }

    @Test func linkIsAppliedToTheSavedSelection() async {
        let viewModel = await loadedViewModel(nil)
        let manager = viewModel.writeopiaManager
        manager.onSelectionChange(stepId: "a", start: 0, end: 5)
        let selection = manager.textSelection!

        // The alert takes the focus away, which clears the live selection.
        manager.onFocusChange(stepId: "a", hasFocus: false)
        manager.setLink("https://writeopia.io", for: selection)

        #expect(manager.step(withId: "a")?.spans == [SpanInfo(start: 0, end: 5, span: "LINK", extra: "https://writeopia.io")])
    }
}
