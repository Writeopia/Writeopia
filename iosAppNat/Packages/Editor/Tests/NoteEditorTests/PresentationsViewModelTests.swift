import Foundation
import Testing
@testable import NoteEditor
import WrData
import WrModels
import WrNetwork
import WrStorage

private let sample = """
    ## Why the sky is blue

    Sunlight scatters in the atmosphere.
    ---

    ## Thank you!
    The presentation is over.
    """

/// Keeps the presentations in memory, like the SQLite repository does on the device.
final class MemoryPresentationsRepository: PresentationsStore {
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

/// A backend of presentations: makes and keeps them, like the cloud does.
final class FakePresentationsBackend: PresentationsRepository, PresentationGenerating {
    let store = MemoryPresentationsRepository()
    var answer: Presentation?
    var error: Error?
    private(set) var generatedFor: [String] = []

    func presentations(ofDocument documentId: String) async throws -> [Presentation] { try await store.presentations(ofDocument: documentId) }
    func presentation(id: String) async throws -> Presentation? { try await store.presentation(id: id) }
    func deletePresentation(id: String) async throws { try await store.deletePresentation(id: id) }

    func generatePresentation(documentId: String) async throws -> Presentation {
        generatedFor.append(documentId)
        if let error { throw error }
        guard let answer else { throw PresentationError(message: "nothing") }
        try await store.savePresentation(answer)
        return answer
    }
}

/// A repository of documents that also keeps presentations, so the editor offers them.
final class PresentingDocumentsRepository: DocumentsRepository, PresentationsStore {
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

/// The local path: the AI on this machine answers and the app parses and keeps the slides.
private func viewModel(ai: FakeAi, repository: MemoryPresentationsRepository = MemoryPresentationsRepository()) -> PresentationsViewModel {
    PresentationsViewModel(
        documentId: "d",
        repository: repository,
        generator: LocalPresentationGenerator(
            aiClient: ai,
            store: repository,
            documentTitle: { "Sky" },
            documentMarkdown: { "# Sky\n\nWhy is it blue?" }
        )
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

@Suite struct CloudPresentationsTests {
    @Test func theBackendMakesAndKeepsThePresentation() async {
        let backend = FakePresentationsBackend()
        backend.answer = Presentation(documentId: "d", title: "From the cloud", slides: PresentationMarkdown.parse(sample))
        var flushed = false
        let viewModel = PresentationsViewModel(documentId: "d", repository: backend, generator: backend) { flushed = true }

        let presentation = await viewModel.generate()

        #expect(flushed, "the pending edits go to the backend before it reads the document")
        #expect(backend.generatedFor == ["d"])
        #expect(presentation?.title == "From the cloud")
        #expect(viewModel.presentations.map(\.title) == ["From the cloud"])
        #expect(viewModel.error == nil)
    }

    @Test func aBackendErrorIsShown() async {
        let backend = FakePresentationsBackend()
        backend.error = PresentationError(message: "AI didn't return any slide")
        let viewModel = PresentationsViewModel(documentId: "d", repository: backend, generator: backend)

        let presentation = await viewModel.generate()

        #expect(presentation == nil)
        #expect(viewModel.error == "AI didn't return any slide")
    }

    @Test func decodesThePresentationOfTheBackend() throws {
        let json = """
            {"id": "p1", "documentId": "d", "title": "Sky", "createdAt": 1700000000000, "slides": [
                {"title": "Sky", "content": [
                    {"id": "s2", "type": {"name": "unordered_list_item", "number": 16}, "text": "Second", "position": 2.0, "tags": [], "spans": []},
                    {"id": "s1", "type": {"name": "message", "number": 0}, "text": "First", "position": 1.0, "tags": [], "spans": []}
                ]},
                {"title": "Bye"}
            ]}
            """

        let presentation = try JSONDecoder().decode(Presentation.self, from: Data(json.utf8))

        #expect(presentation.id == "p1")
        #expect(presentation.createdAt == 1_700_000_000_000)
        #expect(presentation.slides.map(\.title) == ["Sky", "Bye"])
        #expect(presentation.slides[0].steps.map(\.text) == ["First", "Second"], "the steps come in the order of their positions")
        #expect(presentation.slides[0].steps[1].type.number == StoryType.unorderedListItem.number)
        #expect(presentation.slides[1].steps.isEmpty)
    }
}

@Suite struct EditorPresentationsTests {
    @Test func theEditorOffersPresentationsForTheSourceOfTheSession() {
        let repository = PresentingDocumentsRepository(document)

        let local = NoteEditorViewModel(documentId: "d", repository: repository, aiClient: FakeAi(), presentations: .local)
        #expect(local.showsPresentations)
        #expect(local.presentations?.documentId == "d")

        let cloud = NoteEditorViewModel(
            documentId: "d",
            repository: OneDocumentRepository(document),
            aiClient: FakeAi(),
            presentations: .cloud(PresentationsAPI(client: APIClient(transport: URLSession.shared, tokenStore: InMemoryTokenStore()), workspaceId: "w"))
        )
        #expect(cloud.showsPresentations, "the cloud needs no local store")

        let none = NoteEditorViewModel(documentId: "d", repository: repository, aiClient: FakeAi())
        #expect(!none.showsPresentations)

        let localWithoutAi = NoteEditorViewModel(documentId: "d", repository: repository, presentations: .local)
        #expect(!localWithoutAi.showsPresentations)

        let localWithoutStore = NoteEditorViewModel(documentId: "d", repository: OneDocumentRepository(document), aiClient: FakeAi(), presentations: .local)
        #expect(!localWithoutStore.showsPresentations)
    }
}
