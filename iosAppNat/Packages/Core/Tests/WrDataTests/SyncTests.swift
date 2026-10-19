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
    /// The JSON the search answers; the search fails when nil.
    var searchResponse: String?
    private(set) var searchRequests = 0
    private(set) var sentDocuments: [[String: Any]] = []
    private(set) var sentFolders: [[String: Any]] = []
    private(set) var sentTags: [[String: Any]] = []
    private(set) var stepSyncBodies: [[String: Any]] = []
    /// What the step sync answers; the server time is 777.
    var stepSyncUpdatedSteps: [StoryStep] = []
    var stepSyncDeletedIds: [String] = []
    /// The tombstones a device sent, as `[stepId: documentId]`.
    private(set) var deletedStepTombstones: [[String: String]] = []
    /// The ids of the steps deleted elsewhere, answered to `/steps/deleted`.
    var deletedStepIds: [String] = []
    private(set) var deletedStepsSince: [Int64] = []
    /// Every request, in order, as "METHOD path".
    private(set) var requestLog: [String] = []
    private(set) var deletedIds: [String] = []
    private(set) var deletedFolderIds: [String] = []
    private(set) var favorites: [String: Bool] = [:]
    var deleteFails = false
    /// Step deletions and step syncs are refused while these are set.
    var stepDeleteFails = false
    var stepSyncFails = false
    /// Folders sent while this is set are refused, like when the backend can't be reached.
    var folderSendFails = false
    /// Runs while folders are being sent, before the backend answers.
    var onFolderSend: (() -> Void)?

    func data(for request: URLRequest) async throws -> (Data, URLResponse) {
        let path = request.url!.path()
        let body = request.httpBody.flatMap { try? JSONSerialization.jsonObject(with: $0) as? [String: Any] } ?? [:]
        var status = 200
        var response = Data()
        requestLog.append("\(request.httpMethod ?? "GET") \(path)")

        switch path {
        case let path where path.hasSuffix("/steps/delete"):
            if stepDeleteFails {
                status = 500
            } else {
                deletedStepTombstones += (body["tombstones"] as? [[String: String]]) ?? []
                response = Data("Accepted".utf8)
            }
        case let path where path.hasSuffix("/steps/deleted"):
            let since = URLComponents(url: request.url!, resolvingAgainstBaseURL: false)?
                .queryItems?.first { $0.name == "since" }?.value.flatMap(Int64.init) ?? -1
            deletedStepsSince.append(since)
            let ids = String(decoding: try JSONEncoder().encode(deletedStepIds), as: UTF8.self)
            response = Data(#"{"stepIds":\#(ids),"serverTimestamp":778}"#.utf8)
        case "/api/docs/workspace/document/folder/diff":
            // Like the backend, deleted documents aren't returned.
            var contents = folderDiff
            contents.documents.removeAll { deletedIds.contains($0.id) }
            contents.folders.removeAll { deletedFolderIds.contains($0.id) }
            response = try JSONEncoder().encode(contents)
        case "/api/docs/workspace/document":
            sentDocuments += body["documents"] as? [[String: Any]] ?? []
            response = Data("Accepted".utf8)
        case "/api/docs/workspace/tag":
            sentTags += body["tags"] as? [[String: Any]] ?? []
            response = Data("Accepted".utf8)
        case "/api/docs/workspace/folder":
            if folderSendFails {
                status = 500
            } else {
                onFolderSend?()
                sentFolders += body["folders"] as? [[String: Any]] ?? []
                response = Data("Accepted".utf8)
            }
        case let path where request.httpMethod == "DELETE" && path.contains("/folder/"):
            if deleteFails {
                status = 500
            } else {
                deletedFolderIds.append(String(path.split(separator: "/").last ?? ""))
            }
        case let path where path.hasSuffix("/favorite"):
            let id = path.split(separator: "/").dropLast().last.map(String.init) ?? ""
            favorites[id] = body["favorite"] as? Bool
        case let path where path.hasSuffix("/document/delete"):
            if deleteFails {
                status = 500
            } else {
                deletedIds += body["documentIds"] as? [String] ?? []
                response = Data("Deleted".utf8)
            }
        case let path where path.hasSuffix("/steps/sync"):
            if stepSyncFails {
                status = 500
                break
            }
            stepSyncBodies.append(body)
            let steps = String(decoding: try JSONEncoder().encode(stepSyncUpdatedSteps), as: UTF8.self)
            let deleted = String(decoding: try JSONEncoder().encode(stepSyncDeletedIds), as: UTF8.self)
            response = Data(#"{"serverTimestamp":777,"updatedSteps":\#(steps),"deletedIds":\#(deleted)}"#.utf8)
        case "/api/docs/workspace/w/document/search":
            searchRequests += 1
            if let searchResponse {
                response = Data(searchResponse.utf8)
            } else {
                status = 500
            }
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

    @Test func folderSyncAppliesTheTagCatalogueAndSendsTheOutdatedTags() async throws {
        let backend = FakeBackend()
        backend.folderDiff = FolderContents(tags: [
            DocumentTag(id: "remote", workspaceId: "w", name: "Remote", description: "Made elsewhere", lastUpdatedAt: 5),
        ])
        let repository = makeRepository(backend)
        try repository.local.storeTag(DocumentTag(id: "mine", workspaceId: "w", name: "Mine", lastUpdatedAt: 7))

        try await repository.syncFolder("root")

        #expect(backend.sentTags.map { $0["id"] as? String } == ["mine"])
        #expect(Set(backend.sentTags[0].keys) == ["id", "workspaceId", "name", "description", "createdAt", "lastUpdatedAt", "deleted"])
        #expect(try repository.local.storedTag(id: "remote")?.description == "Made elsewhere")
        #expect(try repository.local.storedTag(id: "remote")?.isOutdated == false)
        #expect(try repository.local.storedTag(id: "mine")?.isOutdated == false)
    }

    @Test func savingATagStoresItAndSendsItAtOnce() async throws {
        let backend = FakeBackend()
        let repository = makeRepository(backend)

        try await repository.saveTag(DocumentTag(workspaceId: "", name: "Ideas", description: "Things to try"))

        let sent = try #require(backend.sentTags.first)
        #expect(sent["name"] as? String == "Ideas")
        #expect(sent["workspaceId"] as? String == "w")
        #expect(try await repository.tags().map(\.name) == ["Ideas"])
        #expect(try repository.local.storedOutdatedTags().isEmpty)
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

    @Test func folderSyncSendsTheDeletionsThenTheStepsThenTheMetadata() async throws {
        let backend = FakeBackend()
        let repository = makeRepository(backend)
        // Synced at 100, then a line edited and another deleted here.
        let document = WrDocument(id: "d", title: "Doc", workspaceId: "w", content: [
            step("kept", "old", at: 0, updated: 50),
            step("edited", "new", at: 1, updated: 200),
            step("gone", "bye", at: 2, updated: 50),
        ], lastUpdatedAt: 200, parentId: "root", lastSyncedAt: 100)
        try repository.local.store(document)
        var edited = document
        edited.content.removeAll { $0.id == "gone" }
        try repository.saveEdit(document: edited, changedSteps: [], deletedStepIds: ["gone"])

        try await repository.syncFolder("root")

        #expect(backend.deletedStepTombstones == [["stepId": "gone", "documentId": "d"]])
        // Only the step edited since the last sync went, then the metadata without content.
        let changes = try #require(backend.stepSyncBodies.first?["changes"] as? [[String: Any]])
        #expect(changes.map { ($0["storyStep"] as? [String: Any])?["id"] as? String } == ["edited"])
        let sent = try #require(backend.sentDocuments.first)
        #expect(sent["id"] as? String == "d")
        #expect((sent["content"] as? [Any])?.isEmpty == true)
        let order = backend.requestLog.filter { $0.hasSuffix("/steps/delete") || $0.hasSuffix("/steps/sync") || $0.hasSuffix("/workspace/document") }
        #expect(order == ["POST /api/docs/workspace/w/steps/delete", "POST /api/docs/workspace/w/document/d/steps/sync", "POST /api/docs/workspace/document"], "\(backend.requestLog)")
        // The tombstone went, the document is in sync, and nothing was written back.
        #expect(try repository.local.storedStepTombstones().isEmpty)
        let stored = try #require(try repository.local.storedDocument(id: "d"))
        #expect(!stored.isOutdated)
        #expect(stored.content.map(\.id) == ["kept", "edited"])
    }

    @Test func folderSyncStopsWhenTheDeletionsCantBeSent() async throws {
        let backend = FakeBackend()
        let repository = makeRepository(backend)
        try repository.local.store(WrDocument(id: "d", title: "Doc", workspaceId: "w", content: [step("a", "a", at: 0, updated: 5)], lastUpdatedAt: 5, parentId: "root"))
        try repository.saveEdit(document: WrDocument(id: "d", title: "Doc", workspaceId: "w", lastUpdatedAt: 6, parentId: "root"), changedSteps: [], deletedStepIds: ["a"])
        backend.stepDeleteFails = true

        await #expect(throws: (any Error).self) { try await repository.syncFolder("root") }

        #expect(backend.stepSyncBodies.isEmpty)
        #expect(backend.sentDocuments.isEmpty)
        #expect(try repository.local.storedStepTombstones().map(\.stepId) == ["a"])
    }

    @Test func stepsDeletedElsewhereLeaveAndNeverComeBack() async throws {
        let backend = FakeBackend()
        let repository = makeRepository(backend)
        try repository.local.store(WrDocument(id: "d", title: "Doc", workspaceId: "w", content: [
            step("a", "a", at: 0, updated: 10),
            step("b", "b", at: 1, updated: 10),
        ], lastUpdatedAt: 10, parentId: "root", lastSyncedAt: 20))
        // The backend still lists "b" in a stale copy, but says it was deleted.
        backend.folderDiff = FolderContents(documents: [WrDocument(id: "d", title: "Doc", workspaceId: "w", content: [
            step("a", "a", at: 0, updated: 10),
            step("b", "b", at: 1, updated: 10),
        ], lastUpdatedAt: 10, parentId: "root", lastSyncedAt: 20)])
        backend.deletedStepIds = ["b"]

        try await repository.syncFolder("root")

        #expect(try repository.local.storedDocument(id: "d")?.content.map(\.id) == ["a"])
        #expect(try repository.local.storedStepTombstones().isEmpty)
        // The first time everything is asked for; afterwards only what came since.
        #expect(backend.deletedStepsSince == [0])
        try await Task.sleep(for: .seconds(2.1))
        try await repository.syncFolder("root")
        #expect(backend.deletedStepsSince.count == 2)
        #expect(backend.deletedStepsSince[1] > 0)
    }

    @Test func receivedDocumentsAreMergedStepByStep() async throws {
        let backend = FakeBackend()
        let repository = makeRepository(backend)
        try repository.local.store(WrDocument(id: "d", title: "Doc", workspaceId: "w", content: [
            step("a", "local a", at: 0, updated: 5),
            step("b", "local b", at: 1, updated: 1),
            step("deleted-here", "x", at: 2, updated: 1),
        ], lastUpdatedAt: 10, parentId: "root", lastSyncedAt: 10))
        try repository.saveEdit(
            document: WrDocument(id: "d", title: "Doc", workspaceId: "w", lastUpdatedAt: 10, parentId: "root", lastSyncedAt: 10),
            changedSteps: [],
            deletedStepIds: ["deleted-here"]
        )
        backend.folderDiff = FolderContents(documents: [WrDocument(id: "d", title: "Renamed elsewhere", workspaceId: "w", content: [
            step("a", "server a", at: 0, updated: 3),
            step("b", "server b", at: 1, updated: 9),
            step("c", "server c", at: 3, updated: 9),
            step("deleted-here", "back?", at: 2, updated: 9),
        ], lastUpdatedAt: 20, parentId: "root", lastSyncedAt: 20)])

        try await repository.syncFolder("root")

        let stored = try #require(try repository.local.storedDocument(id: "d"))
        #expect(stored.title == "Renamed elsewhere")
        #expect(stored.content.map(\.text) == ["local a", "server b", "server c"], "\(stored.content)")
        #expect(!stored.isOutdated)
    }

    @Test func aDocumentStillOutdatedHereKeepsLookingOutdatedAfterAMerge() async throws {
        let backend = FakeBackend()
        backend.stepSyncFails = true
        let repository = makeRepository(backend)
        try repository.local.store(WrDocument(id: "d", title: "Doc", workspaceId: "w", content: [
            step("a", "edited here", at: 0, updated: 150),
        ], lastUpdatedAt: 200, parentId: "root", lastSyncedAt: 100))
        backend.folderDiff = FolderContents(documents: [WrDocument(id: "d", title: "Renamed elsewhere", workspaceId: "w", content: [
            step("a", "older elsewhere", at: 0, updated: 50),
        ], lastUpdatedAt: 300, parentId: "root", lastSyncedAt: 300)])

        try await repository.syncFolder("root")

        let stored = try #require(try repository.local.storedDocument(id: "d"))
        #expect(stored.title == "Renamed elsewhere")
        #expect(stored.content.first?.text == "edited here")
        #expect(stored.lastUpdatedAt == 200)
        #expect(stored.lastSyncedAt == 100)
        #expect(stored.isOutdated)
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

    @Test func aStepDeletedInTheEditorLeavesATombstoneAndNoSyncBringsItBack() throws {
        let store = LocalDocumentsRepository(directory: directory, workspaceId: "w", seedsWelcome: false)
        let document = WrDocument(id: "d", title: "Doc", workspaceId: "w", content: [
            StoryStep(id: "a", type: .text, text: "one", position: 0),
            StoryStep(id: "b", type: .text, text: "two", position: 1),
        ], parentId: Folder.rootId)
        try store.store(document)

        try store.saveEdit(document: document, changedSteps: [], deletedStepIds: ["b"])

        let tombstone = try #require(try store.storedStepTombstones().first)
        #expect(tombstone.stepId == "b")
        #expect(tombstone.documentId == "d")
        #expect(tombstone.workspaceId == "w")
        // Whatever a sync writes, whole document or steps: it stays deleted.
        try store.store(document)
        #expect(try store.storedDocument(id: "d")?.content.map(\.id) == ["a"])
        try store.storeSteps(document.content, documentId: "d")
        #expect(try store.storedDocument(id: "d")?.content.map(\.id) == ["a"])
        // Once the backend has the deletion, the tombstone goes.
        try store.deleteStepTombstones(["b"])
        #expect(try store.storedStepTombstones().isEmpty)
    }

    @Test func theEditorMayBringADeletedStepBack() throws {
        let store = LocalDocumentsRepository(directory: directory, workspaceId: "w", seedsWelcome: false)
        let document = WrDocument(id: "d", title: "Doc", workspaceId: "w", content: [
            StoryStep(id: "a", type: .text, text: "one", position: 0),
        ], parentId: Folder.rootId)
        try store.store(document)
        try store.saveEdit(document: document, changedSteps: [], deletedStepIds: ["a"])

        // An undo in the editor: its word wins over the tombstone.
        try store.saveEdit(document: document, changedSteps: document.content, deletedStepIds: [])

        #expect(try store.storedDocument(id: "d")?.content.map(\.id) == ["a"])
        #expect(try store.storedStepTombstones().isEmpty)
    }

    @Test func stepsDeletedElsewhereGoWithoutATombstone() throws {
        let store = LocalDocumentsRepository(directory: directory, workspaceId: "w", seedsWelcome: false)
        try store.store(WrDocument(id: "d", title: "Doc", workspaceId: "w", content: [
            StoryStep(id: "a", type: .text, text: "one", position: 0),
            StoryStep(id: "b", type: .text, text: "two", position: 1),
        ], parentId: Folder.rootId))

        try store.deleteSteps(ids: ["b"])

        #expect(try store.storedDocument(id: "d")?.content.map(\.id) == ["a"])
        #expect(try store.storedStepTombstones().isEmpty)
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

@Suite struct SearchTests {
    private func makeRepository(_ backend: FakeBackend) -> SyncedDocumentsRepository {
        let client = APIClient(transport: backend, tokenStore: InMemoryTokenStore(accessToken: "a"), baseURL: URL(string: "https://x.io")!)
        return SyncedDocumentsRepository(
            local: makeStore(),
            remote: RemoteDocumentsRepository(client: client, workspaceId: "w"),
            api: SyncAPI(client: client, workspaceId: "w")
        )
    }

    @Test func oneRequestFindsTheDocumentsAndPresentationsWithTheOnesOnTheDevice() async throws {
        let backend = FakeBackend()
        let document = String(decoding: try JSONEncoder().encode(WrDocument(id: "doc", title: "Sky notes", workspaceId: "w", parentId: "root")), as: UTF8.self)
        backend.searchResponse = """
            {"documents":[\(document)],"presentations":[{"id":"cloud","documentId":"doc","title":"Sky deck","createdAt":5}]}
            """
        let repository = makeRepository(backend)
        try await repository.savePresentation(Presentation(id: "device", documentId: "doc", title: "Sky by Ollama", createdAt: 3, slides: [Slide(title: "Sky")]))

        let found = try await repository.searchAll(query: "sky")

        #expect(backend.searchRequests == 1)
        #expect(found.documents.map(\.id) == ["doc"])
        #expect(found.presentations.map(\.id) == ["cloud", "device"])
        #expect(found.presentations.allSatisfy { $0.slides.isEmpty })
    }

    @Test func offlineTheDeviceAnswers() async throws {
        let backend = FakeBackend()
        let repository = makeRepository(backend)
        try await repository.savePresentation(Presentation(id: "device", documentId: "doc", title: "Sky by Ollama", createdAt: 3, slides: [Slide(title: "Sky")]))

        let found = try await repository.searchAll(query: "sky")

        #expect(found.presentations.map(\.id) == ["device"])
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

    @Test func offlineMoveIsSentWithTheNextSyncOfAnyFolder() async throws {
        let backend = FakeBackend()
        let repository = makeRepository(backend)
        let folder = Folder(id: "f", parentId: "root", title: "Ideas", workspaceId: "w", lastSyncedAt: Date())
        try repository.local.store(folder)
        let document = WrDocument(id: "d", title: "Note", workspaceId: "w", parentId: "root", lastSyncedAt: Date.nowMillis)
        try repository.local.store(document)

        // The backend refuses the move (offline): it's kept locally, to be sent later.
        try await repository.moveDocument(id: "d", toFolder: "f")
        #expect(try repository.local.storedDocument(id: "d")?.parentId == "f")
        #expect(try repository.local.storedDocument(id: "d")?.isOutdated == true)

        // Syncing the root, not the folder the document moved to, still sends it.
        try await repository.syncFolder("root")

        let sent = try #require(backend.sentDocuments.first)
        #expect(sent["id"] as? String == "d")
        #expect(sent["parentId"] as? String == "f")
        #expect(try repository.local.storedDocument(id: "d")?.isOutdated == false)
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

@Suite struct SelectionActionsTests {
    private func makeRepository(_ backend: FakeBackend) -> SyncedDocumentsRepository {
        let client = APIClient(transport: backend, tokenStore: InMemoryTokenStore(accessToken: "a"), baseURL: URL(string: "https://x.io")!)
        return SyncedDocumentsRepository(
            local: makeStore(),
            remote: RemoteDocumentsRepository(client: client, workspaceId: "w"),
            api: SyncAPI(client: client, workspaceId: "w")
        )
    }

    @Test func duplicateCopiesDocumentsAndFoldersWithNewIds() async throws {
        let store = makeStore()
        let document = WrDocument(id: "d", title: "Notes", workspaceId: "w", content: [
            StoryStep(id: "s", type: .text, text: "Hello", position: 0),
        ], parentId: "root")
        try store.store(document)
        try store.store(Folder(id: "f", parentId: "root", title: "Projects", workspaceId: "w"))
        try store.store(WrDocument(id: "inner", title: "Inside", workspaceId: "w", parentId: "f"))

        try await store.duplicate(ids: ["d", "f"])

        let root = try await store.folderContents(folderId: "root")
        #expect(root.documents.filter { $0.title == "Notes" }.count == 2)
        let copy = try #require(root.documents.first { $0.title == "Notes" && $0.id != "d" })
        #expect(copy.content.map(\.text) == ["Hello"])
        #expect(copy.content.first?.id != "s")

        let folderCopy = try #require(root.folders.first { $0.title == "Projects" && $0.id != "f" })
        #expect(try await store.folderContents(folderId: folderCopy.id).documents.map(\.title) == ["Inside"])
    }

    @Test func favoriteMarksDocumentsAndFolders() async throws {
        let store = makeStore()
        try store.store(WrDocument(id: "d", title: "", workspaceId: "w", parentId: "root"))
        try store.store(Folder(id: "f", parentId: "root", title: "F", workspaceId: "w"))

        try await store.setFavorite(ids: ["d", "f"], favorite: true)

        #expect(try store.storedDocument(id: "d")?.isFavorite == true)
        #expect(try store.storedFolder(id: "f")?.favorite == true)
        #expect(try store.storedDocument(id: "d")?.isOutdated == true)
    }

    @Test func deletingAFolderDeletesWhatsInside() async throws {
        let store = makeStore()
        try store.store(Folder(id: "f", parentId: "root", title: "F", workspaceId: "w"))
        try store.store(Folder(id: "g", parentId: "f", title: "G", workspaceId: "w"))
        try store.store(WrDocument(id: "d", title: "", workspaceId: "w", parentId: "g"))

        try await store.deleteItems(ids: ["f"])

        #expect(try store.storedFolder(id: "f") == nil)
        #expect(try store.storedFolder(id: "g") == nil)
        #expect(try store.storedDocument(id: "d") == nil)
    }

    @Test func openSpaceSendsFavoritesAndDeletions() async throws {
        let backend = FakeBackend()
        let repository = makeRepository(backend)
        try repository.local.store(WrDocument(id: "d", title: "", workspaceId: "w", parentId: "root"))
        try repository.local.store(Folder(id: "f", parentId: "root", title: "F", workspaceId: "w"))

        try await repository.setFavorite(ids: ["d"], favorite: true)
        #expect(backend.favorites["d"] == true)

        try await repository.deleteItems(ids: ["d", "f"])
        #expect(backend.deletedIds == ["d"])
        #expect(backend.deletedFolderIds == ["f"])
        #expect(try repository.local.storedFolder(id: "f") == nil)
    }

    @Test func folderDeletionIsRetriedWithTheNextSync() async throws {
        let backend = FakeBackend()
        backend.deleteFails = true
        let repository = makeRepository(backend)
        let folder = Folder(id: "f", parentId: "root", title: "F", workspaceId: "w", lastSyncedAt: Date())
        try repository.local.store(folder)

        try await repository.deleteItems(ids: ["f"])
        #expect(try await repository.folderContents(folderId: "root").folders.isEmpty)

        backend.deleteFails = false
        backend.folderDiff = FolderContents(folders: [folder])
        try await repository.syncFolder("root")

        #expect(backend.deletedFolderIds == ["f"])
        #expect(try await repository.folderContents(folderId: "root").folders.isEmpty)
    }
}

@Suite struct MarkdownToDocumentTests {
    @Test func readsTheAiSummaryFormat() throws {
        let markdown = """
        # Weekly summary

        ## Decisions
        - Ship the iOS app
        [] Review the sync
        - [x] Write tests
        Plain **bold** paragraph
        """

        let document = try #require(MarkdownToDocument.read(markdown, parentId: "folder", workspaceId: "w"))

        #expect(document.title == "Weekly summary")
        #expect(document.parentId == "folder")
        #expect(document.content.map(\.type.number) == [11, 0, 16, 10, 10, 0])
        #expect(document.content[1].headingLevel == 2)
        #expect(document.content[3].text == "Review the sync")
        #expect(document.content[4].checked == true)
        #expect(document.content[5].text == "Plain bold paragraph")
    }

    @Test func withoutTitleUsesTheFallback() throws {
        let document = try #require(MarkdownToDocument.read("Just text", parentId: "root", workspaceId: "w"))
        #expect(document.title == "Summary")
        #expect(MarkdownToDocument.read("   \n", parentId: "root", workspaceId: "w") == nil)
    }
}

@Suite struct UserPlanTests {
    @Test func planComesFromTheTier() throws {
        let premium = try JSONDecoder().decode(User.self, from: Data(#"{"id":"u","email":"a@b.io","name":"Ana","tier":"PREMIUM"}"#.utf8))
        #expect(premium.isPremium)
        #expect(premium.planName == "Premium")

        let free = try JSONDecoder().decode(User.self, from: Data(#"{"id":"u","email":"a@b.io","name":"Ana","tier":"FREE"}"#.utf8))
        #expect(!free.isPremium)
        #expect(free.planName == "Free")

        // Backends older than the tier field.
        let unknown = try JSONDecoder().decode(User.self, from: Data(#"{"id":"u","email":"a@b.io","name":"Ana"}"#.utf8))
        #expect(unknown.planName == nil)
        #expect(!unknown.isPremium)
    }
}
