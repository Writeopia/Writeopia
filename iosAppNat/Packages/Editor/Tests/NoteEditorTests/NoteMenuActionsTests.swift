import Foundation
import Testing
@testable import NoteEditor
import WrData
import WrModels
import WrNetwork

/// "Favorite" and "Move to" of the editor menu, like `NoteGlobalActionsMenu` of the Compose app.
@Suite struct NoteMenuActionsTests {
    private func loaded(_ repository: OneDocumentRepository) async -> NoteEditorViewModel {
        let viewModel = NoteEditorViewModel(documentId: "d", repository: repository)
        await viewModel.loadDocument()
        return viewModel
    }

    private func document(isFavorite: Bool = false, parentId: String = "work") -> WrDocument {
        WrDocument(
            id: "d",
            title: "Plan",
            workspaceId: "w",
            content: [StoryStep(id: "t", type: .title, text: "Plan", position: Rank.at(0))],
            isFavorite: isFavorite,
            parentId: parentId
        )
    }

    @Test func theFavoriteOfTheDocumentShowsWhenItOpens() async {
        let viewModel = await loaded(OneDocumentRepository(document(isFavorite: true)))

        #expect(viewModel.isFavorite)
    }

    @Test func favoriteTogglesAndIsSaved() async {
        let repository = OneDocumentRepository(document())
        let viewModel = await loaded(repository)

        await viewModel.toggleFavorite()
        #expect(viewModel.isFavorite)
        #expect(viewModel.currentDocument.isFavorite)

        await viewModel.toggleFavorite()
        #expect(!viewModel.isFavorite)
        #expect(repository.favorites == [true, false])
    }

    @Test func aSecondTapWhileTheFavoriteIsSavingIsIgnored() async {
        let repository = OneDocumentRepository(document())
        repository.favoriteDelay = .milliseconds(100)
        let viewModel = await loaded(repository)

        async let first: Void = viewModel.toggleFavorite()
        while !viewModel.isSavingFavorite { await Task.yield() }
        await viewModel.toggleFavorite()
        await first

        #expect(repository.favorites == [true])
        #expect(viewModel.isFavorite)
        #expect(!viewModel.isSavingFavorite)
    }

    @Test func aFavoriteThatFailsKeepsTheStateAndSaysSo() async {
        let repository = OneDocumentRepository(document())
        let viewModel = await loaded(repository)
        repository.failsActions = true

        await viewModel.toggleFavorite()

        #expect(!viewModel.isFavorite)
        #expect(viewModel.menuActionError != nil)
    }

    @Test func moveToChangesTheFolderOfTheDocument() async {
        let repository = OneDocumentRepository(document(parentId: "work"))
        let viewModel = await loaded(repository)
        #expect(viewModel.folderId == "work")

        await viewModel.move(to: "archive")

        #expect(repository.moves == ["archive"])
        #expect(viewModel.folderId == "archive")
        // What's saved next keeps the new folder.
        #expect(viewModel.currentDocument.parentId == "archive")
    }

    @Test func movingToTheFolderItIsInDoesNothing() async {
        let repository = OneDocumentRepository(document(parentId: "work"))
        let viewModel = await loaded(repository)

        await viewModel.move(to: "work")

        #expect(repository.moves.isEmpty)
    }

    @Test func aMoveThatFailsKeepsTheFolderAndSaysSo() async {
        let repository = OneDocumentRepository(document(parentId: "work"))
        let viewModel = await loaded(repository)
        repository.failsActions = true

        await viewModel.move(to: "archive")

        #expect(viewModel.folderId == "work")
        #expect(viewModel.menuActionError != nil)
    }
}

