import Foundation
import Testing
@testable import DocumentsFeature
import WrData
import WrModels
import WrNetwork

/// A repository of the open space: its documents sync with the backend.
private final class FakeSyncedRepository: DocumentsRepository, DocumentSyncing {
    func folderContents(folderId: String) async throws -> FolderContents { FolderContents() }
    func document(id: String) async throws -> WrDocument { throw APIError.notFound }
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

    private(set) var syncCount = 0
    func syncFolder(_ folderId: String) async throws {
        syncCount += 1
        try await Task.sleep(for: .milliseconds(50))
    }
    func fetchAndMerge(documentId: String) async throws -> WrDocument? { nil }
    func pushSteps(document: WrDocument, changes: [StoryStep], deletions: [String], lastSyncTimestamp: Int64) async throws -> Int64 { 0 }
}

@Suite struct SyncStateTests {
    private let edited = WrDocument(id: "d1", title: "Edited", workspaceId: "w", lastUpdatedAt: 20, lastSyncedAt: 10)
    private let synced = WrDocument(id: "d2", title: "Synced", workspaceId: "w", lastUpdatedAt: 10, lastSyncedAt: 20)
    private let neverSent = WrDocument(id: "d3", title: "New", workspaceId: "w", lastUpdatedAt: 10, lastSyncedAt: nil)
    private let folder = Folder(id: "f", parentId: Folder.rootId, title: "Work", workspaceId: "w", lastSyncedAt: nil)

    @Test func openSpaceShowsTheSyncState() {
        let viewModel = FolderContentsViewModel(folderId: Folder.rootId, repository: FakeSyncedRepository())

        #expect(viewModel.showsSyncState)
    }

    @Test func concurrentLoadsSyncTheFolderOnce() async {
        let repository = FakeSyncedRepository()
        let viewModel = FolderContentsViewModel(folderId: Folder.rootId, repository: repository)

        async let first: Void = viewModel.load()
        async let second: Void = viewModel.load()
        _ = await (first, second)
        await viewModel.load()

        #expect(repository.syncCount == 2)
    }

    @Test func privateSpaceHidesTheSyncState() {
        let viewModel = FolderContentsViewModel(folderId: Folder.rootId, repository: FakeDocumentsRepository())

        #expect(!viewModel.showsSyncState)
    }

    @Test func marksDocumentsChangedSinceTheirLastSync() {
        #expect(ItemCard(item: .document(edited), isDropTarget: false, showsSyncState: true).needsSync)
        #expect(ItemCard(item: .document(neverSent), isDropTarget: false, showsSyncState: true).needsSync)
        #expect(!ItemCard(item: .document(synced), isDropTarget: false, showsSyncState: true).needsSync)
    }

    @Test func foldersAndPrivateSpacesAreNeverMarked() {
        #expect(!ItemCard(item: .folder(folder), isDropTarget: false, showsSyncState: true).needsSync)
        #expect(!ItemCard(item: .document(edited), isDropTarget: false, showsSyncState: false).needsSync)
    }
}
