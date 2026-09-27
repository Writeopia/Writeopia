#if canImport(UIKit)
import SwiftUI
import UIKit
import WriteopiaUI
import WrData
import WrDesign
import WrModels

/// The document screen: the Writeopia editor for the document with `documentId`.
public struct NoteEditorView: View {
    @State private var viewModel: NoteEditorViewModel
    private let fallbackTitle: String
    private let openDocumentLink: (DocumentLink) -> Void

    public init(
        documentId: String,
        title: String,
        repository: DocumentsRepository,
        openDocumentLink: @escaping (DocumentLink) -> Void = { _ in }
    ) {
        _viewModel = State(initialValue: NoteEditorViewModel(documentId: documentId, repository: repository))
        fallbackTitle = title
        self.openDocumentLink = openDocumentLink
    }

    public var body: some View {
        Group {
            if viewModel.hasLoaded {
                WriteopiaEditor(manager: viewModel.writeopiaManager)
            } else if let errorMessage = viewModel.errorMessage {
                ContentUnavailableView {
                    Label("Something went wrong", systemImage: "exclamationmark.triangle")
                } description: {
                    Text(errorMessage)
                } actions: {
                    Button("Try again") { Task { await viewModel.loadDocument() } }
                }
            } else {
                ProgressView()
                    .controlSize(.large)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            }
        }
        .background(Color(uiColor: .systemBackground))
        .navigationTitle(viewModel.hasLoaded ? viewModel.title : fallbackTitle)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItemGroup(placement: .keyboard) {
                Spacer()
                Button("Done") {
                    UIApplication.shared.sendAction(#selector(UIResponder.resignFirstResponder), to: nil, from: nil, for: nil)
                }
            }
        }
        .task {
            viewModel.writeopiaManager.onDocumentLinkClick = openDocumentLink
            await viewModel.loadDocument()
        }
    }
}
#endif
