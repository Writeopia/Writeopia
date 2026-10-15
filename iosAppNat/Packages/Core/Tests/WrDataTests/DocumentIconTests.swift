import Foundation
import Testing
@testable import WrData
import WrModels
import WrNetwork
import WrStorage

private func makeLocal() -> LocalDocumentsRepository {
    LocalDocumentsRepository(
        directory: FileManager.default.temporaryDirectory.appending(path: "icons \(UUID().uuidString)", directoryHint: .isDirectory),
        workspaceId: "w",
        seedsWelcome: false
    )
}

/// Collects the `documentIconChanged` notifications posted while it lives.
private final class IconChanges {
    private(set) var received: [(id: String, icon: IconInfo?)] = []
    private var token: NSObjectProtocol?

    init() {
        token = NotificationCenter.default.addObserver(forName: .documentIconChanged, object: nil, queue: nil) { [weak self] note in
            guard let id = note.object as? String else { return }
            self?.received.append((id, note.userInfo?["icon"] as? IconInfo))
        }
    }

    func stop() {
        token.map(NotificationCenter.default.removeObserver)
        token = nil
    }
}

@Suite(.serialized) struct LocalDocumentIconTests {
    @Test func theIconIsStoredAndAnnounced() async throws {
        let local = makeLocal()
        let document = try await local.createDocument(title: "Recipes", parentId: Folder.rootId)
        let changes = IconChanges()
        defer { changes.stop() }

        try await local.updateDocumentIcon(id: document.id, icon: IconInfo(label: "favorites", tint: -65536))

        #expect(try await local.document(id: document.id).icon == IconInfo(label: "favorites", tint: -65536))
        #expect(changes.received.last?.id == document.id)
        #expect(changes.received.last?.icon == IconInfo(label: "favorites", tint: -65536))

        try await local.updateDocumentIcon(id: document.id, icon: nil)
        #expect(try await local.document(id: document.id).icon == nil)
        #expect(changes.received.last?.icon == nil)
    }

    @Test func savingTheEditorsOlderCopyKeepsTheIcon() async throws {
        let local = makeLocal()
        let opened = try await local.document(id: try await local.createDocument(title: "Recipes", parentId: Folder.rootId).id)

        try await local.updateDocumentIcon(id: opened.id, icon: IconInfo(label: "home", tint: nil))
        // The editor still has the copy it opened, without the icon.
        try await local.save(opened)
        try local.saveEdit(document: opened, changedSteps: [], deletedStepIds: [])

        #expect(try await local.document(id: opened.id).icon == IconInfo(label: "home", tint: nil))
    }

    @Test func aMissingDocumentIsNotFound() async throws {
        await #expect(throws: APIError.notFound) {
            try await makeLocal().updateDocumentIcon(id: "nope", icon: nil)
        }
    }
}

@Suite struct SyncedDocumentIconTests {
    private func makeRepository(_ backend: FakeBackend) -> SyncedDocumentsRepository {
        let client = APIClient(transport: backend, tokenStore: InMemoryTokenStore(accessToken: "a"), baseURL: URL(string: "https://x.io")!)
        return SyncedDocumentsRepository(
            local: makeLocal(),
            remote: RemoteDocumentsRepository(client: client, workspaceId: "w"),
            api: SyncAPI(client: client, workspaceId: "w")
        )
    }

    @Test func theIconGoesWithTheNextSyncOfTheDocument() async throws {
        let backend = FakeBackend()
        let repository = makeRepository(backend)
        let document = try await repository.createDocument(title: "Recipes", parentId: Folder.rootId)
        #expect(try repository.local.storedDocument(id: document.id)?.isOutdated == false)

        try await repository.updateDocumentIcon(id: document.id, icon: IconInfo(label: "zap", tint: -256))
        #expect(try repository.local.storedDocument(id: document.id)?.isOutdated == true)

        try await repository.syncFolder(Folder.rootId)

        let sent = try #require(backend.sentDocuments.last { $0["id"] as? String == document.id })
        #expect((sent["icon"] as? [String: Any])?["label"] as? String == "zap")
        #expect(try repository.local.storedDocument(id: document.id)?.icon == IconInfo(label: "zap", tint: -256))
    }

    @Test func savingTheEditorsOlderCopyKeepsTheIcon() async throws {
        let repository = makeRepository(FakeBackend())
        let opened = try await repository.createDocument(title: "Recipes", parentId: Folder.rootId)

        try await repository.updateDocumentIcon(id: opened.id, icon: IconInfo(label: "home", tint: nil))
        try await repository.save(opened)
        try repository.saveEdit(document: opened, changedSteps: [], deletedStepIds: [])

        #expect(try repository.local.storedDocument(id: opened.id)?.icon == IconInfo(label: "home", tint: nil))
    }
}
