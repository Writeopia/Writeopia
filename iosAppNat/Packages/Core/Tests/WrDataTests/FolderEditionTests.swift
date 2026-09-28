import Foundation
import Testing
@testable import WrData
import WrModels
import WrNetwork
import WrStorage

private func makeLocal() -> LocalDocumentsRepository {
    LocalDocumentsRepository(
        directory: FileManager.default.temporaryDirectory.appending(path: "folders \(UUID().uuidString)", directoryHint: .isDirectory),
        workspaceId: "w",
        seedsWelcome: false
    )
}

@Suite struct FolderDisplayTests {
    @Test func rawValuesMatchTheComposeApp() {
        #expect(DocumentsArrangement.allCases.map(\.rawValue) == ["list", "grid", "staggered_grid"])
        #expect(DocumentsOrder.allCases.map(\.rawValue) == ["last_updated_at", "created_at", "title"])
    }
}

@Suite struct LocalFolderEditionTests {
    @Test func pathGoesFromTheRootToTheFolder() async throws {
        let local = makeLocal()
        let work = try await local.createFolder(title: "Work", parentId: Folder.rootId)
        let ideas = try await local.createFolder(title: "Ideas", parentId: work.id)

        #expect(try await local.folderPath(to: ideas.id).map(\.title) == ["Work", "Ideas"])
        #expect(try await local.folderPath(to: Folder.rootId).isEmpty)
    }

    @Test func updateChangesTitleAndIconOnly() async throws {
        let local = makeLocal()
        let work = try await local.createFolder(title: "Work", parentId: Folder.rootId)
        let other = try await local.createFolder(title: "Other", parentId: Folder.rootId)
        try await local.moveFolder(id: work.id, toFolder: other.id)

        // The copy the edition started from still has the old parent.
        var edited = work
        edited.title = "Job"
        edited.icon = IconInfo(label: "home", tint: -65536)
        let updated = try await local.updateFolder(edited)

        #expect(updated.title == "Job")
        #expect(updated.icon == IconInfo(label: "home", tint: -65536))
        #expect(updated.parentId == other.id)
        #expect(try await local.folder(id: work.id)?.title == "Job")
    }

    @Test func deletedFoldersAreNotFound() async throws {
        let local = makeLocal()
        let work = try await local.createFolder(title: "Work", parentId: Folder.rootId)
        try await local.deleteItems(ids: [work.id])

        #expect(try await local.folder(id: work.id) == nil)
    }
}

@Suite struct SyncedFolderEditionTests {
    private func makeRepository(_ backend: FakeBackend) -> SyncedDocumentsRepository {
        let client = APIClient(transport: backend, tokenStore: InMemoryTokenStore(accessToken: "a"), baseURL: URL(string: "https://x.io")!)
        return SyncedDocumentsRepository(
            local: makeLocal(),
            remote: RemoteDocumentsRepository(client: client, workspaceId: "w"),
            api: SyncAPI(client: client, workspaceId: "w")
        )
    }

    @Test func updateIsStoredAndSentRightAway() async throws {
        let backend = FakeBackend()
        let repository = makeRepository(backend)
        var folder = try await repository.createFolder(title: "Work", parentId: Folder.rootId)

        folder.title = "Job"
        folder.icon = IconInfo(label: "star", tint: -256)
        try await repository.updateFolder(folder)

        let sent = try #require(backend.sentFolders.last)
        #expect(sent["title"] as? String == "Job")
        #expect((sent["icon"] as? [String: Any])?["label"] as? String == "star")
        #expect(try repository.local.storedFolder(id: folder.id)?.isOutdated == false)
    }

    @Test func updateMadeOfflineIsSentWithTheNextSync() async throws {
        let backend = FakeBackend()
        let repository = makeRepository(backend)
        var folder = try await repository.createFolder(title: "Work", parentId: Folder.rootId)
        backend.folderDiff = FolderContents(folders: [folder])

        backend.folderSendFails = true
        folder.title = "Job"
        try await repository.updateFolder(folder)
        #expect(try repository.local.storedFolder(id: folder.id)?.isOutdated == true)

        backend.folderSendFails = false
        try await repository.syncFolder(Folder.rootId)

        #expect(backend.sentFolders.last?["title"] as? String == "Job")
        #expect(try repository.local.storedFolder(id: folder.id)?.title == "Job")
        #expect(try repository.local.storedFolder(id: folder.id)?.isOutdated == false)
    }

    @Test func moveMadeOfflineIsNotUndoneBySyncingTheOldParent() async throws {
        let backend = FakeBackend()
        let repository = makeRepository(backend)
        let moved = try await repository.createFolder(title: "Moved", parentId: Folder.rootId)
        let target = try await repository.createFolder(title: "Target", parentId: Folder.rootId)
        // The backend can't be reached for the move (the fake answers 404), so it still has the
        // folder in the root.
        backend.folderDiff = FolderContents(folders: [moved, target])
        try await repository.moveFolder(id: moved.id, toFolder: target.id)

        try await repository.syncFolder(Folder.rootId)

        #expect(try repository.local.storedFolder(id: moved.id)?.parentId == target.id)
        #expect(backend.sentFolders.last?["parentId"] as? String == target.id)
    }

    @Test func changeRightAfterASyncIsStillSent() async throws {
        let local = makeLocal()
        let now = Date()
        // Synced a moment in the future (another device's clock, or the same millisecond).
        try local.store(Folder(id: "f", parentId: "root", title: "Work", workspaceId: "w",
                               lastUpdatedAt: now, lastSyncedAt: now.addingTimeInterval(1)))

        var edited = try #require(try local.storedFolder(id: "f"))
        edited.title = "Job"
        try await local.updateFolder(edited)
        #expect(try local.storedFolder(id: "f")?.isOutdated == true)

        try local.store(Folder(id: "g", parentId: "root", title: "Other", workspaceId: "w",
                               lastUpdatedAt: now, lastSyncedAt: now.addingTimeInterval(1)))
        try await local.moveFolder(id: "g", toFolder: "f")
        #expect(try local.storedFolder(id: "g")?.isOutdated == true)
    }

    @Test func editMadeWhileSendingStaysOutdated() async throws {
        let backend = FakeBackend()
        let repository = makeRepository(backend)
        let folder = try await repository.createFolder(title: "Work", parentId: Folder.rootId)
        backend.onFolderSend = {
            // Another edit lands while the first one is on its way.
            var newer = try! repository.local.storedFolder(id: folder.id)!
            newer.title = "Newer"
            newer.lastUpdatedAt = Date().addingTimeInterval(10)
            try! repository.local.store(newer)
        }

        var edited = folder
        edited.title = "Job"
        try await repository.updateFolder(edited)

        let stored = try #require(try repository.local.storedFolder(id: folder.id))
        #expect(stored.title == "Newer")
        #expect(stored.isOutdated)
    }
}
