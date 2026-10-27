import Foundation
import Testing
@testable import NoteEditor
import WrData
import WrModels
import WrNetwork

/// A repository whose backend merge waits until the test lets it go, to look at the editor
/// while the document is still opening.
private final class GatedSyncingRepository: DocumentsRepository, DocumentSyncing {
    let document: WrDocument
    private var gate: CheckedContinuation<Void, Never>?
    private(set) var mergeStarted = false

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

    func syncFolder(_ folderId: String) async throws {}
    func syncWorkspaceChanges() async throws {}
    func fetchAndMerge(documentId: String) async throws -> WrDocument? {
        mergeStarted = true
        await withCheckedContinuation { gate = $0 }
        return nil
    }
    func pushSteps(document: WrDocument, changes: [StoryStep], deletions: [String], lastSyncTimestamp: Int64) async throws -> Int64 { 0 }

    func letMergeFinish() {
        gate?.resume()
        gate = nil
    }
}

private let document = WrDocument(id: "d", title: "Plan", workspaceId: "w", content: [
    StoryStep(id: "t", type: .title, text: "Plan", position: Rank.at(0)),
])

@Suite struct ContentLoadActivityTests {
    @Test func theActivityHoldsFromTheLocalLoadUntilTheBackendMerge() async {
        let activity = ContentLoadActivity()
        let repository = GatedSyncingRepository(document)
        let viewModel = NoteEditorViewModel(documentId: "d", repository: repository, loadActivity: activity)
        #expect(!activity.isLoading)

        let opening = Task { await viewModel.open() }
        while !repository.mergeStarted { await Task.yield() }

        // The device copy is already shown, but the backend copy is still on its way.
        #expect(viewModel.hasLoaded)
        #expect(activity.isLoading)

        repository.letMergeFinish()
        await opening.value
        #expect(!activity.isLoading)
    }

    @Test func overlappingLoadsKeepTheActivityUntilTheLastOneEnds() async {
        let activity = ContentLoadActivity()
        let first = GatedSyncingRepository(document)
        let second = GatedSyncingRepository(document)
        let firstOpen = Task { await NoteEditorViewModel(documentId: "d", repository: first, loadActivity: activity).open() }
        let secondOpen = Task { await NoteEditorViewModel(documentId: "d", repository: second, loadActivity: activity).open() }
        while !(first.mergeStarted && second.mergeStarted) { await Task.yield() }

        first.letMergeFinish()
        await firstOpen.value
        #expect(activity.isLoading)

        second.letMergeFinish()
        await secondOpen.value
        #expect(!activity.isLoading)
    }

    @Test func openingWithoutAnActivityStillLoadsAndMerges() async {
        let repository = GatedSyncingRepository(document)
        let viewModel = NoteEditorViewModel(documentId: "d", repository: repository)
        let opening = Task { await viewModel.open() }
        while !repository.mergeStarted { await Task.yield() }
        repository.letMergeFinish()
        await opening.value

        #expect(viewModel.hasLoaded)
    }
}
