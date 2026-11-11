import Foundation
import Testing
@testable import SettingsFeature
import WrData
import WrModels
import WrNetwork

/// The pictures of the backend in memory.
private final class FakeHeaderImages: HeaderImagesManaging {
    var images: [HeaderImage]
    var failsToDelete = false
    var failsToList = false
    private(set) var deleted: [String] = []

    init(_ images: [HeaderImage]) {
        self.images = images
    }

    func pictures() async throws -> [HeaderImage] {
        if failsToList { throw APIError.offline }
        return images
    }

    func deleteHeaderImage(id: String) async throws {
        if failsToDelete { throw APIError.unexpectedStatus(500) }
        deleted.append(id)
        images.removeAll { $0.id == id }
    }
}

@Suite struct HeaderImagesSettingsModelTests {
    private func picture(_ id: String) -> HeaderImage {
        HeaderImage(id: id, url: "https://cdn.test/header-images/w/\(id).png", createdAt: 0)
    }

    @Test func thePicturesOfTheWorkspaceAreListed() async {
        let model = HeaderImagesSettingsModel(source: FakeHeaderImages([picture("a"), picture("b")]))

        await model.load()

        #expect(model.images.map(\.id) == ["a", "b"])
        #expect(model.errorMessage == nil)
    }

    @Test func outsideAnOnlineWorkspaceThereIsNothingToManage() async {
        let model = HeaderImagesSettingsModel(source: nil)

        await model.load()

        #expect(!model.isAvailable)
        #expect(model.images.isEmpty)
    }

    @Test func aListingThatFailsSaysWhy() async {
        let source = FakeHeaderImages([picture("a")])
        source.failsToList = true
        let model = HeaderImagesSettingsModel(source: source)

        await model.load()

        #expect(model.errorMessage != nil)
    }

    @Test func aDeletedPictureLeavesTheList() async {
        let source = FakeHeaderImages([picture("a"), picture("b")])
        let model = HeaderImagesSettingsModel(source: source)
        await model.load()

        await model.delete(picture("a"))

        #expect(source.deleted == ["a"])
        #expect(model.images.map(\.id) == ["b"])
        #expect(model.deleting.isEmpty)
    }

    @Test func aPictureThatCouldNotBeDeletedStays() async {
        let source = FakeHeaderImages([picture("a")])
        source.failsToDelete = true
        let model = HeaderImagesSettingsModel(source: source)
        await model.load()

        await model.delete(picture("a"))

        #expect(model.images.map(\.id) == ["a"])
        #expect(model.deleteError != nil)
        #expect(model.deleting.isEmpty)
    }
}

@Suite struct TagsSettingsModelTests {
    private func makeStore() -> LocalDocumentsRepository {
        LocalDocumentsRepository(
            directory: FileManager.default.temporaryDirectory.appending(path: "tags settings \(UUID().uuidString)", directoryHint: .isDirectory),
            workspaceId: "w",
            seedsWelcome: false
        )
    }

    @Test func aNewTagIsStoredTrimmed() async throws {
        let store = makeStore()
        let model = TagsSettingsModel(repository: store, workspaceId: "w")

        await model.create(name: "  Research ", description: " Papers to read ")

        #expect(model.tags.map(\.name) == ["Research"])
        #expect(model.tags.first?.description == "Papers to read")
        #expect(model.tags.first?.workspaceId == "w")
    }

    @Test func anEditedTagKeepsItsIdAndIsOutdatedForTheSync() async throws {
        let store = makeStore()
        let tag = DocumentTag(id: "t1", workspaceId: "w", name: "Ideas", createdAt: 1, lastUpdatedAt: 1, lastSyncedAt: 1)
        try await store.saveTag(tag)
        let model = TagsSettingsModel(repository: store, workspaceId: "w")
        await model.load()

        await model.edit(tag, name: "Big ideas", description: "The ones worth a document")

        let stored = try #require(try store.storedAllTags().first { $0.id == "t1" })
        #expect(stored.name == "Big ideas")
        #expect(stored.description == "The ones worth a document")
        #expect(stored.lastUpdatedAt > 1)
        #expect(try store.storedOutdatedTags().map(\.id) == ["t1"])
    }

    @Test func aDeletedTagBecomesATombstone() async throws {
        let store = makeStore()
        let ideas = DocumentTag(id: "t1", workspaceId: "w", name: "Ideas", createdAt: 1, lastUpdatedAt: 1, lastSyncedAt: 1)
        try await store.saveTag(ideas)
        try await store.saveTag(DocumentTag(id: "t2", workspaceId: "w", name: "Work"))
        let model = TagsSettingsModel(repository: store, workspaceId: "w")
        await model.load()

        await model.delete(ideas)

        #expect(model.tags.map(\.name) == ["Work"])
        let tombstone = try #require(try store.storedAllTags().first { $0.id == "t1" })
        #expect(tombstone.deleted)
        #expect(try store.storedOutdatedTags().contains { $0.id == "t1" })
    }

    @Test func namesAreTakenIgnoringCaseExceptByTheTagBeingEdited() {
        let tags = [
            DocumentTag(id: "t1", workspaceId: "w", name: "Ideas"),
            DocumentTag(id: "t2", workspaceId: "w", name: "Work"),
        ]

        #expect(TagsSettingsModel.isNameTaken(" ideas ", in: tags))
        #expect(!TagsSettingsModel.isNameTaken("Ideas", in: tags, except: "t1"))
        #expect(!TagsSettingsModel.isNameTaken("Plans", in: tags))
        #expect(!TagsSettingsModel.isNameTaken("  ", in: tags))
    }
}
