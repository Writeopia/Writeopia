import Drawing
import PhotosUI
import SwiftUI
import Writeopia
import WriteopiaUI
import WrData
import WrDesign
import WrModels

/// Shown while the AI writes into the document, with a button to stop it, like the AI task
/// indicator of the desktop app.
struct AiRunningChip: View {
    let onStop: () -> Void

    var body: some View {
        HStack(spacing: 10) {
            ProgressView()
                .controlSize(.small)
            Text("AI is writing…")
                .font(.footnote.weight(.medium))
            Button("Stop", action: onStop)
                .font(.footnote.weight(.semibold))
                .buttonStyle(.borderless)
                .accessibilityIdentifier("editor.ai.stop")
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 8)
        .background(.regularMaterial, in: Capsule())
        .shadow(color: .black.opacity(0.1), radius: 8, y: 2)
    }
}

/// The document screen: the Writeopia editor for the document with `documentId`.
public struct NoteEditorView: View {
    @State private var viewModel: NoteEditorViewModel
    @State private var showAiDialog = false
    @State private var showSelectedLinesAiDialog = false
    /// Line with the cursor when the AI dialog opened (the focus goes away with the keyboard).
    @State private var aiCursorStepId: String?
    @State private var showMenu = false
    @State private var showPresentations = false
    @State private var showPublish = false
    @State private var showPremium = false
    @State private var drawingTarget: DrawingTarget?
    @Environment(\.colorScheme) private var colorScheme
    @Environment(\.dismiss) private var dismiss
    /// Landscape: no navigation bar nor bottom menu, the side options instead.
    @Environment(\.isWideLayout) private var isWideLayout
    @Environment(\.openEditors) private var openEditors
    /// Selection waiting for a URL. Kept here because the alert takes the focus from the text.
    @State private var sideTab: EditorSideOptions.SideTab?
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
        presentationsEnabled: Bool = false,
        openDocumentLink: @escaping (DocumentLink) -> Void = { _ in }
    ) {
        _viewModel = State(initialValue: NoteEditorViewModel(
            documentId: documentId,
            repository: repository,
            aiClient: aiClient,
            publishing: publishing,
            imageUploader: imageUploader,
            isPremium: isPremium,
            presentationsEnabled: presentationsEnabled
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

    /// Image files dropped on the editor are added where the cursor is, like the Compose app.
    private func dropImages(_ urls: [URL]) -> Bool {
        let images = urls.filter { ImageProcessing.supportedExtensions.contains($0.pathExtension.lowercased()) }
        guard !images.isEmpty, !viewModel.isLocked else { return false }
        for url in images {
            addImage(fileURL: url)
        }
        return true
    }

    /// An image file picked on the Mac, or dropped on the editor.
    private func addImage(fileURL url: URL) {
        let accessing = url.startAccessingSecurityScopedResource()
        defer { if accessing { url.stopAccessingSecurityScopedResource() } }
        guard let data = try? Data(contentsOf: url) else { return }
        Task { await viewModel.addImage(data) }
    }

    private func addImage(_ item: PhotosPickerItem) {
        Task {
            do {
                guard let data = try await item.loadTransferable(type: Data.self) else { return }
                await viewModel.addImage(data)
            } catch {
                viewModel.imageError = String(localized: "The image couldn't be loaded from the library.")
            }
        }
    }

    /// Black ink on light backgrounds, white on dark ones.
    private var defaultDrawingColor: Int {
        colorScheme == .dark ? DrawingColor.argb(0xFFFF_FFFF) : Stroke.black
    }

    /// AI actions for the line that had the cursor: write the section of a heading, or suggest
    /// the next items of a list.
    private var aiCursorActions: [AiCursorAction] {
        let manager = viewModel.writeopiaManager
        guard let stepId = aiCursorStepId, let step = manager.step(withId: stepId) else { return [] }

        var actions: [AiCursorAction] = []
        if manager.headingText(stepId: stepId) != nil {
            actions.append(AiCursorAction(id: "section", title: String(localized: "Write this section"), systemImage: "wand.and.sparkles") {
                viewModel.generateSection(stepId: stepId)
            })
        }
        if step.type.number == StoryType.checkItem.number || step.type.number == StoryType.unorderedListItem.number {
            actions.append(AiCursorAction(id: "suggestItems", title: String(localized: "Suggest more items"), systemImage: "list.bullet.indent") {
                viewModel.suggestListItems(after: stepId)
            })
        }
        return actions
    }

    /// Hardware keyboard shortcuts of the desktop app: Cmd+K sends the line with the cursor to the
    /// AI, Esc stops the AI and removes its suggestions.
    /// The shortcuts of the Compose desktop app. Cmd+Z is left to the text views for now.
    private var keyboardShortcuts: some View {
        let manager = viewModel.writeopiaManager
        let canEdit = viewModel.hasLoaded && !viewModel.isLocked
        return ZStack {
            Button("Ask AI") {
                viewModel.runAi(.prompt, mode: .cursor)
            }
            .keyboardShortcut("k", modifiers: .command)
            .disabled(!viewModel.hasLoaded || !viewModel.isAiAvailable || viewModel.isLocked || viewModel.isAiRunning)

            Button("Stop AI") {
                viewModel.cancelAi()
                viewModel.dismissAiSuggestions()
                withAnimation(.snappy) { manager.clearLineSelection() }
            }
            .keyboardShortcut(.escape, modifiers: [])
            .disabled(!viewModel.isAiRunning && !manager.hasAiSuggestions && !manager.hasSelectedLines)

            Button("Bold") { manager.toggleSpan(.bold) }
                .keyboardShortcut("b", modifiers: .command)
                .disabled(!canEdit)
            Button("Italic") { manager.toggleSpan(.italic) }
                .keyboardShortcut("i", modifiers: .command)
                .disabled(!canEdit)
            Button("Underline") { manager.toggleSpan(.underline) }
                .keyboardShortcut("u", modifiers: .command)
                .disabled(!canEdit)
            Button("Box") { manager.toggleTag(.box) }
                .keyboardShortcut("b", modifiers: [.command, .shift])
                .disabled(!canEdit)
            Button("List item") { manager.toggleType(.unorderedListItem) }
                .keyboardShortcut("-", modifiers: .command)
                .disabled(!canEdit)
            Button("Select all lines") { withAnimation(.snappy) { manager.selectAllLines() } }
                .keyboardShortcut("a", modifiers: [.command, .shift])
                .disabled(!canEdit)
            Button("Link to page") { Task { await viewModel.linkSelectionToNewPage() } }
                .keyboardShortcut("l", modifiers: .command)
                .disabled(!canEdit || !manager.hasSelectedLines)
            // Backspace and Delete remove the selected lines wherever the keyboard focus is;
            // without a selection they reach the text as usual.
            Button("Delete lines") { withAnimation(.snappy) { manager.deleteSelectedLines() } }
                .keyboardShortcut(.delete, modifiers: [])
                .disabled(!canEdit || !manager.hasSelectedLines)
            Button("Delete lines forward") { withAnimation(.snappy) { manager.deleteSelectedLines() } }
                .keyboardShortcut(.deleteForward, modifiers: [])
                .disabled(!canEdit || !manager.hasSelectedLines)
        }
        .opacity(0)
        .allowsHitTesting(false)
        .accessibilityHidden(true)
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

    /// Publishing is for premium users in the open space, like in the Compose app.
    private func publishClick() {
        if viewModel.canPublish {
            showPublish = true
        } else {
            showPremium = true
        }
    }

    private func deleteDocument() {
        Task {
            if await viewModel.deleteDocument() {
                dismiss()
            }
        }
    }

    public var body: some View {
        Group {
            if viewModel.hasLoaded {
                WriteopiaEditor(manager: viewModel.writeopiaManager, customDrawers: customDrawers)
                    .dropDestination(for: URL.self) { urls, _ in dropImages(urls) }
                    .safeAreaInset(edge: .bottom, spacing: 0) {
                        // A locked document can't be edited, so its menu is hidden. While lines are
                        // selected, the selection menu takes the place of the regular one.
                        // In landscape the side options take their place.
                        if viewModel.isLocked || isWideLayout {
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
                                onAiClick: {
                                    aiCursorStepId = viewModel.writeopiaManager.currentTextStep?.step.id
                                    showAiDialog = true
                                },
                                onLinkClick: linkClick,
                                onDrawingClick: { drawingTarget = DrawingTarget(stepId: nil, drawing: nil) },
                                onImagePicked: addImage
                            )
                            .transition(.move(edge: .bottom).combined(with: .opacity))
                        }
                    }
                    .overlay(alignment: .trailing) {
                        if isWideLayout {
                            EditorSideOptions(
                                viewModel: viewModel,
                                onBack: { dismiss() },
                                onAiClick: {
                                    if viewModel.writeopiaManager.hasSelectedLines {
                                        showSelectedLinesAiDialog = true
                                    } else {
                                        showAiDialog = true
                                    }
                                },
                                onLinkClick: linkClick,
                                onDrawingClick: { drawingTarget = DrawingTarget(stepId: nil, drawing: nil) },
                                onImagePicked: addImage,
                                onImageFilePicked: addImage(fileURL:),
                                onPublishClick: publishClick,
                                onDelete: deleteDocument,
                                onPresentationClick: { showPresentations = true },
                                tab: $sideTab
                            )
                            .padding(.trailing, 10)
                            .transition(.move(edge: .trailing).combined(with: .opacity))
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
        .background(WrColors.systemBackground)
        .overlay(alignment: .top) {
            if viewModel.isAiRunning {
                AiRunningChip { viewModel.cancelAi() }
                    .padding(.top, 8)
                    .transition(.move(edge: .top).combined(with: .opacity))
            }
        }
        .animation(.snappy, value: viewModel.isAiRunning)
        .background { keyboardShortcuts }
        .navigationTitle(viewModel.hasLoaded ? viewModel.title : fallbackTitle)
        .toolbarTitleDisplayMode(.inline)
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
        #if os(iOS)
        // The side options of landscape replace the navigation bar, back button included.
        .toolbar(isWideLayout ? .hidden : .automatic, for: .navigationBar)
        #endif
        .animation(.snappy, value: isWideLayout)
        .sheet(isPresented: $showMenu) {
            NoteMenuSheet(viewModel: viewModel, onPublishClick: publishClick, onDelete: deleteDocument)
                .wrSheetSize(width: 440, height: 560)
        }
        .sheet(isPresented: $showPublish) {
            PublishSheet(viewModel: viewModel)
                .wrSheetSize(width: 440, height: 400)
        }
        .sheet(isPresented: $showPresentations) {
            if let presentations = viewModel.presentations {
                PresentationsSheet(viewModel: presentations)
                    .wrSheetSize(width: 440, height: 480)
            }
        }
        .alert("Premium Feature", isPresented: $showPremium) {
            Button("OK", role: .cancel) {}
        } message: {
            Text("This feature is only available for premium users using an online workspace")
        }
        .animation(.snappy, value: viewModel.isLocked)
        #if os(iOS)
        // The editor has its own bottom menu, like the Compose app.
        .toolbar(.hidden, for: .tabBar)
        #endif
        .sheet(isPresented: $showAiDialog) {
            AiDialog(cursorActions: aiCursorActions) { command, mode in
                viewModel.runAi(command, mode: mode)
            }
            .onAppear { viewModel.prewarmAi() }
            .wrSheetSize(width: 420, height: 440)
        }
        .sheet(isPresented: $showSelectedLinesAiDialog) {
            AiDialog(fixedMode: .selectedLines) { command, mode in
                viewModel.runAi(command, mode: mode)
            }
            .onAppear { viewModel.prewarmAi() }
            .wrSheetSize(width: 420, height: 440)
        }
        .alert(
            "Could not delete the document",
            isPresented: Binding(get: { viewModel.deleteError != nil }, set: { if !$0 { viewModel.deleteError = nil } })
        ) {
            Button("OK", role: .cancel) {}
        } message: {
            Text(viewModel.deleteError ?? "")
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
                #if os(iOS)
                .textInputAutocapitalization(.never)
                .keyboardType(.URL)
                #endif
                .autocorrectionDisabled()
            Button("Cancel", role: .cancel) {}
            Button("Add") {
                if let selection = linkSelection, let url = Self.normalizedURL(linkURL) {
                    viewModel.writeopiaManager.setLink(url, for: selection)
                }
            }
        }
        #if os(iOS)
        .fullScreenCover(item: $drawingTarget) { target in
            DrawingEditorView(drawing: target.drawing, defaultColor: defaultDrawingColor) { drawing in
                viewModel.saveDrawing(drawing, stepId: target.stepId)
            }
        }
        #else
        .sheet(item: $drawingTarget) { target in
            DrawingEditorView(drawing: target.drawing, defaultColor: defaultDrawingColor) { drawing in
                viewModel.saveDrawing(drawing, stepId: target.stepId)
            }
            .frame(minWidth: 800, minHeight: 560)
        }
        #endif
        .onAppear { openEditors?.opened() }
        .onDisappear {
            openEditors?.closed()
            viewModel.cancelAi()
            // Save and send what's pending before leaving.
            Task { await viewModel.flush() }
        }
        .task {
            viewModel.writeopiaManager.onDocumentLinkClick = openDocumentLink
            viewModel.writeopiaManager.onCopySelectedLines = { viewModel.copySelectedLines(to: SystemLinePasteboard()) }
            viewModel.writeopiaManager.onCutSelectedLines = {
                withAnimation(.snappy) { viewModel.cutSelectedLines(to: SystemLinePasteboard()) }
            }
            viewModel.writeopiaManager.onLinkRequested = linkClick
            // A click on the empty space closes the side menu and clears the selection, like the
            // Compose desktop app.
            viewModel.writeopiaManager.onBackgroundClick = {
                withAnimation(.snappy) {
                    sideTab = nil
                    viewModel.writeopiaManager.clearLineSelection()
                }
            }
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
        #if os(macOS)
        // The Mac toolbar draws a bubble around the item; the title needs room inside it.
        .padding(.horizontal, 12)
        .padding(.vertical, 2)
        #endif
    }
}
