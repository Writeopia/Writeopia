import Foundation
import Testing
@testable import DocumentsFeature
import WrData
import WrModels
import WrNetwork
import WrStorage

@MainActor
@Suite struct TabPlaceTests {
    let work = Folder(id: "work", parentId: Folder.rootId, title: "Work", workspaceId: "w")
    let ideas = Folder(id: "ideas", parentId: "work", title: "Ideas", workspaceId: "w")

    private func repository() -> FakeDocumentsRepository {
        let repository = FakeDocumentsRepository()
        repository.foldersById = [work.id: work, ideas.id: ideas]
        return repository
    }

    @Test func placeIsTheScreenOnTop() {
        let router = DocumentsRouter()
        #expect(router.place == nil)

        router.path = [.folder(work)]
        #expect(router.place == .folder(id: "work"))
        #expect(router.shownDocumentId == nil)

        router.path.append(.document(id: "d", title: "Plan", searchQuery: "words"))
        #expect(router.place == .document(id: "d", title: "Plan"))
        #expect(router.shownDocumentId == "d")

        router.openFavorites()
        #expect(router.place == .favorites)
        router.openGraph()
        #expect(router.place == .graph)

        // A tag is not kept: the tab comes back at the root.
        router.path = [.tag(DocumentTag(id: "t", workspaceId: "w", name: "Ideas"))]
        #expect(router.place == nil)
    }

    @Test func openingAFolderPlaceRebuildsItsAncestors() async {
        let router = DocumentsRouter()
        await router.open(place: .folder(id: "ideas"), repository: repository())

        #expect(router.path == [.folder(work), .folder(ideas)])
        #expect(router.shownFolderId == "ideas")
        #expect(router.place == .folder(id: "ideas"))
    }

    @Test func aFolderThatIsGoneLeavesTheRoot() async {
        let router = DocumentsRouter()
        await router.open(place: .folder(id: "deleted"), repository: repository())

        #expect(router.path.isEmpty)
        #expect(router.shownFolderId == Folder.rootId)
    }

    @Test func openingADocumentPlaceShowsItOverTheRoot() async {
        let router = DocumentsRouter()
        router.path = [.folder(work)]
        await router.open(place: .document(id: "d", title: "Plan"), repository: repository())

        #expect(router.path == [.document(id: "d", title: "Plan")])
        #expect(router.shownDocumentId == "d")
    }

    @Test func placesSurviveCoding() throws {
        let places: [TabPlace] = [.document(id: "d", title: "Plan \"q\""), .folder(id: "f"), .favorites, .graph]
        let data = try JSONEncoder().encode(places)
        #expect(try JSONDecoder().decode([TabPlace].self, from: data) == places)
    }
}
