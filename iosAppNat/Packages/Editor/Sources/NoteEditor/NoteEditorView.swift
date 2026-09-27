#if canImport(UIKit)
import SwiftUI
import UIKit
import Writeopia
import WriteopiaUI
import WrData
import WrDesign
import WrModels

/// The document screen: the Writeopia editor for the document with `documentId`.
public struct NoteEditorView: View {
    @State private var viewModel: NoteEditorViewModel
    @State private var showAiDialog = false
    /// Selection waiting for a URL. Kept here because the alert takes the focus from the text.
    @State private var linkSelection: StepSelection?
    @State private var linkURL = ""
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

    /// Asks for a URL for the selected text, or removes the link when it already has one.
    private func linkClick() {
        let manager = viewModel.writeopiaManager
        guard let selection = manager.textSelection, !selection.isEmpty else { return }

        if manager.isSpanActive(.link) {
            manager.setLink(nil, for: selection)
        } else {
            linkURL = ""
            linkSelection = selection
        }
    }

    /// Adds `https://` when the scheme is missing; nil for empty input.
    static func normalizedURL(_ input: String) -> String? {
        let trimmed = input.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return nil }
        return trimmed.contains("://") ? trimmed : "https://" + trimmed
    }

    public var body: some View {
        Group {
            if viewModel.hasLoaded {
                WriteopiaEditor(manager: viewModel.writeopiaManager)
                    .safeAreaInset(edge: .bottom, spacing: 0) {
                        EditorBottomMenu(
                            manager: viewModel.writeopiaManager,
                            showsAi: viewModel.isAiAvailable,
                            onAiClick: { showAiDialog = true },
                            onLinkClick: linkClick
                        )
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
        .alert(
            "Add link",
            isPresented: Binding(get: { linkSelection != nil }, set: { if !$0 { linkSelection = nil } })
        ) {
            TextField("https://", text: $linkURL)
                .textInputAutocapitalization(.never)
                .keyboardType(.URL)
                .autocorrectionDisabled()
            Button("Cancel", role: .cancel) {}
            Button("Add") {
                if let selection = linkSelection, let url = Self.normalizedURL(linkURL) {
                    viewModel.writeopiaManager.setLink(url, for: selection)
                }
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
