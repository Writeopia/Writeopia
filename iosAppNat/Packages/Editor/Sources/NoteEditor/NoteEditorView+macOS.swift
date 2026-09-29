#if os(macOS)
import SwiftUI
import WriteopiaUI
import WrData
import WrDesign
import WrModels

/// Read-only view of a document on macOS, until the editor is ported to `NSTextView`.
/// Same initializer as the iOS editor, so the documents and search screens open it the same way.
public struct NoteEditorView: View {
    @State private var viewModel: NoteEditorViewModel
    @Environment(\.openEditors) private var openEditors
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

    public var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 10) {
                Text(viewModel.title.isEmpty ? fallbackTitle : viewModel.title)
                    .font(.largeTitle.bold())
                    .foregroundStyle(WrColors.textLight)
                    .padding(.bottom, 8)

                ForEach(viewModel.writeopiaManager.documentContent.filter(Self.isDrawn)) { step in
                    row(for: step)
                }

                WrErrorText(viewModel.errorMessage)

                Label("Editing on Mac is coming in a next version.", systemImage: "info.circle")
                    .font(.footnote)
                    .foregroundStyle(WrColors.textLighter)
                    .padding(.top, 24)
            }
            .frame(maxWidth: 760, alignment: .leading)
            .frame(maxWidth: .infinity)
            .padding(32)
        }
        .background(WrColors.background)
        .navigationTitle(viewModel.title.isEmpty ? fallbackTitle : viewModel.title)
        .overlay {
            if viewModel.isLoading {
                ProgressView()
            }
        }
        .task { await viewModel.loadDocument() }
        .onAppear { openEditors?.opened() }
        .onDisappear { openEditors?.closed() }
    }

    private static func isDrawn(_ step: StoryStep) -> Bool {
        step.type != .title && step.type != .space && step.type != .lastSpace && step.type != .onDragSpace
    }

    @ViewBuilder
    private func row(for step: StoryStep) -> some View {
        let text = step.text ?? ""

        switch step.type {
        case .checkItem:
            Label {
                Text(text)
            } icon: {
                Image(systemName: step.checked == true ? "checkmark.square.fill" : "square")
                    .foregroundStyle(step.checked == true ? WrColors.accent : WrColors.textLighter)
            }
            .font(bodyFont(for: step))
        case .unorderedListItem:
            Label(text, systemImage: "circle.fill")
                .labelStyle(BulletLabelStyle())
                .font(bodyFont(for: step))
        case .codeBlock:
            Text(text)
                .font(.body.monospaced())
                .padding(12)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(WrColors.surface, in: RoundedRectangle(cornerRadius: 8))
        case .divider:
            Divider()
                .padding(.vertical, 8)
        case .documentLink:
            if let link = step.documentLink {
                Button {
                    openDocumentLink(link)
                } label: {
                    Label(link.title ?? "Untitled", systemImage: "doc.text")
                }
                .buttonStyle(.link)
            }
        case .image:
            Label("Image", systemImage: "photo")
                .foregroundStyle(.secondary)
                .frame(maxWidth: .infinity, minHeight: 120)
                .background(WrColors.secondaryFill, in: RoundedRectangle(cornerRadius: 12))
        case .drawing:
            Label("Drawing", systemImage: "pencil.and.outline")
                .foregroundStyle(.secondary)
                .frame(maxWidth: .infinity, minHeight: 120)
                .background(WrColors.secondaryFill, in: RoundedRectangle(cornerRadius: 12))
        case .aiAnswer:
            Text(text)
                .font(.body)
                .padding(12)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(WrColors.accent.opacity(0.08), in: RoundedRectangle(cornerRadius: 8))
        case .loading:
            ProgressView()
        default:
            Text(text)
                .font(bodyFont(for: step))
        }
    }

    private func bodyFont(for step: StoryStep) -> Font {
        switch step.headingLevel {
        case 1: .title.bold()
        case 2: .title2.bold()
        case 3: .title3.bold()
        case 4: .headline
        default: .body
        }
    }
}

private struct BulletLabelStyle: LabelStyle {
    func makeBody(configuration: Configuration) -> some View {
        HStack(alignment: .firstTextBaseline, spacing: 8) {
            configuration.icon
                .font(.system(size: 6))
                .foregroundStyle(WrColors.textLighter)
            configuration.title
        }
    }
}
#endif
