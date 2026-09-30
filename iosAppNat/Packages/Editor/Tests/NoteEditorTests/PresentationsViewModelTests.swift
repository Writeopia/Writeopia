import Foundation
import Testing
@testable import NoteEditor
import WrData
import WrModels
import WrNetwork

private let sample = """
    ## Why the sky is blue

    Sunlight scatters in the atmosphere.
    ---

    ## Thank you!
    The presentation is over.
    """

/// Keeps the presentations in memory, like the SQLite repository does on the device.
final class MemoryPresentationsRepository: PresentationsRepository {
    var saved: [Presentation] = []
    var failsSaving = false

    func presentations(ofDocument documentId: String) async throws -> [Presentation] {
        saved.filter { $0.documentId == documentId }.sorted { $0.createdAt > $1.createdAt }
    }

    func presentation(id: String) async throws -> Presentation? {
        saved.first { $0.id == id }
    }

    func savePresentation(_ presentation: Presentation) async throws {
        if failsSaving { throw APIError.unexpectedStatus(500) }
        saved.removeAll { $0.id == presentation.id }
        saved.append(presentation)
    }

    func deletePresentation(id: String) async throws {
        saved.removeAll { $0.id == id }
    }
}

/// A repository of documents that also keeps presentations, so the editor offers them.
final class PresentingDocumentsRepository: DocumentsRepository, PresentationsRepository {
    let document: WrDocument
    let presentations = MemoryPresentationsRepository()
    init(_ document: WrDocument) { self.document = document }

    func folderContents(folderId: String) async throws -> FolderContents { FolderContents() }
    func document(id: String) async throws -> WrDocument { document }
    func search(query: String) async throws -> [WrDocument] { [] }
    func createFolder(title: String, parentId: String) async throws -> Folder { throw APIError.notFound }
    func createDocument(title: String, parentId: String) async throws -> WrDocument { throw APIError.notFound }
    func moveDocument(id: String, toFolder folderId: String) async throws {}
    func moveFolder(id: String, toFolder folderId: String) async throws {}
    func save(_ document: WrDocument) async throws {}
    func deleteDocument(id: String) async throws {}
    func duplicate(ids: [String]) async throws {}
    func setFavorite(ids: [String], favorite: Bool) async throws {}
    func deleteItems(ids: [String]) async throws {}

    func presentations(ofDocument documentId: String) async throws -> [Presentation] { try await presentations.presentations(ofDocument: documentId) }
    func presentation(id: String) async throws -> Presentation? { try await presentations.presentation(id: id) }
    func savePresentation(_ presentation: Presentation) async throws { try await presentations.savePresentation(presentation) }
    func deletePresentation(id: String) async throws { try await presentations.deletePresentation(id: id) }
}

private let document = WrDocument(id: "d", title: "Sky", workspaceId: "w", content: [
    StoryStep(id: "t", type: .title, text: "Sky", position: 0),
    StoryStep(id: "a", type: .text, text: "Why is it blue?", position: 1),
])

private func viewModel(ai: FakeAi, repository: MemoryPresentationsRepository = MemoryPresentationsRepository()) -> PresentationsViewModel {
    PresentationsViewModel(
        documentId: "d",
        documentTitle: { "Sky" },
        documentMarkdown: { "# Sky\n\nWhy is it blue?" },
        repository: repository,
        aiClient: ai
    )
}

@Suite struct PresentationsViewModelTests {
    @Test func generatesSavesAndListsThePresentation() async {
        let ai = FakeAi()
        ai.answers = ["## Why", sample]
        let repository = MemoryPresentationsRepository()
        let viewModel = viewModel(ai: ai, repository: repository)

        let presentation = await viewModel.generate()

        #expect(presentation?.title == "Why the sky is blue")
        #expect(presentation?.documentId == "d")
        #expect(presentation?.slides.map(\.title) == ["Why the sky is blue", "Thank you!"])
        #expect(repository.saved.map(\.id) == [presentation?.id])
        #expect(viewModel.presentations.map(\.id) == [presentation?.id])
        #expect(viewModel.error == nil)
        #expect(!viewModel.isGenerating)
    }

    @Test func sendsTheDocumentAsMarkdownWithThePresentationInstructions() async {
        let ai = FakeAi()
        ai.answers = [sample]
        let viewModel = viewModel(ai: ai)

        await viewModel.generate()

        #expect(ai.requests.count == 1)
        #expect(ai.requests[0].0 == .prompt)
        #expect(ai.requests[0].1.contains("# Sky\n\nWhy is it blue?"))
        #expect(ai.requests[0].1.contains("---"))
    }

    @Test func aSlideWithoutAHeadingIsNamedAfterTheDocument() async {
        let ai = FakeAi()
        ai.answers = ["Just text\n---\n## Second\nMore"]
        let viewModel = viewModel(ai: ai)

        let presentation = await viewModel.generate()

        #expect(presentation?.title == "Sky")
        #expect(presentation?.slides.count == 2)
    }

    @Test func anAnswerWithoutSlidesIsAnError() async {
        let ai = FakeAi()
        ai.answers = ["", "\n---\n"]
        let repository = MemoryPresentationsRepository()
        let viewModel = viewModel(ai: ai, repository: repository)

        let presentation = await viewModel.generate()

        #expect(presentation == nil)
        #expect(viewModel.error != nil)
        #expect(repository.saved.isEmpty)
    }

    @Test func anAiErrorIsShown() async {
        let ai = FakeAi()
        ai.error = AiStreamError(message: "Quota exceeded")
        let viewModel = viewModel(ai: ai)

        let presentation = await viewModel.generate()

        #expect(presentation == nil)
        #expect(viewModel.error == "Quota exceeded")
    }

    @Test func aSavingErrorIsShown() async {
        let ai = FakeAi()
        ai.answers = [sample]
        let repository = MemoryPresentationsRepository()
        repository.failsSaving = true
        let viewModel = viewModel(ai: ai, repository: repository)

        let presentation = await viewModel.generate()

        #expect(presentation == nil)
        #expect(viewModel.error != nil)
    }

    @Test func deletesAPresentation() async throws {
        let repository = MemoryPresentationsRepository()
        let presentation = Presentation(documentId: "d", title: "Sky", slides: PresentationMarkdown.parse(sample))
        try await repository.savePresentation(presentation)
        let viewModel = viewModel(ai: FakeAi(), repository: repository)
        await viewModel.load()
        #expect(viewModel.presentations.count == 1)

        await viewModel.delete(presentation)

        #expect(viewModel.presentations.isEmpty)
        #expect(repository.saved.isEmpty)
    }
}

@Suite struct EditorPresentationsTests {
    @Test func theEditorOffersPresentationsWhenEnabledWithAnAiAndAStore() {
        let repository = PresentingDocumentsRepository(document)

        let enabled = NoteEditorViewModel(documentId: "d", repository: repository, aiClient: FakeAi(), presentationsEnabled: true)
        #expect(enabled.showsPresentations)
        #expect(enabled.presentations?.documentId == "d")

        let disabled = NoteEditorViewModel(documentId: "d", repository: repository, aiClient: FakeAi())
        #expect(!disabled.showsPresentations)

        let withoutAi = NoteEditorViewModel(documentId: "d", repository: repository, presentationsEnabled: true)
        #expect(!withoutAi.showsPresentations)

        let withoutStore = NoteEditorViewModel(documentId: "d", repository: OneDocumentRepository(document), aiClient: FakeAi(), presentationsEnabled: true)
        #expect(!withoutStore.showsPresentations)
    }
}
