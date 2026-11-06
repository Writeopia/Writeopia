import Foundation
import Testing
@testable import NoteEditor
import Writeopia
import WriteopiaUI
import WrModels

/// The find bar of the editor, and a document opened by the words the global search found it by.
@Suite struct InDocumentSearchViewModelTests {
    private let document = WrDocument(id: "d", title: "Groceries", workspaceId: "w", content: [
        StoryStep(id: "t", type: .title, text: "Groceries", position: Rank.at(0)),
        StoryStep(id: "a", type: .text, text: "Buy some fresh bananas", position: Rank.at(1)),
        StoryStep(id: "b", type: .checkItem, text: "and milk", checked: false, position: Rank.at(2)),
    ])

    @Test func aDocumentOpenedFromTheSearchShowsTheFindBarOnItsWords() async {
        let viewModel = NoteEditorViewModel(documentId: "d", repository: OneDocumentRepository(document), searchQuery: "milk")
        #expect(!viewModel.isSearchVisible)

        await viewModel.loadDocument()

        #expect(viewModel.isSearchVisible)
        #expect(viewModel.searchText == "milk")
        #expect(viewModel.searchResultCount == 1)
        #expect(viewModel.writeopiaManager.activeSearchStepId == "b")
    }

    @Test func theFindBarSearchesWhatIsTypedAndClosingClearsIt() async {
        let viewModel = NoteEditorViewModel(documentId: "d", repository: OneDocumentRepository(document))
        await viewModel.loadDocument()
        #expect(!viewModel.isSearchVisible)

        viewModel.showSearch()
        viewModel.searchText = "banana"
        #expect(viewModel.isSearchVisible)
        #expect(viewModel.searchResultCount == 1)
        #expect(viewModel.searchResultIndex == 0)

        viewModel.hideSearch()
        #expect(!viewModel.isSearchVisible)
        #expect(viewModel.searchResultCount == 0)
        #expect(viewModel.searchText.isEmpty)
    }

    @Test func anEmptyQueryFromTheSearchOpensNothing() async {
        let viewModel = NoteEditorViewModel(documentId: "d", repository: OneDocumentRepository(document), searchQuery: "")
        await viewModel.loadDocument()
        #expect(!viewModel.isSearchVisible)
    }
}
