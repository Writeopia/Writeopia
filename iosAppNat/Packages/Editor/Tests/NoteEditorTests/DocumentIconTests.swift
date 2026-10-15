import Foundation
import Testing
@testable import NoteEditor
import WrData
import WrModels

@Suite struct DocumentIconEditorTests {
    private func document(icon: IconInfo?) -> WrDocument {
        WrDocument(id: "d", title: "Plan", workspaceId: "w", content: [
            StoryStep(id: "t", type: .title, text: "Plan", position: 0),
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
}
