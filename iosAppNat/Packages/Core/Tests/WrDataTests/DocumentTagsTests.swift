import Foundation
import Testing
@testable import WrData
import WrModels

@Suite struct DocumentTagsStoreTests {
    private func makeStore() -> LocalDocumentsRepository {
        LocalDocumentsRepository(
            directory: FileManager.default.temporaryDirectory.appending(path: "tags \(UUID().uuidString)", directoryHint: .isDirectory),
            workspaceId: "w",
            seedsWelcome: false
        )
    }

    @Test func aDocumentKeepsItsTagIdsAndANewSaveReplacesThem() throws {
        let store = makeStore()
        let document = WrDocument(id: "d", title: "T", workspaceId: "w", parentId: "root", tagIds: ["t1", "t2"])

        try store.store(document)
        #expect(try store.storedDocument(id: "d")?.tagIds == ["t1", "t2"])

        var fewer = document
        fewer.tagIds = ["t2"]
        try store.store(fewer)
        #expect(try store.storedDocument(id: "d")?.tagIds == ["t2"])
        #expect(try store.storedDocuments(inFolder: "root").first?.tagIds == ["t2"])
    }

    @Test func aStepEditKeepsTheTagsTheEditorCarries() throws {
        let store = makeStore()
        let document = WrDocument(id: "d", title: "T", workspaceId: "w", parentId: "root", tagIds: ["t1"])
        try store.store(document)

        try store.saveEdit(document: document, changedSteps: [StoryStep(id: "s", type: .text, text: "hi", position: 0)], deletedStepIds: [])

        #expect(try store.storedDocument(id: "d")?.tagIds == ["t1"])
    }

    @Test func theDocumentsOfATagAndTheWholeWorkspaceAreListed() async throws {
        let store = makeStore()
        try store.store(WrDocument(id: "a", title: "A", workspaceId: "w", lastUpdatedAt: 1, parentId: "root", tagIds: ["t1"]))
        try store.store(WrDocument(id: "b", title: "B", workspaceId: "w", lastUpdatedAt: 2, parentId: "f", tagIds: ["t1", "t2"]))
        try store.store(WrDocument(id: "c", title: "C", workspaceId: "w", lastUpdatedAt: 3, parentId: "root"))
        try store.store(Folder(id: "f", parentId: "root", title: "F", workspaceId: "w"))

        #expect(try await store.documents(withTag: "t1").map(\.id) == ["b", "a"])
        #expect(try await store.documents(withTag: "t2").map(\.id) == ["b"])
        let all = try await store.allContents()
        #expect(all.folders.map(\.id) == ["f"])
        #expect(all.documents.map(\.id) == ["c", "b", "a"])
    }

    @Test func theCatalogueRoundTripsWithTombstonesAndOutdatedTags() throws {
        let store = makeStore()
        try store.storeTag(DocumentTag(id: "a", workspaceId: "w", name: "Beta", description: "B", lastUpdatedAt: 2, lastSyncedAt: 2))
        try store.storeTag(DocumentTag(id: "b", workspaceId: "w", name: "Alpha", description: "A", lastUpdatedAt: 3))
        try store.storeTag(DocumentTag(id: "c", workspaceId: "w", name: "Gone", lastUpdatedAt: 4, deleted: true))

        #expect(try store.storedTags().map(\.name) == ["Alpha", "Beta"])
        #expect(try store.storedAllTags().map(\.name) == ["Alpha", "Beta", "Gone"])
        #expect(try store.storedOutdatedTags().map(\.id).sorted() == ["b", "c"])

        try store.softDeleteTag(id: "b")
        #expect(try store.storedTags().map(\.name) == ["Beta"])
        #expect(try store.storedTag(id: "b")?.deleted == true)
    }
}

@Suite struct TagConflictHandlerTests {
    private func makeStore() -> LocalDocumentsRepository {
        LocalDocumentsRepository(
            directory: FileManager.default.temporaryDirectory.appending(path: "tags \(UUID().uuidString)", directoryHint: .isDirectory),
            workspaceId: "w",
            seedsWelcome: false
        )
    }

    @Test func theCopyUpdatedLastWinsAndLocalOnlyTagsAreSent() throws {
        let store = makeStore()
        let stale = DocumentTag(id: "a", workspaceId: "w", name: "Old", lastUpdatedAt: 1)
        let fresh = DocumentTag(id: "b", workspaceId: "w", name: "Renamed here", lastUpdatedAt: 9)
        let mineOnly = DocumentTag(id: "c", workspaceId: "w", name: "Mine", lastUpdatedAt: 5)
        for tag in [stale, fresh, mineOnly] { try store.storeTag(tag) }

        let toSend = try TagConflictHandler.handle(
            local: [stale, fresh, mineOnly],
            remote: [
                DocumentTag(id: "a", workspaceId: "w", name: "New", lastUpdatedAt: 2),
                DocumentTag(id: "b", workspaceId: "w", name: "Renamed there", lastUpdatedAt: 2),
                DocumentTag(id: "d", workspaceId: "w", name: "Theirs", lastUpdatedAt: 3),
            ],
            store: store
        )

        #expect(toSend.map(\.id) == ["b", "c"])
        #expect(try store.storedTag(id: "a")?.name == "New")
        #expect(try store.storedTag(id: "b")?.name == "Renamed here")
        #expect(try store.storedTag(id: "d")?.lastSyncedAt == 3)
    }

    @Test func aTagDeletedHereIsNotBroughtBack() throws {
        let store = makeStore()
        let gone = DocumentTag(id: "a", workspaceId: "w", name: "Gone", lastUpdatedAt: 10, deleted: true)
        try store.storeTag(gone)

        let toSend = try TagConflictHandler.handle(
            local: [gone],
            remote: [DocumentTag(id: "a", workspaceId: "w", name: "Gone", lastUpdatedAt: 4)],
            store: store
        )

        #expect(toSend.map(\.id) == ["a"])
        #expect(try store.storedTag(id: "a")?.deleted == true)
    }
}
