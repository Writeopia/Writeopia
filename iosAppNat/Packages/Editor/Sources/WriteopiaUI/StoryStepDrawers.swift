import SwiftUI
import Writeopia
import WrDesign
import WrModels

/// Draws one `DrawStory`, picking the drawer of its type like the drawers map of the SDK.
/// Drawers the app provides for step types the editor doesn't know, like the drawers map the
/// Compose app merges into the SDK drawers (e.g. `DrawingPreviewDrawer`).
public typealias CustomStepDrawer = (StoryStep) -> AnyView

extension EnvironmentValues {
    /// True inside a slide of a presentation (`WriteopiaEditor.Layout.slide`): no gutter, no
    /// room below the last line.
    @Entry var isSlideLayout = false
}

struct CustomStepDrawersKey: EnvironmentKey {
    static let defaultValue: [Int: CustomStepDrawer] = [:]
}

extension EnvironmentValues {
    var customStepDrawers: [Int: CustomStepDrawer] {
        get { self[CustomStepDrawersKey.self] }
        set { self[CustomStepDrawersKey.self] = newValue }
    }
}

struct StoryStepDrawer: View {
    let draw: DrawStory
    let manager: WriteopiaStateManager
    @Environment(\.customStepDrawers) private var customDrawers

    private var step: StoryStep { draw.storyStep }

    @Environment(\.reorderCoordinator) private var reorder

    private var isSpace: Bool {
        [StoryType.space.number, StoryType.onDragSpace.number, StoryType.lastSpace.number]
            .contains(step.type.number)
    }

    var body: some View {
        if isSpace {
            content
        } else {
            content
                .reorderFrame(stepId: step.id)
                .opacity(reorder?.active?.stepId == step.id ? 0.35 : 1)
        }
    }

    @ViewBuilder
    private var content: some View {
        switch step.type.number {
        case StoryType.title.number:
            TitleDrawer(step: step, manager: manager)
        case StoryType.text.number:
            DraggableStep(step: step, position: draw.position, manager: manager, block: draw.block) {
                StepTextView(step: step, manager: manager)
                    .overlay(alignment: .trailing) {
                        SectionWandButton(step: step, position: draw.position, manager: manager)
                    }
            }
        case StoryType.checkItem.number, StoryType.unorderedListItem.number:
            if step.isAiSuggestion {
                AiSuggestionDrawer(step: step, manager: manager)
            } else if step.type.number == StoryType.checkItem.number {
                DraggableStep(step: step, position: draw.position, manager: manager, block: draw.block) {
                    CheckItemDrawer(step: step, manager: manager)
                }
            } else {
                DraggableStep(step: step, position: draw.position, manager: manager, block: draw.block) {
                    UnorderedListItemDrawer(step: step, manager: manager)
                }
            }
        case StoryType.codeBlock.number:
            DraggableStep(step: step, position: draw.position, manager: manager, block: draw.block) {
                CodeBlockDrawer(step: step, manager: manager, lineNumber: draw.block?.lineNumber ?? 1)
            }
        case StoryType.aiAnswer.number:
            DraggableStep(step: step, position: draw.position, manager: manager, block: draw.block) {
                AiAnswerDrawer(step: step, manager: manager)
            }
        case StoryType.image.number:
            DraggableStep(step: step, position: draw.position, manager: manager, block: draw.block) {
                ImageDrawer(step: step, isUploading: manager.uploadingStepIds.contains(step.id))
                    .contextMenu {
                        if manager.isEditable {
                            Button("Delete image", systemImage: "trash", role: .destructive) {
                                manager.removeStep(stepId: step.id)
                            }
                        }
                    }
            }
        case StoryType.documentLink.number:
            DraggableStep(step: step, position: draw.position, manager: manager, block: draw.block) {
                DocumentLinkDrawer(step: step, manager: manager)
            }
        case StoryType.linkCard.number:
            DraggableStep(step: step, position: draw.position, manager: manager, block: draw.block) {
                LinkCardDrawer(step: step, manager: manager)
            }
        case StoryType.divider.number:
            DraggableStep(step: step, position: draw.position, manager: manager, block: draw.block) {
                DividerDrawer()
            }
        case StoryType.loading.number:
            LoadingDrawer()
        case StoryType.skeleton.number:
            SkeletonDrawer(step: step)
        case StoryType.space.number, StoryType.onDragSpace.number:
            SpaceDrawer(draw: draw, manager: manager)
        case StoryType.lastSpace.number:
            LastSpaceDrawer(draw: draw, manager: manager)
        default:
            if let custom = customDrawers[step.type.number] {
                DraggableStep(step: step, position: draw.position, manager: manager, block: draw.block) {
                    custom(step)
                }
            }
        }
    }
}

/// Leading gutter shared by every step. The grip shows while the cursor is in the step, and
/// holding it starts a drag to reorder the step. The gutter keeps its width when the grip is
/// hidden so the text doesn't shift. `block` paints the code block, box or card the step is part
/// of around the content.
struct DraggableStep<Content: View>: View {
    let step: StoryStep
    let position: String
    let manager: WriteopiaStateManager
    var block: BlockInfo? = nil
    var alignment: VerticalAlignment = .center
    @ViewBuilder let content: Content
    @State private var isHovered = false
    @Environment(\.isSlideLayout) private var isSlideLayout

    var body: some View {
        HStack(alignment: alignment, spacing: 4) {
            // A slide has nothing to drag, and the gutter would push its text off center.
            if !isSlideLayout {
                ReorderGrip(step: step)
                    .opacity(showsGrip ? 1 : 0)
                    .allowsHitTesting(showsGrip)
                    .accessibilityHidden(!showsGrip)
                    .animation(.easeInOut(duration: 0.15), value: showsGrip)
            }

            content
                .frame(maxWidth: .infinity, alignment: .leading)
                .blockInsets(block)
                // The block itself is painted by the editor, as one piece from its first step
                // to its last (see `BlockPainter`).
                .blockPiece(block)
        }
        .swipeToSelect(step, manager: manager)
        .selectableByDrag(id: step.id)
        // With a pointer the grip shows on hover, like the drag handle of the Compose app.
        .onHover { isHovered = $0 }
    }

    private var showsGrip: Bool {
        manager.isEditable && (manager.currentStory.focus == position || isHovered)
    }
}

enum EditorLayout {
    static let gutter: CGFloat = 20
}

/// The title of the document, with its colored header when it has one, like `HeaderDrawer` of
/// the SDK. A pencil in the corner opens the header colors.
struct TitleDrawer: View {
    let step: StoryStep
    let manager: WriteopiaStateManager
    @State private var isHovered = false
    @State private var showsColors = false
    @Environment(\.isSlideLayout) private var isSlideLayout

    /// The picture of the header, which takes the place of its color.
    private var headerImageURL: URL? {
        step.url.flatMap { $0.isEmpty ? nil : URL(string: $0) }
    }

    private var headerColor: Color? {
        guard headerImageURL == nil else { return nil }
        return step.decoration?.backgroundColor.map { Color(argb: $0) }
    }

    /// The height of the picture of the header. The Mac window is taller, like the colored band.
    private static let headerImageHeight: CGFloat = {
        #if os(macOS)
        240
        #else
        200
        #endif
    }()

    /// Space above the title inside a colored header. The Compose app uses 131; the Mac window
    /// is taller, so the band gets a little more room there.
    private static let coloredHeaderTopPadding: CGFloat = {
        #if os(macOS)
        160
        #else
        114
        #endif
    }()

    var body: some View {
        VStack(spacing: 0) {
            // A picture sits as a band above the title, like `HeaderDrawer` of the SDK.
            if let headerImageURL {
                HeaderImageView(url: headerImageURL)
                    .frame(maxWidth: .infinity)
                    .frame(height: Self.headerImageHeight)
                    .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                    .transition(.opacity)
            }
            titleText
        }
            .overlay(alignment: .topTrailing) {
                if manager.isEditable, showsEditButton {
                    Button {
                        showsColors = true
                    } label: {
                        Image(systemName: "pencil")
                            .font(.system(size: 13, weight: .semibold))
                            .foregroundStyle(Color.primary)
                            .frame(width: 30, height: 30)
                            .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 8, style: .continuous))
                            .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .help("Edit header")
                    .accessibilityLabel("Edit header")
                    .accessibilityIdentifier("editor.header.edit")
                    .padding(8)
                    .headerColorsPresentation(isPresented: $showsColors, manager: manager)
                    .transition(.opacity)
                }
            }
            .onHover { isHovered = $0 }
            .animation(.easeInOut(duration: 0.15), value: isHovered)
            .animation(.snappy, value: headerColor == nil)
            .animation(.snappy, value: headerImageURL)
    }

    private var titleText: some View {
        HStack(alignment: .center, spacing: 10) {
            // The icon of the document, chosen in the sidebar, like `HeaderDrawer` of the SDK.
            if let icon = manager.documentIcon {
                DocumentIconImage(icon: icon, defaultColor: .primary)
                    .font(.system(size: 30, weight: .regular))
                    .frame(width: 40)
                    .transition(.opacity)
            }

            StepTextView(step: step, manager: manager)
                .overlay(alignment: .topLeading) {
                    if (step.text ?? "").isEmpty {
                        Text("Untitled")
                            .font(manager.fontFamily.font(size: 34, weight: .bold))
                            .foregroundStyle(.quaternary)
                            .allowsHitTesting(false)
                    }
                }
        }
            .animation(.snappy, value: manager.documentIcon)
            // In a slide the title lines up with the text below, which has no gutter.
            .padding(.leading, isSlideLayout ? 4 : EditorLayout.gutter + 4)
            .padding(.trailing, 12)
            .padding(.top, headerColor == nil ? 8 : Self.coloredHeaderTopPadding)
            .padding(.bottom, 8)
            .background {
                if let headerColor {
                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                        .fill(headerColor)
                }
            }
    }

    /// On the Mac the pencil shows on hover; on touch screens it stays, like the Compose app.
    private var showsEditButton: Bool {
        #if os(macOS)
        isHovered || showsColors
        #else
        true
        #endif
    }
}

private extension View {
    /// The header colors: a popover under the pencil on the Mac, and a bottom sheet on iOS, where
    /// a popover doesn't fit the screen.
    @ViewBuilder
    func headerColorsPresentation(isPresented: Binding<Bool>, manager: WriteopiaStateManager) -> some View {
        #if os(macOS)
        popover(isPresented: isPresented, arrowEdge: .bottom) {
            HeaderColorPicker(manager: manager)
                .padding(16)
        }
        #else
        sheet(isPresented: isPresented) {
            ScrollView {
                HeaderColorPicker(manager: manager)
                    .padding(.horizontal, 20)
                    .padding(.top, 24)
                    .padding(.bottom, 16)
                    .frame(maxWidth: .infinity, alignment: .top)
            }
            // The pictures need room for the one the AI draws; the colors alone fit a short sheet.
            .presentationDetents(manager.headerImages == nil ? [.height(220)] : [.medium, .large])
            .presentationDragIndicator(.visible)
        }
        #endif
    }
}

/// The colors of the header: none, then the palette of the Compose app. The selected one is a
/// wider, squarer swatch, like `HeaderEditionOptions`.
struct HeaderColorPicker: View {
    let manager: WriteopiaStateManager

    /// On the Mac the swatches fit in one row; on a phone they wrap to the width of the sheet.
    private static let wrapsSwatches: Bool = {
        #if os(macOS)
        false
        #else
        true
        #endif
    }()

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Header")
                .font(.headline)

            colors

            if let images = manager.headerImages {
                HeaderImagesSection(manager: manager, model: images)
                    .padding(.top, 8)
            }
        }
    }

    @ViewBuilder
    private var colors: some View {
        if Self.wrapsSwatches {
            LazyVGrid(columns: [GridItem(.adaptive(minimum: 44), spacing: 10)], alignment: .leading, spacing: 10) {
                swatch(nil)
                ForEach(WriteopiaStateManager.headerColors, id: \.self) { color in
                    swatch(color)
                }
            }
        } else {
            HStack(spacing: 10) {
                swatch(nil)
                ForEach(WriteopiaStateManager.headerColors, id: \.self) { color in
                    swatch(color)
                }
            }
        }
    }

    private func swatch(_ argb: Int?) -> some View {
        let isSelected = manager.headerImageUrl == nil && manager.headerColor == argb
        let shape = RoundedRectangle(cornerRadius: isSelected ? 10 : 20, style: .continuous)
        return Button {
            withAnimation(.snappy) { manager.setHeaderColor(argb) }
        } label: {
            shape
                .fill(argb.map { Color(argb: $0) } ?? Color.clear)
                .overlay(shape.strokeBorder(isSelected ? WrColors.accent : WrColors.divider, lineWidth: isSelected ? 2 : 1))
                .overlay {
                    if argb == nil {
                        Image(systemName: "slash.circle")
                            .foregroundStyle(Color.primary)
                    }
                }
                .frame(width: isSelected ? 44 : 34, height: 34)
                .contentShape(shape)
        }
        .buttonStyle(.plain)
        .help(argb == nil ? "No color" : "Header color")
        .accessibilityLabel(argb == nil ? "No color" : "Header color")
        .accessibilityAddTraits(isSelected ? .isSelected : [])
        .animation(.snappy, value: isSelected)
    }
}

/// The pictures of the header: a button to draw one with the AI, the pictures of the workspace,
/// and the one being drawn, shown to be approved or drawn again, like `HeaderEditionOptions` of
/// the Compose app.
struct HeaderImagesSection: View {
    let manager: WriteopiaStateManager
    let model: HeaderImagesModel

    private static let tileSize = CGSize(width: 88, height: 56)

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("Images")
                .font(.subheadline.weight(.semibold))

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 10) {
                    generateTile
                    ForEach(model.images, id: \.self) { url in
                        imageTile(url)
                    }
                }
                .padding(.vertical, 2)
            }

            if model.generation != .idle {
                generationPanel
                    .transition(.opacity.combined(with: .move(edge: .top)))
            }
        }
        .animation(.snappy, value: model.generation)
        .task { await model.load() }
    }

    private var generateTile: some View {
        Button {
            model.generate()
        } label: {
            VStack(spacing: 4) {
                Image(systemName: "sparkles")
                    .foregroundStyle(WrColors.accent)
                Text("Generate with AI")
                    .font(.caption2)
                    .lineLimit(1)
            }
            .frame(width: Self.tileSize.width + 24, height: Self.tileSize.height)
            .background(WrColors.secondaryFill, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: 12, style: .continuous).strokeBorder(WrColors.divider))
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .disabled(model.isGenerating)
        .accessibilityIdentifier("editor.header.generateImage")
    }

    private func imageTile(_ url: String) -> some View {
        let isSelected = manager.headerImageUrl == url
        let shape = RoundedRectangle(cornerRadius: 12, style: .continuous)
        return Button {
            withAnimation(.snappy) { manager.setHeaderImage(url) }
        } label: {
            HeaderImageView(url: URL(string: url))
                .frame(width: Self.tileSize.width, height: Self.tileSize.height)
                .clipShape(shape)
                .overlay(shape.strokeBorder(isSelected ? WrColors.accent : WrColors.divider, lineWidth: isSelected ? 2 : 1))
                .contentShape(shape)
        }
        .buttonStyle(.plain)
        .accessibilityLabel("Header image")
        .accessibilityAddTraits(isSelected ? .isSelected : [])
    }

    private var generationPanel: some View {
        VStack(alignment: .leading, spacing: 8) {
            ZStack {
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .fill(WrColors.secondaryFill)

                switch model.generation {
                case .candidate(let url):
                    HeaderImageView(url: URL(string: url))
                case .failed(let message):
                    Text(message ?? String(localized: "The AI could not draw a header. Try again."))
                        .font(.callout)
                        .foregroundStyle(.red)
                        .multilineTextAlignment(.center)
                        .padding()
                default:
                    VStack(spacing: 8) {
                        ProgressView()
                        Text("Drawing your header…")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                }
            }
            .aspectRatio(16 / 9, contentMode: .fit)
            .frame(maxWidth: 420)
            .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))

            if model.saveFailed {
                Text(model.saveError ?? String(localized: "The image could not be saved to the workspace. Try again."))
                    .font(.caption)
                    .foregroundStyle(.red)
            }

            Text("Uses your AI tokens")
                .font(.caption2)
                .foregroundStyle(.secondary)

            HStack(spacing: 8) {
                Spacer()
                Button("Discard", role: .cancel) { model.discard() }
                    .accessibilityIdentifier("editor.header.discardImage")
                if !model.isGenerating {
                    Button("Try another") { model.generate() }
                        .buttonStyle(.bordered)
                        .disabled(model.isSaving)
                        .accessibilityIdentifier("editor.header.retryImage")
                }
                if case .candidate = model.generation {
                    Button {
                        Task { await model.approve() }
                    } label: {
                        if model.isSaving {
                            ProgressView().controlSize(.small)
                        } else {
                            Text("Use this image")
                        }
                    }
                    .buttonStyle(.borderedProminent)
                    .disabled(model.isSaving)
                    .accessibilityIdentifier("editor.header.useImage")
                }
            }
            .frame(maxWidth: 420)
        }
    }
}

/// A header picture from the CDN, scaled to cover the frame it is given with its proportions
/// kept and the excess cropped away, with a quiet placeholder while it loads.
public struct HeaderImageView: View {
    let url: URL?

    public init(url: URL?) {
        self.url = url
    }

    public var body: some View {
        // The clear color takes exactly the frame the caller gives (the band of the editor, the
        // top of a card, a tile of the picker) and the picture is drawn over it. An overlay never
        // takes part in the layout, so the part of the picture that spills over the frame can't
        // stretch the band or the card around it; `clipped` cuts it away.
        Color.clear
            .overlay {
                AsyncImage(url: url) { phase in
                    switch phase {
                    case .success(let image):
                        image
                            .resizable()
                            .scaledToFill()
                    case .failure:
                        WrColors.secondaryFill
                            .overlay(Image(systemName: "photo").foregroundStyle(.secondary))
                    default:
                        WrColors.secondaryFill
                    }
                }
            }
            .clipped()
            .accessibilityLabel("Header image")
    }
}

/// A line of code with its number, like `CodeBlockDrawer` of the SDK. The tinted background of
/// the block is painted by `DraggableStep`, so consecutive lines share it.
struct CodeBlockDrawer: View {
    let step: StoryStep
    let manager: WriteopiaStateManager
    var lineNumber: Int = 1

    /// What the number, the divider and their spacing add beside the text.
    static let gutterWidth: CGFloat = 24 + 8 + 1 + 8

    var body: some View {
        // The text view has no baseline for SwiftUI to align to, so the number sits on the
        // first line by hand, like the bullet of a list item.
        HStack(alignment: .top, spacing: 8) {
            Text(String(lineNumber))
                .font(.system(size: 12, design: .monospaced))
                .foregroundStyle(.secondary)
                .frame(width: 24, alignment: .trailing)
                .frame(height: TextStyles.lineHeight(of: TextStyles.baseFont(for: step, family: manager.fontFamily)))
            Rectangle()
                .fill(WrColors.divider)
                .frame(width: 1)
            StepTextView(step: step, manager: manager)
        }
    }
}

struct CheckItemDrawer: View {
    let step: StoryStep
    let manager: WriteopiaStateManager

    var body: some View {
        HStack(alignment: .center, spacing: 8) {
            Button {
                manager.onCheckedChange(stepId: step.id, checked: !(step.checked ?? false))
            } label: {
                Image(systemName: step.checked == true ? "checkmark.square.fill" : "square")
                    .foregroundStyle(step.checked == true ? WrColors.accent : Color.secondary)
                    .imageScale(.large)
            }
            .buttonStyle(.plain)
            .disabled(!manager.isEditable)
            .accessibilityLabel(step.checked == true ? "Checked" : "Unchecked")

            StepTextView(step: step, manager: manager)
        }
    }
}

struct UnorderedListItemDrawer: View {
    let step: StoryStep
    let manager: WriteopiaStateManager

    var body: some View {
        // The text view has no baseline for SwiftUI to align to, so the bullet sits on the
        // first line by hand, like the checkbox next to it.
        HStack(alignment: .top, spacing: 8) {
            Text("•")
                .font(.body.bold())
                .foregroundStyle(.secondary)
                .frame(height: TextStyles.lineHeight(of: TextStyles.baseFont(for: step, family: manager.fontFamily)))
            StepTextView(step: step, manager: manager)
        }
    }
}

/// Text written by the AI, shown in a card. It stays editable like any paragraph. Once complete,
/// it can be accepted (turned into regular lines), copied or discarded, like `AiAnswerDrawer` of
/// the SDK.
struct AiAnswerDrawer: View {
    let step: StoryStep
    let manager: WriteopiaStateManager

    private var isStreaming: Bool { manager.streamingAnswerId == step.id }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 4) {
                Label("AI generated", systemImage: "sparkles")
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(WrColors.accent)
                Spacer()
                if !isStreaming {
                    actions
                }
            }
            StepTextView(step: step, manager: manager)
        }
        .padding(14)
        .background(WrColors.secondaryBackground, in: RoundedRectangle(cornerRadius: 12))
        // A tinted edge marks where the answer starts and ends, whatever the background.
        .overlay {
            RoundedRectangle(cornerRadius: 12)
                .strokeBorder(WrColors.accent.opacity(0.35), lineWidth: 1)
        }
        .padding(.vertical, 8)
    }

    private var actions: some View {
        HStack(spacing: 2) {
            Button {
                SystemPasteboard.copy(step.text ?? "")
            } label: {
                Image(systemName: "doc.on.doc")
                    .frame(width: 32, height: 28)
            }
            .accessibilityLabel("Copy")
            .accessibilityIdentifier("ai.answer.copy")

            if manager.isEditable {
                Button(role: .destructive) {
                    withAnimation(.snappy) { manager.removeStep(stepId: step.id) }
                } label: {
                    Image(systemName: "trash")
                        .frame(width: 32, height: 28)
                }
                .accessibilityLabel("Discard")
                .accessibilityIdentifier("ai.answer.discard")

                Button {
                    withAnimation(.snappy) { manager.acceptAiAnswer(stepId: step.id) }
                } label: {
                    Image(systemName: "checkmark")
                        .frame(width: 32, height: 28)
                }
                .accessibilityLabel("Accept")
                .accessibilityIdentifier("ai.answer.accept")
            }
        }
        .font(.footnote.weight(.semibold))
        .buttonStyle(.borderless)
        .foregroundStyle(.secondary)
    }
}

/// The AI wand of a heading, like the one the Compose app shows on hover: it writes the content
/// of the section. Shown while the heading has the cursor.
struct SectionWandButton: View {
    let step: StoryStep
    let position: String
    let manager: WriteopiaStateManager

    private var isShown: Bool {
        manager.isEditable &&
            manager.onGenerateSection != nil &&
            manager.currentStory.focus == position &&
            step.headingLevel != nil &&
            !(step.text ?? "").trimmingCharacters(in: .whitespaces).isEmpty
    }

    var body: some View {
        if isShown {
            Button {
                manager.onGenerateSection?(step.id)
            } label: {
                Image(systemName: "wand.and.sparkles")
                    .font(.body.weight(.semibold))
                    .foregroundStyle(WrColors.accent)
                    .padding(6)
                    .background(.thinMaterial, in: Circle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Write this section with AI")
            .accessibilityIdentifier("editor.section.ai")
            .transition(.opacity)
        }
    }
}

/// A list item suggested by the AI: gray and read-only until accepted. The first one of the group
/// holds the buttons to keep or remove them all.
struct AiSuggestionDrawer: View {
    let step: StoryStep
    let manager: WriteopiaStateManager

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            if step.isFirstAiSuggestion {
                HStack(spacing: 12) {
                    Label("AI suggestions", systemImage: "sparkles")
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(WrColors.accent)
                    Spacer()
                    Button("Dismiss", role: .cancel) {
                        withAnimation(.snappy) { manager.dismissAiSuggestions() }
                    }
                    .accessibilityIdentifier("ai.suggestions.dismiss")
                    Button("Accept") {
                        withAnimation(.snappy) { manager.acceptAiSuggestions() }
                    }
                    .fontWeight(.semibold)
                    .accessibilityIdentifier("ai.suggestions.accept")
                }
                .font(.footnote)
                .buttonStyle(.borderless)
            }

            HStack(alignment: .firstTextBaseline, spacing: 8) {
                if step.type.number == StoryType.checkItem.number {
                    Image(systemName: "square")
                        .imageScale(.large)
                } else {
                    Text("•").font(.body.bold())
                }
                Text(step.text ?? "")
                    .font(manager.fontFamily.font(size: 17, weight: .regular).monospaced())
            }
            .foregroundStyle(.secondary)
        }
        .padding(.leading, EditorLayout.gutter + 4)
        .padding(.vertical, 2)
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("ai.suggestion")
    }
}

struct DocumentLinkDrawer: View {
    let step: StoryStep
    let manager: WriteopiaStateManager

    private var title: String {
        step.documentLink?.title ?? step.text ?? String(localized: "Linked document")
    }

    var body: some View {
        Button {
            if let link = step.documentLink {
                manager.onDocumentLinkClick?(link)
            }
        } label: {
            HStack(spacing: 8) {
                Image(systemName: "doc.text")
                Text(title)
                    .underline()
                    .multilineTextAlignment(.leading)
            }
            .font(.body.weight(.medium))
            .foregroundStyle(WrColors.accent)
            .padding(.vertical, 4)
        }
        .buttonStyle(.plain)
        .disabled(step.documentLink == nil)
    }
}

/// A card for a pasted web address: the link icon, the address and, once the backend AI wrote
/// it, a short description of the page. It can not be edited; a tap opens the address, and the
/// button at its end copies it.
struct LinkCardDrawer: View {
    let step: StoryStep
    let manager: WriteopiaStateManager
    @Environment(\.openURL) private var openURL
    /// True for a moment after the address was copied; the button shows a check mark.
    @State private var copied = false
    @State private var copiedFeedback: Task<Void, Never>?

    private var address: String { step.url ?? "" }
    private var description: String? {
        let text = step.text?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        return text.isEmpty ? nil : text
    }
    private var isDescribing: Bool { manager.describingLinkStepIds.contains(step.id) }

    var body: some View {
        Button {
            if let url = URL(string: address) {
                openURL(url)
            }
        } label: {
            HStack(alignment: .center, spacing: 12) {
                Image(systemName: "link")
                    .font(.body.weight(.medium))
                    .foregroundStyle(WrColors.textLighter)

                VStack(alignment: .leading, spacing: 4) {
                    Text(address)
                        .font(.body.weight(.medium))
                        .foregroundStyle(WrColors.textLight)
                        .lineLimit(1)
                        .truncationMode(.middle)

                    if let description {
                        Text(description)
                            .font(.footnote)
                            .foregroundStyle(WrColors.textLighter)
                            .lineLimit(4)
                            .multilineTextAlignment(.leading)
                    } else if isDescribing {
                        HStack(spacing: 6) {
                            ProgressView()
                                .controlSize(.mini)
                            Text("Describing the page...")
                                .font(.footnote)
                                .foregroundStyle(WrColors.textLighter)
                        }
                    }
                }
                Spacer(minLength: 0)
            }
            .padding(.leading, 14)
            // Room for the copy button, which floats over the trailing end.
            .padding(.trailing, address.isEmpty ? 14 : 48)
            .padding(.vertical, 12)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(WrColors.secondaryFill, in: RoundedRectangle(cornerRadius: 12))
            .padding(.vertical, 4)
        }
        .buttonStyle(.plain)
        .disabled(URL(string: address) == nil)
        .accessibilityLabel(Text("Link card \(address)"))
        // A button inside the card's button wouldn't get the taps on the Mac; over it, it does.
        .overlay(alignment: .trailing) {
            if !address.isEmpty {
                copyButton
                    .padding(.trailing, 8)
            }
        }
        .contextMenu {
            if manager.isEditable {
                Button("Delete link", systemImage: "trash", role: .destructive) {
                    manager.removeStep(stepId: step.id)
                }
            }
        }
    }

    private var copyButton: some View {
        Button {
            SystemPasteboard.copy(address)
            copiedFeedback?.cancel()
            withAnimation(.snappy(duration: 0.15)) { copied = true }
            copiedFeedback = Task {
                try? await Task.sleep(for: .seconds(1.5))
                guard !Task.isCancelled else { return }
                withAnimation(.snappy(duration: 0.15)) { copied = false }
            }
        } label: {
            Image(systemName: copied ? "checkmark" : "doc.on.doc")
                .font(.footnote.weight(.semibold))
                .foregroundStyle(copied ? WrColors.accent : WrColors.textLighter)
                .frame(width: 32, height: 32)
                .contentShape(Circle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(copied ? Text("Copied") : Text("Copy link"))
        .accessibilityIdentifier("step.link_card.copy")
    }
}

struct DividerDrawer: View {
    var body: some View {
        // `Divider` would be vertical inside the gutter's HStack, so draw the line directly.
        Rectangle()
            .fill(WrColors.separator)
            .frame(maxWidth: .infinity)
            .frame(height: 1)
            .padding(.vertical, 12)
            .accessibilityLabel("Divider")
    }
}

struct LoadingDrawer: View {
    var body: some View {
        ProgressView()
            .controlSize(.small)
            .padding(.leading, EditorLayout.gutter + 5)
            .padding(.vertical, 6)
    }
}

/// The placeholder of text the AI is rewriting: pulsing bars, one per line of the text that was
/// there, so the document keeps about the same shape until the new text arrives.
struct SkeletonDrawer: View {
    let step: StoryStep
    @State private var pulsing = false

    private var lines: Int {
        let count = (step.text ?? "").split(separator: "\n", omittingEmptySubsequences: false).count
        return min(max(count, 1), 8)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            ForEach(0..<lines, id: \.self) { index in
                GeometryReader { geometry in
                    RoundedRectangle(cornerRadius: 4)
                        .fill(Color.secondary.opacity(0.3))
                        .frame(width: geometry.size.width * (index == lines - 1 && lines > 1 ? 0.6 : 0.95))
                }
                .frame(height: 14)
            }
        }
        .opacity(pulsing ? 0.9 : 0.35)
        .padding(.leading, EditorLayout.gutter + 5)
        .padding(.trailing, 16)
        .padding(.vertical, 6)
        .onAppear {
            withAnimation(.easeInOut(duration: 0.7).repeatForever(autoreverses: true)) { pulsing = true }
        }
    }
}

/// Gap between steps. While a step is dragged over it, it turns into the `ON_DRAG_SPACE`
/// highlight that shows where the step will land.
/// The gap between two steps. Inside a code block, box or card the block is painted through it
/// by the editor (see `BlockPainter`).
struct SpaceDrawer: View {
    let draw: DrawStory
    let manager: WriteopiaStateManager

    private var isTarget: Bool { draw.storyStep.type.number == StoryType.onDragSpace.number }

    var body: some View {
        RoundedRectangle(cornerRadius: 3)
            .fill(isTarget ? Color.gray.opacity(0.6) : Color.clear)
            .frame(height: isTarget ? 6 : 4)
            .padding(.vertical, isTarget ? 2 : 3)
            .padding(.leading, EditorLayout.gutter)
            .contentShape(Rectangle().inset(by: -6))
            .animation(.easeOut(duration: 0.15), value: isTarget)
    }
}

/// Empty area after the last step: tapping it continues the document, and it accepts drops.
struct LastSpaceDrawer: View {
    let draw: DrawStory
    let manager: WriteopiaStateManager
    @Environment(\.isSlideLayout) private var isSlideLayout

    private var isTarget: Bool { draw.storyStep.type.number == StoryType.onDragSpace.number }

    var body: some View {
        VStack(spacing: 0) {
            RoundedRectangle(cornerRadius: 3)
                .fill(isTarget ? Color.gray.opacity(0.6) : Color.clear)
                .frame(height: 6)
                .padding(.leading, EditorLayout.gutter)
            // Room to click below the last line and keep writing. A slide doesn't need it: the
            // clear color takes every point it's offered, which would push the slide to the top.
            if !isSlideLayout {
                Color.clear
                    .frame(maxWidth: .infinity, minHeight: 240)
            }
        }
        .contentShape(Rectangle())
        .onTapGesture { manager.clickAtTheEnd() }
        .accessibilityIdentifier("editor.end")
    }
}

struct DragPreview: View {
    let step: StoryStep

    var body: some View {
        Text(previewText)
            .font(.body)
            .lineLimit(2)
            .padding(10)
            .frame(maxWidth: 260, alignment: .leading)
            .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 10))
    }

    private var previewText: String {
        if step.type.number == StoryType.divider.number { return String(localized: "Divider") }
        if step.type.number == StoryType.linkCard.number { return step.url ?? String(localized: "Link") }
        let text = step.documentLink?.title ?? step.text ?? ""
        return text.isEmpty ? String(localized: "Empty line") : text
    }
}

