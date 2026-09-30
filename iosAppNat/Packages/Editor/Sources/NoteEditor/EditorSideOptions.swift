import PhotosUI
import SwiftUI
import UniformTypeIdentifiers
import Writeopia
import WriteopiaUI
import WrData
import WrDesign
import WrModels

/// Options of the editor in landscape, like `SideEditorOptions` of the desktop app: a column of
/// tabs on the right edge, each opening its panel to the left. Replaces the navigation bar and
/// the bottom menu, so the text gets the whole screen.
struct EditorSideOptions: View {
    @Bindable var viewModel: NoteEditorViewModel
    let onBack: () -> Void
    let onAiClick: () -> Void
    let onLinkClick: () -> Void
    let onDrawingClick: () -> Void
    let onImagePicked: (PhotosPickerItem) -> Void
    /// An image file picked with the file importer (the Mac).
    let onImageFilePicked: (URL) -> Void
    let onPublishClick: () -> Void
    let onDelete: () -> Void
    /// Opens the presentations of the document (the Mac, with the cloud AI or Ollama).
    var onPresentationClick: () -> Void = {}
    /// The open panel; the editor closes it on a click outside.
    @Binding var tab: SideTab?
    private static let panelWidth: CGFloat = 280
    private static let spacing: CGFloat = 8

    enum SideTab {
        case page
        case text
        case export
    }

    var body: some View {
        HStack(alignment: .center, spacing: Self.spacing) {
            if let tab {
                panel(tab)
                    .frame(width: Self.panelWidth)
                    // Slides out from behind the column of tabs: it starts right under it, and
                    // the clip below hides what would stick out on the right of the column.
                    .transition(.offset(x: Self.panelWidth + Self.spacing).combined(with: .opacity))
            }
            fitting { column }
                .zIndex(1)
        }
        .clipShape(LeadingOverflowClip())
        .animation(.snappy(duration: 0.27), value: tab)
        .onChange(of: viewModel.isLocked) { _, isLocked in
            if isLocked, tab == .text { tab = nil }
        }
    }

    private var column: some View {
        VStack(spacing: 4) {
            #if os(iOS)
            // The Mac window keeps its own back button; here it would only take space.
            MenuButton(systemImage: "chevron.left", label: "Back", action: onBack)
                .accessibilityIdentifier("editor.side.back")
            Divider()
                .frame(width: 22)
            #endif
            tabButton(.page, systemImage: "doc.text", label: "Page")
                .accessibilityIdentifier("editor.side.page")
            tabButton(.text, systemImage: "textformat", label: "Text", isEnabled: !viewModel.isLocked)
                .accessibilityIdentifier("editor.side.text")
            tabButton(.export, systemImage: "square.and.arrow.up", label: "Export")
                .accessibilityIdentifier("editor.side.export")
            #if os(macOS)
            if viewModel.showsPresentations {
                MenuButton(systemImage: "play.rectangle", label: "Presentation") {
                    tab = nil
                    onPresentationClick()
                }
                .accessibilityIdentifier("editor.side.presentation")
            }
            #endif
            if viewModel.isAiAvailable && !viewModel.isLocked {
                MenuButton(systemImage: "sparkles", label: "AI", tint: WrColors.accent) {
                    tab = nil
                    onAiClick()
                }
                .accessibilityIdentifier("editor.side.ai")
            }
        }
        .padding(.vertical, 8)
        .padding(.horizontal, 4)
        .glassCapsule()
    }

    private func tabButton(
        _ option: SideTab,
        systemImage: String,
        label: LocalizedStringKey,
        isEnabled: Bool = true
    ) -> some View {
        MenuButton(systemImage: systemImage, label: label, isEnabled: isEnabled, isActive: tab == option) {
            tab = tab == option ? nil : option
        }
    }

    @ViewBuilder
    private func panel(_ tab: SideTab) -> some View {
        fitting {
            VStack(alignment: .leading, spacing: 16) {
                switch tab {
                case .page:
                    SidePagePanel(viewModel: viewModel, onDelete: onDelete)
                case .text:
                    SideTextPanel(
                        viewModel: viewModel,
                        onLinkClick: onLinkClick,
                        onDrawingClick: {
                            self.tab = nil
                            onDrawingClick()
                        },
                        onImagePicked: onImagePicked,
                        onImageFilePicked: onImageFilePicked
                    )
                case .export:
                    SideExportPanel(viewModel: viewModel, onPublishClick: onPublishClick)
                }
            }
            .padding(16)
        }
        .glassPanel()
    }

    /// The content at its size when it fits the height, scrolling otherwise (the keyboard takes
    /// most of a landscape phone).
    private func fitting<Content: View>(@ViewBuilder _ content: () -> Content) -> some View {
        let content = content()
        return ViewThatFits(in: .vertical) {
            content
            ScrollView(showsIndicators: false) { content }
        }
    }
}

/// Lock, font and delete, like `PageOptions` of the desktop app.
private struct SidePagePanel: View {
    @Bindable var viewModel: NoteEditorViewModel
    let onDelete: () -> Void
    @State private var confirmsDelete = false

    var body: some View {
        PanelSection("Page") {
            Toggle(isOn: Binding(get: { viewModel.isLocked }, set: { _ in viewModel.toggleLock() })) {
                Label("Lock document", systemImage: viewModel.isLocked ? "lock.fill" : "lock.open")
            }
            .tint(WrColors.accent)
            .accessibilityIdentifier("side.lock")
        }

        PanelSection("Font") {
            HStack(spacing: 8) {
                ForEach(EditorFont.allCases) { font in
                    FontOption(font: font, isSelected: viewModel.fontFamily == font) {
                        viewModel.changeFontFamily(font)
                    }
                }
            }
        }

        Button(role: .destructive) {
            confirmsDelete = true
        } label: {
            Label("Delete document", systemImage: "trash")
        }
        .accessibilityIdentifier("side.delete")
        .confirmationDialog("Delete this document?", isPresented: $confirmsDelete, titleVisibility: .visible) {
            Button("Delete document", role: .destructive, action: onDelete)
        } message: {
            Text("It will be removed from every device. This can't be undone.")
        }
    }
}

/// Formats, line types and content, like `TextOptions` of the desktop app. Line types act on
/// the selected lines, or else on the line with the cursor.
private struct SideTextPanel: View {
    let viewModel: NoteEditorViewModel
    let onLinkClick: () -> Void
    let onDrawingClick: () -> Void
    let onImagePicked: (PhotosPickerItem) -> Void
    let onImageFilePicked: (URL) -> Void
    @State private var pickedPhoto: PhotosPickerItem?
    @State private var picksImageFile = false

    private var manager: WriteopiaStateManager { viewModel.writeopiaManager }
    private let columns = Array(repeating: GridItem(.fixed(40), spacing: 6), count: 5)

    var body: some View {
        PanelSection("Text") {
            LazyVGrid(columns: columns, alignment: .leading, spacing: 6) {
                spanButton(.bold, systemImage: "bold", label: "Bold")
                spanButton(.italic, systemImage: "italic", label: "Italic")
                spanButton(.underline, systemImage: "underline", label: "Underline")
                MenuButton(systemImage: "link", label: "Link", isActive: manager.isSpanActive(.link), action: onLinkClick)
                    .accessibilityIdentifier("side.link")
            }
            HighlightColors(manager: manager)
        }

        PanelSection("Blocks") {
            LazyVGrid(columns: columns, alignment: .leading, spacing: 6) {
                typeButton(.checkItem, systemImage: "checkmark.square", label: "Checkbox")
                typeButton(.unorderedListItem, systemImage: "list.bullet", label: "List item")
                typeButton(.codeBlock, systemImage: "chevron.left.forwardslash.chevron.right", label: "Code block")
            }
            chips([
                (String(localized: "Box"), manager.blocksHave(.box), { manager.toggleTag(.box) }),
                (String(localized: "Card"), manager.blocksHave(.card), { manager.toggleTag(.card) }),
            ])
            chips([
                (String(localized: "Title"), manager.blocksHave(.h1), { manager.toggleHeading(.h1) }),
                (String(localized: "SubTitle"), manager.blocksHave(.h2), { manager.toggleHeading(.h2) }),
                (String(localized: "Header"), manager.blocksHave(.h3), { manager.toggleHeading(.h3) }),
            ])
        }

        PanelSection("Content") {
            LazyVGrid(columns: columns, alignment: .leading, spacing: 6) {
                MenuButton(systemImage: "pencil.and.scribble", label: "Drawing", action: onDrawingClick)
                    .accessibilityIdentifier("side.drawing")
                #if os(macOS)
                // Images come from files on the Mac, not from the photo library.
                MenuButton(systemImage: "photo", label: "Image") { picksImageFile = true }
                    .accessibilityIdentifier("side.image")
                    .fileImporter(isPresented: $picksImageFile, allowedContentTypes: [.image]) { result in
                        if case .success(let url) = result { onImageFilePicked(url) }
                    }
                #else
                PhotosPicker(selection: $pickedPhoto, matching: .images, photoLibrary: .shared()) {
                    MenuIcon(systemImage: "photo")
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Image")
                .accessibilityIdentifier("side.image")
                .onChange(of: pickedPhoto) { _, item in
                    guard let item else { return }
                    onImagePicked(item)
                    pickedPhoto = nil
                }
                #endif
            }
        }

        // What the selection menu of portrait offers for the selected lines.
        if manager.hasSelectedLines {
            PanelSection("\(manager.selectedPositions.count) lines selected") {
                LazyVGrid(columns: columns, alignment: .leading, spacing: 6) {
                    MenuButton(systemImage: "doc.badge.plus", label: "Link to page") {
                        Task { await viewModel.linkSelectionToNewPage() }
                    }
                    MenuButton(systemImage: "doc.on.doc", label: "Copy") {
                        viewModel.copySelectedLines(to: SystemLinePasteboard())
                    }
                    MenuButton(systemImage: "scissors", label: "Cut") {
                        withAnimation(.snappy) { viewModel.cutSelectedLines(to: SystemLinePasteboard()) }
                    }
                    MenuButton(systemImage: "trash", label: "Delete", tint: .red) {
                        withAnimation(.snappy) { manager.deleteSelectedLines() }
                    }
                    MenuButton(systemImage: "xmark", label: "Unselect") {
                        withAnimation(.snappy) { manager.clearLineSelection() }
                    }
                }
            }
        }
    }

    private func spanButton(_ span: Span, systemImage: String, label: LocalizedStringKey) -> some View {
        MenuButton(systemImage: systemImage, label: label, isActive: manager.isSpanActive(span)) {
            manager.toggleSpan(span)
        }
        .accessibilityIdentifier("side.\(span.rawValue.lowercased())")
    }

    private func typeButton(_ type: StoryType, systemImage: String, label: LocalizedStringKey) -> some View {
        MenuButton(systemImage: systemImage, label: label, isActive: manager.blocksAre(type)) {
            manager.toggleType(type)
        }
        .accessibilityIdentifier("side.\(type.name)")
    }

    private func chips(_ options: [(title: String, isActive: Bool, action: () -> Void)]) -> some View {
        HStack(spacing: 6) {
            ForEach(options, id: \.title) { option in
                Button(action: option.action) {
                    Text(option.title)
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(option.isActive ? WrColors.accent : .primary)
                        .frame(maxWidth: .infinity, minHeight: 32)
                        .background(
                            Capsule().fill(option.isActive ? WrColors.accent.opacity(0.15) : WrColors.tertiaryFill)
                        )
                        .contentShape(Capsule())
                }
                .buttonStyle(.plain)
                .accessibilityAddTraits(option.isActive ? .isSelected : [])
            }
        }
    }
}

/// Export and publish, like `Actions` of the desktop app.
private struct SideExportPanel: View {
    let viewModel: NoteEditorViewModel
    let onPublishClick: () -> Void
    @State private var exportedFile: ExportedFile?
    @State private var exportError: String?

    var body: some View {
        PanelSection("Export") {
            ForEach(ExportFormat.allCases) { format in
                Button {
                    export(format)
                } label: {
                    Label(format.title, systemImage: format.systemImage)
                }
                .accessibilityIdentifier("side.export.\(format.rawValue)")
            }
        }

        PanelSection("Publish") {
            Button(action: onPublishClick) {
                Label {
                    Text("Publish to Web")
                } icon: {
                    Image(systemName: viewModel.isPublished ? "globe.badge.chevron.backward" : "globe")
                }
            }
            .accessibilityIdentifier("side.publish")
        }
        #if os(iOS)
        .sheet(item: $exportedFile) { file in
            ShareSheet(items: [file.url])
                .presentationDetents([.medium, .large])
        }
        #else
        .onChange(of: exportedFile) { _, file in
            guard let file else { return }
            ExportSaver.save(file.url)
            exportedFile = nil
        }
        #endif
        .alert(
            "Could not export",
            isPresented: Binding(get: { exportError != nil }, set: { if !$0 { exportError = nil } })
        ) {
            Button("OK", role: .cancel) {}
        } message: {
            Text(exportError ?? "")
        }
    }

    private func export(_ format: ExportFormat) {
        do {
            exportedFile = ExportedFile(url: try viewModel.exportFile(format))
        } catch {
            exportError = error.localizedDescription
        }
    }
}

/// Clips only at the right edge of the column: the other sides get room for the shadows.
private nonisolated struct LeadingOverflowClip: Shape {
    func path(in rect: CGRect) -> Path {
        let overflow: CGFloat = 40
        return Path(CGRect(
            x: rect.minX - overflow,
            y: rect.minY - overflow,
            width: rect.width + overflow,
            height: rect.height + overflow * 2
        ))
    }
}

/// A titled group of a side panel.
private struct PanelSection<Content: View>: View {
    let title: LocalizedStringKey
    @ViewBuilder let content: Content

    init(_ title: LocalizedStringKey, @ViewBuilder content: () -> Content) {
        self.title = title
        self.content = content()
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(title)
                .font(.caption.weight(.semibold))
                .foregroundStyle(.secondary)
                .textCase(.uppercase)
            content
        }
        .tint(WrColors.accent)
    }
}

private extension View {
    @ViewBuilder
    func glassPanel() -> some View {
        let shape = RoundedRectangle(cornerRadius: 22, style: .continuous)
        if #available(iOS 26.0, *) {
            glassEffect(.regular, in: shape)
        } else {
            background(.regularMaterial, in: shape)
                .overlay(shape.strokeBorder(Color.primary.opacity(0.08)))
                .shadow(color: .black.opacity(0.12), radius: 12, y: 4)
        }
    }
}
