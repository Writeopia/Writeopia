import Foundation
import Testing
@testable import WrData
import WrModels

@Suite struct EraseLocalCopyTests {
    private func makeStore() -> LocalDocumentsRepository {
        LocalDocumentsRepository(
            directory: FileManager.default.temporaryDirectory.appending(path: "erase \(UUID().uuidString)", directoryHint: .isDirectory),
            workspaceId: "w",
            seedsWelcome: false
        )
    }

    @Test func erasingRemovesTheContentAndTheSyncWatermarks() async throws {
        let store = makeStore()
        let folder = try await store.createFolder(title: "Ideas", parentId: Folder.rootId)
        let document = WrDocument(id: "d", title: "T", workspaceId: "w", parentId: folder.id)
        try store.store(document)
        try store.saveEdit(document: document, changedSteps: [StoryStep(id: "s", type: .text, text: "hi", position: Rank.at(0))], deletedStepIds: [])
        try store.setWorkspaceContentsSyncedAt(10)
        try store.setEventsSyncedAt(20)
        try store.setStepDeletionsSyncedAt(30)

        try store.eraseAll()

        #expect(try store.storedDocument(id: "d") == nil)
        #expect(try await store.folderContents(folderId: Folder.rootId).folders.isEmpty)
        #expect(try store.workspaceContentsSyncedAt() == nil)
        #expect(try store.eventsSyncedAt() == nil)
        #expect(try store.stepDeletionsSyncedAt() == nil)
    }
}
