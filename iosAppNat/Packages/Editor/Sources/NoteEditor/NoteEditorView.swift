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
    @State private var showAiDialog = false
    private let fallbackTitle: String
    private let openDocumentLink: (DocumentLink) -> Void

    public init(
        documentId: String,
        title: String,
        repository: DocumentsRepository,
        aiClient: AiStreaming? = nil,
        openDocumentLink: @escaping (DocumentLink) -> Void = { _ in }
    ) {
        _viewModel = State(initialValue: NoteEditorViewModel(documentId: documentId, repository: repository, aiClient: aiClient))
        fallbackTitle = title
        self.openDocumentLink = openDocumentLink
    }

    public var body: some View {
        Group {
            if viewModel.hasLoaded {
                WriteopiaEditor(manager: viewModel.writeopiaManager)
                    .safeAreaInset(edge: .bottom, spacing: 0) {
                        EditorBottomMenu(showsAi: viewModel.isAiAvailable) {
                            showAiDialog = true
                        }
                    }
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
        // The editor has its own bottom menu, like the Compose app.
        .toolbar(.hidden, for: .tabBar)
        .sheet(isPresented: $showAiDialog) {
            AiDialog { command, mode in
                viewModel.runAi(command, mode: mode)
            }
        }
        .onDisappear { viewModel.cancelAi() }
        .task {
            viewModel.writeopiaManager.onDocumentLinkClick = openDocumentLink
            await viewModel.loadDocument()
        }
    }
}
#endif
