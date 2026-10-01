import Foundation
import Testing
@testable import DocumentsFeature
import WrData
import WrModels
import WrNetwork

final class FakeDocumentsRepository: DocumentsRepository {
    var contents = FolderContents()
    var moves: [String] = []
    var moveError: Error?

    func folderContents(folderId: String) async throws -> FolderContents { contents }
    func document(id: String) async throws -> WrDocument {
        guard let document = documentsById[id] else { throw APIError.notFound }
        return document
    }
    func search(query: String) async throws -> [WrDocument] { [] }
    var createdFolders: [Folder] = []

    func createFolder(title: String, parentId: String) async throws -> Folder {
        let folder = Folder(id: "new-\(createdFolders.count)", parentId: parentId, title: title, workspaceId: "w")
        createdFolders.append(folder)
        return folder
    }
    func createDocument(title: String, parentId: String) async throws -> WrDocument {
        WrDocument(
            id: UUID().uuidString,
            title: title,
            workspaceId: "w",
            content: [StoryStep(type: .title, text: title, position: 0)],
            parentId: parentId
        )
    }

    func moveDocument(id: String, toFolder folderId: String) async throws {
        if let moveError { throw moveError }
        moves.append("document \(id) -> \(folderId)")
        contents.documents.removeAll { $0.id == id }
    }

    func moveFolder(id: String, toFolder folderId: String) async throws {
        if let moveError { throw moveError }
        moves.append("folder \(id) -> \(folderId)")
        contents.folders.removeAll { $0.id == id }
        foldersById[id]?.parentId = folderId
    }

    var foldersById: [String: Folder] = [:]
    private(set) var updatedFolders: [Folder] = []
    var updateError: Error?
    func folder(id: String) async throws -> Folder? { foldersById[id] }
    func updateFolder(_ folder: Folder) async throws -> Folder {
        if let updateError { throw updateError }
        updatedFolders.append(folder)
        foldersById[folder.id] = folder
        return folder
    }

    func save(_ document: WrDocument) async throws { saved.append(document) }
    func deleteDocument(id: String) async throws {}

    var saved: [WrDocument] = []
    var documentsById: [String: WrDocument] = [:]
    private(set) var duplicated: [[String]] = []
    private(set) var favorited: [([String], Bool)] = []
    private(set) var deleted: [[String]] = []
    func duplicate(ids: [String]) async throws { duplicated.append(ids.sorted()) }
    func setFavorite(ids: [String], favorite: Bool) async throws { favorited.append((ids.sorted(), favorite)) }
    func deleteItems(ids: [String]) async throws { deleted.append(ids.sorted()) }
}

@Suite struct FolderMoveTests {
    let ideas = Folder(id: "f1", parentId: Folder.rootId, title: "Ideas", workspaceId: "w")
    let work = Folder(id: "f2", parentId: Folder.rootId, title: "Work", workspaceId: "w")
    let draft = WrDocument(id: "d1", title: "Draft", workspaceId: "w", parentId: Folder.rootId)

    private func makeViewModel() async -> (FolderContentsViewModel, FakeDocumentsRepository) {
        let repository = FakeDocumentsRepository()
        repository.contents = FolderContents(folders: [ideas, work], documents: [draft])
        let viewModel = FolderContentsViewModel(folderId: Folder.rootId, repository: repository)
        await viewModel.load()
        return (viewModel, repository)
    }

    @Test func favoritesListsTheFavoritesOfTheWholeTreeWithoutCreation() async {
        let repository = FakeDocumentsRepository()
        var favoriteDraft = draft
        favoriteDraft.isFavorite = true
        var favoriteIdeas = ideas
        favoriteIdeas.favorite = true
        repository.contents = FolderContents(folders: [favoriteIdeas, work], documents: [favoriteDraft])
        let viewModel = FolderContentsViewModel(folderId: FolderContentsViewModel.favoritesId, repository: repository)
        await viewModel.load()

        #expect(viewModel.isFavorites)
        #expect(!viewModel.canCreateItems)
        // "Ideas" is a favorite folder, "Draft" a favorite document; "Work" isn't a favorite.
        #expect(viewModel.folders.map(\.id) == ["f1"])
        #expect(viewModel.documents.map(\.id) == ["d1"])
        #expect(viewModel.ancestors.isEmpty)
        #expect(await viewModel.deleteFolder() == false)
    }

    @Test func payloadRoundTrips() {
        let payload = FolderItem.document(draft).payload
        #expect(FolderItem.Payload(payload.rawValue) == payload)
        #expect(FolderItem.Payload("some text from another app") == nil)
        #expect(FolderItem.Payload("writeopia-item:unknown:x") == nil)
    }

    @Test func dropsDocumentIntoFolder() async {
        let (viewModel, repository) = await makeViewModel()

        let moved = await viewModel.move(FolderItem.document(draft).payload.rawValue, into: ideas)

        #expect(moved)
        #expect(repository.moves == ["document d1 -> f1"])
        #expect(viewModel.documents.isEmpty)
    }

    @Test func dropsFolderIntoFolder() async {
        let (viewModel, repository) = await makeViewModel()

        #expect(await viewModel.move(FolderItem.folder(work).payload.rawValue, into: ideas))
        #expect(repository.moves == ["folder f2 -> f1"])
        #expect(viewModel.folders.map(\.id) == ["f1"])
    }

    @Test func ignoresDropOnItselfAndForeignText() async {
        let (viewModel, repository) = await makeViewModel()

        #expect(await viewModel.move(FolderItem.folder(ideas).payload.rawValue, into: ideas) == false)
        #expect(await viewModel.move("hello", into: ideas) == false)
        #expect(repository.moves.isEmpty)
    }

    @Test func failedMoveRestoresItemAndShowsError() async {
        let (viewModel, repository) = await makeViewModel()
        repository.moveError = MoveError.folderIntoItself

        #expect(await viewModel.move(FolderItem.folder(work).payload.rawValue, into: ideas) == false)
        #expect(viewModel.folders.map(\.id) == ["f1", "f2"])
        #expect(viewModel.actionError == MoveError.folderIntoItself.userMessage)
    }
}

final class FakeSummaryAi: AiStreaming {
    var answer = "# Summary of notes\n- Point one"
    private(set) var prompts: [(AiCommand, String)] = []

    func stream(_ command: AiCommand, prompt: String) -> AsyncThrowingStream<String, Error> {
        prompts.append((command, prompt))
        let answer = answer
        return AsyncThrowingStream { continuation in
            continuation.yield("# Summ")
            continuation.yield(answer)
            continuation.finish()
        }
    }
}

@Suite struct DocumentsSelectionTests {
    let ideas = Folder(id: "f1", parentId: Folder.rootId, title: "Ideas", workspaceId: "w", favorite: true)
    let draft = WrDocument(id: "d1", title: "Draft", workspaceId: "w", content: [
        StoryStep(type: .title, text: "Draft", position: 0),
        StoryStep(type: .text, text: "Body", position: 1),
    ], isFavorite: true, parentId: Folder.rootId)
    let plan = WrDocument(id: "d2", title: "Plan", workspaceId: "w", parentId: Folder.rootId)

    private func makeViewModel(ai: AiStreaming? = nil) async -> (FolderContentsViewModel, FakeDocumentsRepository) {
        let repository = FakeDocumentsRepository()
        repository.contents = FolderContents(folders: [ideas], documents: [draft, plan])
        repository.documentsById = ["d1": draft, "d2": plan]
        let viewModel = FolderContentsViewModel(folderId: Folder.rootId, repository: repository, aiClient: ai)
        await viewModel.load()
        return (viewModel, repository)
    }

    @Test func slidingTogglesSelection() async {
        let (viewModel, _) = await makeViewModel()

        viewModel.toggleSelection("d1")
        viewModel.toggleSelection("f1")
        #expect(viewModel.selectedIds == ["d1", "f1"])
        viewModel.toggleSelection("d1")
        #expect(viewModel.selectedIds == ["f1"])
        viewModel.clearSelection()
        #expect(!viewModel.hasSelection)
    }

    @Test func favoriteTogglesAllOrNothing() async {
        let (viewModel, repository) = await makeViewModel()

        // Both selected items are favorites: the button removes them.
        viewModel.toggleSelection("d1")
        viewModel.toggleSelection("f1")
        #expect(viewModel.selectionIsFavorite)
        await viewModel.favoriteSelected()
        #expect(repository.favorited.last?.0 == ["d1", "f1"])
        #expect(repository.favorited.last?.1 == false)

        // A mix: all become favorites.
        viewModel.toggleSelection("d1")
        viewModel.toggleSelection("d2")
        await viewModel.favoriteSelected()
        #expect(repository.favorited.last?.1 == true)
        #expect(!viewModel.hasSelection)
    }

    @Test func copyAndDeleteUseTheSelection() async {
        let (viewModel, repository) = await makeViewModel()

        viewModel.toggleSelection("d2")
        await viewModel.copySelected()
        #expect(repository.duplicated == [["d2"]])

        viewModel.toggleSelection("d1")
        viewModel.toggleSelection("f1")
        await viewModel.deleteSelected()
        #expect(repository.deleted == [["d1", "f1"]])
    }

    @Test func summaryBecomesANewDocumentInTheFolder() async throws {
        let ai = FakeSummaryAi()
        let (viewModel, repository) = await makeViewModel(ai: ai)
        #expect(viewModel.canSummarize)

        viewModel.toggleSelection("d1")
        viewModel.toggleSelection("f1")
        viewModel.summarizeSelected()
        #expect(viewModel.isSummarizing)
        await viewModel.summaryTask?.value
        #expect(!viewModel.isSummarizing)

        let prompt = try #require(ai.prompts.first)
        #expect(prompt.0 == .summary)
        #expect(prompt.1.contains("# Draft\nBody"))
        let summary = try #require(repository.saved.first)
        #expect(summary.title == "Summary of notes")
        #expect(summary.parentId == Folder.rootId)
        #expect(!viewModel.hasSelection)
    }

    @Test func summaryCanBeCancelledAndSavesNothing() async {
        let ai = NeverEndingAi()
        let (viewModel, repository) = await makeViewModel(ai: ai)
        viewModel.toggleSelection("d1")
        viewModel.summarizeSelected()
        #expect(viewModel.isSummarizing)
        for _ in 0..<10 { await Task.yield() }

        viewModel.cancelSummary()
        await viewModel.summaryTask?.value
        for _ in 0..<10 { await Task.yield() }

        #expect(!viewModel.isSummarizing)
        #expect(viewModel.summaryError == nil, "error: \(viewModel.summaryError ?? "none")")
        #expect(repository.saved.isEmpty)
    }

    @Test func droppedImagesBecomeUntitledNotesWithTheImage() async throws {
        let (viewModel, repository) = await makeViewModel()
        // A 1×1 PNG, and a file that isn't an image.
        let png = Data(base64Encoded: "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==")!
        let directory = URL.temporaryDirectory.appending(path: "drop \(UUID().uuidString)")
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        let image = directory.appending(path: "photo.png")
        try png.write(to: image)
        let text = directory.appending(path: "notes.txt")
        try Data("hi".utf8).write(to: text)

        await viewModel.importImages([image, text])

        let created = try #require(repository.saved.first)
        #expect(created.title == "")
        #expect(created.parentId == Folder.rootId)
        #expect(created.content.map(\.type) == [.title, .image])
        #expect(created.content[1].path?.hasSuffix(".jpg") == true)
        #expect(repository.saved.count == 1, "the text file was ignored")
        #expect(viewModel.actionError == nil)
    }

    @Test func theSelectionMovesToAnotherFolder() async {
        let (viewModel, repository) = await makeViewModel()
        viewModel.toggleSelection("d1")
        viewModel.toggleSelection("f1")
        #expect(viewModel.selectedFolderIds == ["f1"])

        await viewModel.moveSelected(to: "f2")

        #expect(Set(repository.moves) == ["document d1 -> f2", "folder f1 -> f2"])
        #expect(!viewModel.hasSelection)
        #expect(viewModel.actionError == nil)
    }

    @Test func foldersInTheSelectionHideCopyAndSummary() async {
        let (viewModel, _) = await makeViewModel()
        viewModel.toggleSelection("d1")
        #expect(!viewModel.selectionHasFolders)
        viewModel.toggleSelection("f1")
        #expect(viewModel.selectionHasFolders)
        viewModel.toggleSelection("d1")
        #expect(viewModel.selectionHasFolders)
        viewModel.clearSelection()
        #expect(!viewModel.selectionHasFolders)
    }

    @Test func summaryNeedsTheCloudAi() async {
        let (viewModel, _) = await makeViewModel()
        #expect(!viewModel.canSummarize)
    }
}

/// An AI that never answers, so a summary can only end by being cancelled.
private final class NeverEndingAi: AiStreaming {
    func stream(_ command: AiCommand, prompt: String) -> AsyncThrowingStream<String, Error> {
        AsyncThrowingStream { continuation in
            let task = Task {
                while !Task.isCancelled {
                    try? await Task.sleep(for: .milliseconds(20))
                }
                continuation.finish(throwing: CancellationError())
            }
            continuation.onTermination = { _ in task.cancel() }
        }
    }
}
