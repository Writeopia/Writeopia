#if canImport(UIKit)
import Drawing
import PhotosUI
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
    @State private var showSelectedLinesAiDialog = false
    @State private var showMenu = false
    @State private var showPublish = false
    @State private var showPremium = false
    @State private var drawingTarget: DrawingTarget?
    @Environment(\.colorScheme) private var colorScheme
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
        publishing: DocumentPublishing? = nil,
        imageUploader: ImageUploading? = nil,
        isPremium: Bool = false,
        openDocumentLink: @escaping (DocumentLink) -> Void = { _ in }
    ) {
        _viewModel = State(initialValue: NoteEditorViewModel(
            documentId: documentId,
            repository: repository,
            aiClient: aiClient,
            publishing: publishing,
            imageUploader: imageUploader,
            isPremium: isPremium
        ))
        fallbackTitle = title
        self.openDocumentLink = openDocumentLink
    }

    /// Drawings in the document, like `DrawingPreviewDrawer` of the Compose app: tap to edit,
    /// long press to delete.
    private var customDrawers: [Int: CustomStepDrawer] {
        [
            StoryType.drawing.number: { step in
                AnyView(
                    DrawingPreview(json: step.text) {
                        guard !viewModel.isLocked else { return }
                        drawingTarget = DrawingTarget(stepId: step.id, drawing: DrawingData.fromJson(step.text))
                    }
                    .contextMenu {
                        if !viewModel.isLocked {
                            Button("Delete drawing", systemImage: "trash", role: .destructive) {
                                viewModel.writeopiaManager.removeStep(stepId: step.id)
                            }
                        }
                    }
                )
            },
        ]
    }

    private func addImage(_ item: PhotosPickerItem) {
        Task {
            do {
                guard let data = try await item.loadTransferable(type: Data.self) else { return }
                await viewModel.addImage(data)
            } catch {
                viewModel.imageError = "The image couldn't be loaded from the library."
            }
        }
    }

    /// Black ink on light backgrounds, white on dark ones.
    private var defaultDrawingColor: Int {
        colorScheme == .dark ? DrawingColor.argb(0xFFFF_FFFF) : Stroke.black
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
                WriteopiaEditor(manager: viewModel.writeopiaManager, customDrawers: customDrawers)
                    .safeAreaInset(edge: .bottom, spacing: 0) {
                        // A locked document can't be edited, so its menu is hidden. While lines are
                        // selected, the selection menu takes the place of the regular one.
                        if viewModel.isLocked {
                            EmptyView()
                        } else if viewModel.writeopiaManager.hasSelectedLines {
                            SelectionMenu(
                                manager: viewModel.writeopiaManager,
                                showsAi: viewModel.isAiAvailable,
                                onAiClick: { showSelectedLinesAiDialog = true },
                                onLinkToPage: { Task { await viewModel.linkSelectionToNewPage() } },
                                onCopy: { viewModel.copySelectedLines(to: SystemLinePasteboard()) },
                                onCut: { withAnimation(.snappy) { viewModel.cutSelectedLines(to: SystemLinePasteboard()) } }
                            )
                            .transition(.move(edge: .bottom).combined(with: .opacity))
                        } else {
                            EditorBottomMenu(
                                manager: viewModel.writeopiaManager,
                                showsAi: viewModel.isAiAvailable,
                                onAiClick: { showAiDialog = true },
                                onLinkClick: linkClick,
                                onDrawingClick: { drawingTarget = DrawingTarget(stepId: nil, drawing: nil) },
                                onImagePicked: addImage
                            )
                            .transition(.move(edge: .bottom).combined(with: .opacity))
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
        .toolbar {
            ToolbarItem(placement: .principal) {
                TitleView(
                    title: viewModel.hasLoaded ? viewModel.title : fallbackTitle,
                    isLocked: viewModel.isLocked,
                    isPublished: viewModel.isPublished
                )
            }
            if viewModel.hasLoaded {
                ToolbarItem(placement: .primaryAction) {
                    Button {
                        showMenu = true
                    } label: {
                        Label("More", systemImage: "ellipsis")
                    }
                    .accessibilityIdentifier("editor.more")
                }
            }
        }
        .sheet(isPresented: $showMenu) {
            NoteMenuSheet(viewModel: viewModel) {
                // Publishing is for premium users in the open space, like in the Compose app.
                if viewModel.canPublish {
                    showPublish = true
                } else {
                    showPremium = true
                }
            }
        }
        .sheet(isPresented: $showPublish) {
            PublishSheet(viewModel: viewModel)
        }
        .alert("Premium Feature", isPresented: $showPremium) {
            Button("OK", role: .cancel) {}
        } message: {
            Text("This feature is only available for premium users using an online workspace")
        }
        .animation(.snappy, value: viewModel.isLocked)
        // The editor has its own bottom menu, like the Compose app.
        .toolbar(.hidden, for: .tabBar)
        .sheet(isPresented: $showAiDialog) {
            AiDialog { command, mode in
                viewModel.runAi(command, mode: mode)
            }
        }
        .sheet(isPresented: $showSelectedLinesAiDialog) {
            AiDialog(fixedMode: .selectedLines) { command, mode in
                viewModel.runAi(command, mode: mode)
            }
        }
        .alert(
            "Could not add the image",
            isPresented: Binding(get: { viewModel.imageError != nil }, set: { if !$0 { viewModel.imageError = nil } })
        ) {
            Button("OK", role: .cancel) {}
        } message: {
            Text(viewModel.imageError ?? "")
        }
        .alert(
            "Could not create the page",
            isPresented: Binding(get: { viewModel.linkError != nil }, set: { if !$0 { viewModel.linkError = nil } })
        ) {
            Button("OK", role: .cancel) {}
        } message: {
            Text(viewModel.linkError ?? "")
        }
        .animation(.snappy, value: viewModel.writeopiaManager.hasSelectedLines)
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
        .fullScreenCover(item: $drawingTarget) { target in
            DrawingEditorView(drawing: target.drawing, defaultColor: defaultDrawingColor) { drawing in
                viewModel.saveDrawing(drawing, stepId: target.stepId)
            }
        }
        .onDisappear {
            viewModel.cancelAi()
            // Save and send what's pending before leaving.
            Task { await viewModel.flush() }
        }
        .task {
            viewModel.writeopiaManager.onDocumentLinkClick = openDocumentLink
            await viewModel.loadDocument()
            await viewModel.mergeFromBackend()
            await viewModel.loadPublishState()
        }
        .onChange(of: viewModel.writeopiaManager.changeCount) {
            viewModel.documentChanged()
        }
    }
}
/// A drawing being created (`stepId == nil`) or edited.
private struct DrawingTarget: Identifiable {
    let id = UUID()
    let stepId: String?
    let drawing: DrawingData?
}

/// Title of the navigation bar, with the lock and published marks of the Compose top bar.
private struct TitleView: View {
    let title: String
    let isLocked: Bool
    let isPublished: Bool

    var body: some View {
        HStack(spacing: 6) {
            Text(title.isEmpty ? "Untitled" : title)
                .font(.headline)
                .lineLimit(1)
            if isLocked {
                Image(systemName: "lock.fill")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .accessibilityLabel("Locked")
            }
            if isPublished {
                Image(systemName: "globe")
                    .font(.caption)
                    .foregroundStyle(WrColors.accent)
                    .accessibilityLabel("Published")
            }
        }
    }
}
#endif
