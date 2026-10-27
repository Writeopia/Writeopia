import Foundation
import Testing
@testable import NoteEditor
import WrData
import WrModels

@Suite struct DocumentIconEditorTests {
    private func document(icon: IconInfo?) -> WrDocument {
        WrDocument(id: "d", title: "Plan", workspaceId: "w", content: [
            StoryStep(id: "t", type: .title, text: "Plan", position: Rank.at(0)),
        ], icon: icon)
    }

    @Test func theIconOfTheDocumentShowsByTheTitle() async {
        let viewModel = NoteEditorViewModel(
            documentId: "d",
            repository: OneDocumentRepository(document(icon: IconInfo(label: "home", tint: -65536)))
        )
        await viewModel.loadDocument()

        #expect(viewModel.writeopiaManager.documentIcon == IconInfo(label: "home", tint: -65536))
    }

    @Test func anIconChosenInTheSidebarShowsInTheOpenEditor() async {
        let viewModel = NoteEditorViewModel(documentId: "d", repository: OneDocumentRepository(document(icon: nil)))
        await viewModel.loadDocument()
        #expect(viewModel.writeopiaManager.documentIcon == nil)

        // Another document changing its icon changes nothing here.
        viewModel.documentIconChanged(id: "other", icon: IconInfo(label: "zap", tint: nil))
        #expect(viewModel.writeopiaManager.documentIcon == nil)

        viewModel.documentIconChanged(id: "d", icon: IconInfo(label: "zap", tint: nil))
        #expect(viewModel.writeopiaManager.documentIcon == IconInfo(label: "zap", tint: nil))

        viewModel.documentIconChanged(id: "d", icon: nil)
        #expect(viewModel.writeopiaManager.documentIcon == nil)
    }

    @Test func anIconChosenInTheEditorMenuIsSavedAndShown() async {
        let repository = OneDocumentRepository(document(icon: nil))
        let viewModel = NoteEditorViewModel(documentId: "d", repository: repository)
        await viewModel.loadDocument()

        await viewModel.changeIcon(IconInfo(label: "zap", tint: -65536))
        #expect(viewModel.documentIcon == IconInfo(label: "zap", tint: -65536))

        await viewModel.changeIcon(nil)
        #expect(viewModel.documentIcon == nil)
        #expect(repository.icons == [IconInfo(label: "zap", tint: -65536), nil])
    }

    @Test func anIconThatFailsToSaveGoesBackAndSaysSo() async {
        let repository = OneDocumentRepository(document(icon: IconInfo(label: "home", tint: nil)))
        let viewModel = NoteEditorViewModel(documentId: "d", repository: repository)
        await viewModel.loadDocument()
        repository.failsActions = true

        await viewModel.changeIcon(IconInfo(label: "zap", tint: nil))

        #expect(viewModel.documentIcon == IconInfo(label: "home", tint: nil))
        #expect(viewModel.menuActionError != nil)
    }
}
