import Foundation
import Testing
@testable import WrData
import WrModels
import WrNetwork
import WrStorage

private func step(_ id: String, _ text: String, at position: Double, updated: Int64?) -> StoryStep {
    StoryStep(id: id, type: .text, text: text, position: position, lastUpdatedAt: updated)
}

@Suite struct DocumentMergerTests {
    @Test func newerStepWinsAndStepsFromBothSidesAreKept() throws {
        let local = WrDocument(id: "d", title: "Local", workspaceId: "w", content: [
            step("a", "local a", at: 0, updated: 100),
            step("b", "local b", at: 1, updated: 300),
            step("only-local", "L", at: 3, updated: 10),
        ], lastUpdatedAt: 500)
        let remote = WrDocument(id: "d", title: "Remote", workspaceId: "w", content: [
            step("a", "remote a", at: 0, updated: 200),
            step("b", "remote b", at: 1, updated: 250),
            step("only-remote", "R", at: 2, updated: 10),
        ], lastUpdatedAt: 400)

        let merged = try #require(DocumentMerger.merge(local: local, remote: remote))

        #expect(merged.content.map(\.text) == ["remote a", "local b", "R", "L"])
        // Metadata from the copy updated last.
        #expect(merged.title == "Local")
    }

    @Test func localWinsWhenTimesAreMissingOrEqual() throws {
        let local = WrDocument(id: "d", title: "", workspaceId: "w", content: [step("a", "local", at: 0, updated: nil)])
        let remote = WrDocument(id: "d", title: "", workspaceId: "w", content: [step("a", "remote", at: 0, updated: 999)])
        #expect(DocumentMerger.merge(local: local, remote: remote)?.content.first?.text == "local")

        let tieLocal = WrDocument(id: "d", title: "", workspaceId: "w", content: [step("a", "local", at: 0, updated: 5)])
        let tieRemote = WrDocument(id: "d", title: "", workspaceId: "w", content: [step("a", "remote", at: 0, updated: 5)])
        #expect(DocumentMerger.merge(local: tieLocal, remote: tieRemote)?.content.first?.text == "local")
    }

    @Test func oneMissingSideReturnsTheOther() {
        let document = WrDocument(id: "d", title: "", workspaceId: "w")
        #expect(DocumentMerger.merge(local: nil, remote: document) == document)
        #expect(DocumentMerger.merge(local: document, remote: nil) == document)
        #expect(DocumentMerger.merge(local: nil, remote: nil) == nil)
    }
}

private func makeStore() -> LocalDocumentsRepository {
    LocalDocumentsRepository(
        directory: FileManager.default.temporaryDirectory.appending(path: "sync \(UUID().uuidString)", directoryHint: .isDirectory),
        workspaceId: "w",
        seedsWelcome: false
    )
}

@Suite struct ConflictHandlerTests {
    @Test func newestDocumentWinsAndLocalWinnersAreSent() throws {
        let store = makeStore()
        let localNewer = WrDocument(id: "a", title: "mine", workspaceId: "w", lastUpdatedAt: 200, parentId: "root")
        let remoteOlder = WrDocument(id: "a", title: "theirs", workspaceId: "w", lastUpdatedAt: 100, parentId: "root")
        let remoteOnly = WrDocument(id: "b", title: "new there", workspaceId: "w", lastUpdatedAt: 50, parentId: "root")
        let localOnly = WrDocument(id: "c", title: "new here", workspaceId: "w", lastUpdatedAt: 50, parentId: "root")

        let toSend = try DocumentConflictHandler.handle(local: [localNewer, localOnly], remote: [remoteOlder, remoteOnly], store: store)

        #expect(Set(toSend.map(\.id)) == ["a", "c"])
        #expect(try store.storedDocument(id: "a")?.title == "mine")
        let received = try #require(try store.storedDocument(id: "b"))
        #expect(received.title == "new there")
        #expect(!received.isOutdated)
    }

    @Test func documentsDeletedHereAreNotBroughtBack() throws {
        let store = makeStore()
        try store.store(WrDocument(id: "a", title: "gone", workspaceId: "w", lastUpdatedAt: 300, deleted: true))

        _ = try DocumentConflictHandler.handle(
            local: [],
            remote: [WrDocument(id: "a", title: "still there", workspaceId: "w", lastUpdatedAt: 100)],
            store: store
        )

        #expect(try store.storedDocument(id: "a")?.deleted == true)
    }

    @Test func foldersFollowTheSameRules() throws {
        let store = makeStore()
        let mine = Folder(id: "f", parentId: "root", title: "Mine", workspaceId: "w", lastUpdatedAt: Date(millis: 200))
        let theirs = Folder(id: "f", parentId: "root", title: "Theirs", workspaceId: "w", lastUpdatedAt: Date(millis: 100))
        let newThere = Folder(id: "g", parentId: "root", title: "New", workspaceId: "w", lastUpdatedAt: Date(millis: 100))

        let toSend = try FolderConflictHandler.handle(local: [mine], remote: [theirs, newThere], store: store)

        #expect(toSend.map(\.id) == ["f"])
        #expect(try store.storedFolder(id: "g")?.title == "New")
        #expect(try store.storedFolder(id: "g")?.lastSyncedAt != nil)
    }
}

/// Answers the sync endpoints from an in-memory backend.
final class FakeBackend: HTTPTransport {
    var folderDiff: FolderContents = FolderContents()
    var remoteDocument: WrDocument?
    private(set) var sentDocuments: [[String: Any]] = []
    private(set) var sentFolders: [[String: Any]] = []
    private(set) var stepSyncBodies: [[String: Any]] = []
    private(set) var deletedIds: [String] = []
    var deleteFails = false

    func data(for request: URLRequest) async throws -> (Data, URLResponse) {
        let path = request.url!.path()
        let body = request.httpBody.flatMap { try? JSONSerialization.jsonObject(with: $0) as? [String: Any] } ?? [:]
        var status = 200
        var response = Data()

        switch path {
        case "/api/docs/workspace/document/folder/diff":
            // Like the backend, deleted documents aren't returned.
            var contents = folderDiff
            contents.documents.removeAll { deletedIds.contains($0.id) }
            response = try JSONEncoder().encode(contents)
        case "/api/docs/workspace/document":
            sentDocuments += body["documents"] as? [[String: Any]] ?? []
            response = Data("Accepted".utf8)
        case "/api/docs/workspace/folder":
            sentFolders += body["folders"] as? [[String: Any]] ?? []
            response = Data("Accepted".utf8)
        case let path where path.hasSuffix("/document/delete"):
            if deleteFails {
                status = 500
            } else {
                deletedIds += body["documentIds"] as? [String] ?? []
                response = Data("Deleted".utf8)
            }
        case let path where path.hasSuffix("/steps/sync"):
            stepSyncBodies.append(body)
            response = Data(#"{"serverTimestamp":777,"updatedSteps":[],"deletedIds":[]}"#.utf8)
        case let path where path.hasPrefix("/api/docs/workspace/w/document/"):
            if let remoteDocument {
                response = try JSONEncoder().encode(remoteDocument)
            } else {
                status = 404
            }
        default:
            status = 404
        }
        return (response, HTTPURLResponse(url: request.url!, statusCode: status, httpVersion: nil, headerFields: nil)!)
    }
}

@Suite struct SyncedRepositoryTests {
    private func makeRepository(_ backend: FakeBackend) -> SyncedDocumentsRepository {
        let client = APIClient(transport: backend, tokenStore: InMemoryTokenStore(accessToken: "a"), baseURL: URL(string: "https://x.io")!)
        return SyncedDocumentsRepository(
            local: makeStore(),
            remote: RemoteDocumentsRepository(client: client, workspaceId: "w"),
            api: SyncAPI(client: client, workspaceId: "w")
        )
    }

    @Test func folderSyncDownloadsSendsAndMarksSynced() async throws {
        let backend = FakeBackend()
        backend.folderDiff = FolderContents(
            folders: [Folder(id: "f", parentId: "root", title: "Remote folder", workspaceId: "w")],
            documents: [WrDocument(id: "remote", title: "From the backend", workspaceId: "w", parentId: "root")]
        )
        let repository = makeRepository(backend)
        let draft = WrDocument(id: "draft", title: "Written offline", workspaceId: "w", parentId: "root")
        try repository.local.store(draft)

        try await repository.syncFolder("root")

        let contents = try await repository.folderContents(folderId: "root")
        #expect(Set(contents.documents.map(\.id)) == ["remote", "draft"])
        #expect(contents.folders.map(\.title) == ["Remote folder"])
        #expect(backend.sentDocuments.map { $0["id"] as? String } == ["draft"])
        #expect(try repository.local.storedDocument(id: "draft")?.isOutdated == false)
    }

    @Test func documentsDeletedElsewhereLeaveThisDevice() async throws {
        let backend = FakeBackend()
        let repository = makeRepository(backend)
        try repository.local.store(WrDocument(id: "old", title: "Synced", workspaceId: "w", lastUpdatedAt: 10, parentId: "root", lastSyncedAt: 20))

        try await repository.syncFolder("root")

        #expect(try repository.local.storedDocument(id: "old") == nil)
    }

    @Test func foldersAreSentInTheBackendFormat() async throws {
        let backend = FakeBackend()
        let repository = makeRepository(backend)

        let folder = try await repository.createFolder(title: "Ideas", parentId: "root")

        let sent = try #require(backend.sentFolders.first)
        #expect(sent["id"] as? String == folder.id)
        #expect(Set(sent.keys) == ["id", "parentId", "title", "createdAt", "lastUpdatedAt", "workspaceId", "favorite", "itemCount"])
        #expect((sent["createdAt"] as? String)?.hasSuffix("Z") == true)
        #expect(try repository.local.storedFolder(id: folder.id)?.lastSyncedAt != nil)
    }

    @Test func openingMergesTheBackendCopy() async throws {
        let backend = FakeBackend()
        let repository = makeRepository(backend)
        try repository.local.store(WrDocument(id: "d", title: "Doc", workspaceId: "w", content: [
            step("a", "local a", at: 0, updated: 100),
        ], lastUpdatedAt: 100, parentId: "root", lastSyncedAt: 100))
        backend.remoteDocument = WrDocument(id: "d", title: "Doc", workspaceId: "w", content: [
            step("a", "edited elsewhere", at: 0, updated: 200),
            step("b", "added elsewhere", at: 1, updated: 200),
        ], lastUpdatedAt: 200, parentId: "root")

        let merged = try #require(try await repository.fetchAndMerge(documentId: "d"))

        #expect(merged.content.map(\.text) == ["edited elsewhere", "added elsewhere"])
        #expect(try repository.local.storedDocument(id: "d")?.content.count == 2)
        // Nothing new the second time.
        #expect(try await repository.fetchAndMerge(documentId: "d") == nil)
    }

    @Test func pushingStepsSendsChangesAndMarksTheDocument() async throws {
        let backend = FakeBackend()
        let repository = makeRepository(backend)
        let document = WrDocument(id: "d", title: "Doc", workspaceId: "w", content: [step("a", "hi", at: 0, updated: 5)], lastUpdatedAt: 5, parentId: "root")
        try repository.local.store(document)

        let serverTime = try await repository.pushSteps(document: document, changes: document.content, deletions: ["gone"], lastSyncTimestamp: 1)

        #expect(serverTime == 777)
        let body = try #require(backend.stepSyncBodies.first)
        #expect(body["documentId"] as? String == "d")
        #expect(body["lastSyncTimestamp"] as? Int == 1)
        #expect(body["deletions"] as? [String] == ["gone"])
        let change = try #require((body["changes"] as? [[String: Any]])?.first)
        #expect((change["storyStep"] as? [String: Any])?["id"] as? String == "a")
        #expect(try repository.local.storedDocument(id: "d")?.isOutdated == false)
    }

    @Test func folderDatesReadBothFormats() throws {
        let iso = try JSONDecoder().decode(Folder.self, from: Data(#"{"id":"f","parentId":"root","title":"T","workspaceId":"w","createdAt":"2026-01-02T10:00:00Z","lastUpdatedAt":"2026-01-02T10:00:00.250Z","itemCount":1}"#.utf8))
        #expect(iso.createdAt != nil)
        #expect(iso.lastUpdatedAt?.timeIntervalSince1970.truncatingRemainder(dividingBy: 1) == 0.25)

        let millis = try JSONDecoder().decode(Folder.self, from: Data(#"{"id":"f","parentId":"root","title":"T","workspaceId":"w","createdAt":1700000000000}"#.utf8))
        #expect(millis.createdAt == Date(millis: 1_700_000_000_000))
    }
}

@Suite struct SQLiteStoreTests {
    private let directory = FileManager.default.temporaryDirectory.appending(path: "sqlite \(UUID().uuidString)", directoryHint: .isDirectory)

    @Test func documentsSurviveReopeningTheDatabase() async throws {
        let first = LocalDocumentsRepository(directory: directory, workspaceId: "w", seedsWelcome: false)
        let document = try await first.createDocument(title: "Kept", parentId: Folder.rootId)
        let folder = try await first.createFolder(title: "Folder", parentId: Folder.rootId)

        let reopened = LocalDocumentsRepository(directory: directory, workspaceId: "w", seedsWelcome: false)

        #expect(try await reopened.document(id: document.id).title == "Kept")
        #expect(try await reopened.folderContents(folderId: Folder.rootId).folders.map(\.id) == [folder.id])
    }

    @Test func editsWriteOnlyTheChangedSteps() async throws {
        let store = LocalDocumentsRepository(directory: directory, workspaceId: "w", seedsWelcome: false)
        var document = WrDocument(id: "d", title: "Doc", workspaceId: "w", content: [
            StoryStep(id: "a", type: .text, text: "one", position: 0),
            StoryStep(id: "b", type: .text, text: "two", position: 1),
            StoryStep(id: "c", type: .text, text: "three", position: 2),
        ], parentId: Folder.rootId)
        try store.store(document)

        document.title = "Renamed"
        var edited = document.content[0]
        edited.text = "one!"
        try store.saveEdit(document: document, changedSteps: [edited], deletedStepIds: ["c"])

        let saved = try #require(try store.storedDocument(id: "d"))
        #expect(saved.title == "Renamed")
        #expect(saved.content.map(\.text) == ["one!", "two"])
    }

    @Test func stepsKeepTheirContent() throws {
        let store = LocalDocumentsRepository(directory: directory, workspaceId: "w", seedsWelcome: false)
        let rich = StoryStep(
            id: "s",
            type: .checkItem,
            text: "Link",
            checked: true,
            tags: [TagInfo(tag: "H2")],
            spans: [SpanInfo(start: 0, end: 4, span: "LINK", extra: "https://x.io")],
            position: 3.5,
            documentLink: DocumentLink(id: "other", title: "Other"),
            lastUpdatedAt: 42
        )
        try store.store(WrDocument(id: "d", title: "", workspaceId: "w", content: [rich], parentId: Folder.rootId))

        #expect(try store.storedDocument(id: "d")?.content == [rich])
    }

    @Test func oldJsonFilesAreImportedOnce() async throws {
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        let old = WrDocument(id: "old", title: "From a file", workspaceId: "local", content: [
            StoryStep(id: "t", type: .title, text: "From a file", position: 0),
        ], parentId: Folder.rootId)
        try JSONEncoder().encode(old).write(to: directory.appending(path: "From a file_old.wrdoc.json"))
        let folder = Folder(id: "f", parentId: Folder.rootId, title: "Old folder", workspaceId: "local")
        try JSONEncoder().encode(folder).write(to: directory.appending(path: "Old folder_f.wrfolder.json"))

        let store = LocalDocumentsRepository(directory: directory)

        let contents = try await store.folderContents(folderId: Folder.rootId)
        // The welcome document isn't added to a space that already had documents.
        #expect(contents.documents.map(\.id) == ["old"])
        #expect(contents.folders.map(\.id) == ["f"])
        #expect(FileManager.default.fileExists(atPath: directory.appending(path: "imported-json/From a file_old.wrdoc.json").path(percentEncoded: false)))
        #expect(!FileManager.default.fileExists(atPath: directory.appending(path: "From a file_old.wrdoc.json").path(percentEncoded: false)))
    }
}

@Suite struct DeleteDocumentTests {
    private func makeRepository(_ backend: FakeBackend) -> SyncedDocumentsRepository {
        let client = APIClient(transport: backend, tokenStore: InMemoryTokenStore(accessToken: "a"), baseURL: URL(string: "https://x.io")!)
        return SyncedDocumentsRepository(
            local: makeStore(),
            remote: RemoteDocumentsRepository(client: client, workspaceId: "w"),
            api: SyncAPI(client: client, workspaceId: "w")
        )
    }

    @Test func privateSpaceRemovesTheDocument() async throws {
        let store = makeStore()
        let document = try await store.createDocument(title: "Bye", parentId: Folder.rootId)

        try await store.deleteDocument(id: document.id)

        #expect(try store.storedDocument(id: document.id) == nil)
        #expect(try await store.folderContents(folderId: Folder.rootId).documents.isEmpty)
    }

    @Test func openSpaceDeletesOnTheBackendAndHere() async throws {
        let backend = FakeBackend()
        let repository = makeRepository(backend)
        try repository.local.store(WrDocument(id: "d", title: "Bye", workspaceId: "w", parentId: "root", lastSyncedAt: Date.nowMillis))

        try await repository.deleteDocument(id: "d")

        #expect(backend.deletedIds == ["d"])
        #expect(try repository.local.storedDocument(id: "d") == nil)
    }

    @Test func offlineDeletionIsHiddenAndSentWithTheNextSync() async throws {
        let backend = FakeBackend()
        backend.deleteFails = true
        let repository = makeRepository(backend)
        let document = WrDocument(id: "d", title: "Bye", workspaceId: "w", parentId: "root", lastSyncedAt: Date.nowMillis)
        try repository.local.store(document)

        try await repository.deleteDocument(id: "d")

        #expect(try repository.local.storedDocument(id: "d")?.deleted == true)
        #expect(try await repository.folderContents(folderId: "root").documents.isEmpty)

        // The backend still has it: the sync must neither bring it back nor lose the deletion.
        backend.deleteFails = false
        backend.folderDiff = FolderContents(documents: [document])
        try await repository.syncFolder("root")

        #expect(backend.deletedIds == ["d"])
        #expect(try await repository.folderContents(folderId: "root").documents.isEmpty)
    }
}
