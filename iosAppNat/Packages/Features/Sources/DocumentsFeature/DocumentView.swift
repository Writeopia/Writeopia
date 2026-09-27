import Observation
import SwiftUI
import WrData
import WrDesign
import WrModels
import WrNetwork

@Observable
final class DocumentViewModel {
    private(set) var document: WrDocument?
    private(set) var isLoading = false
    private(set) var errorMessage: String?

    private let documentId: String
    private let repository: DocumentsRepository

    init(documentId: String, repository: DocumentsRepository) {
        self.documentId = documentId
        self.repository = repository
    }

    func load() async {
        isLoading = true
        defer { isLoading = false }

        do {
            document = try await repository.document(id: documentId)
            errorMessage = nil
        } catch is CancellationError {
            return
        } catch {
            errorMessage = error.userMessage
        }
    }
}

/// Read only rendering of a Writeopia document.
public struct DocumentView: View {
    @State private var viewModel: DocumentViewModel
    private let title: String

    public init(documentId: String, title: String, repository: DocumentsRepository) {
        _viewModel = State(initialValue: DocumentViewModel(documentId: documentId, repository: repository))
        self.title = title
    }

    public var body: some View {
        ScrollView {
            if let document = viewModel.document {
                VStack(alignment: .leading, spacing: 10) {
                    Text(document.displayTitle)
                        .font(.largeTitle.bold())
                        .foregroundStyle(WrColors.textLight)
                        .padding(.bottom, 4)

                    if document.lastUpdatedAt > 0 {
                        Text("Edited \(document.lastUpdatedDate.formatted(.relative(presentation: .named)))")
                            .font(.caption)
                            .foregroundStyle(WrColors.textLighter)
                            .padding(.bottom, 8)
                    }

                    ForEach(document.bodySteps) { step in
                        StoryStepView(step: step)
                    }
                }
                .textSelection(.enabled)
                .frame(maxWidth: 720, alignment: .leading)
                .frame(maxWidth: .infinity)
                .padding()
            }
        }
        .background(WrColors.background)
        .overlay {
            WrStateOverlay(
                isLoading: viewModel.isLoading,
                isEmpty: viewModel.document == nil,
                errorMessage: viewModel.errorMessage,
                emptyTitle: "Document not found",
                emptyImage: "doc",
                retry: { Task { await viewModel.load() } }
            )
        }
        .navigationTitle(title)
        .navigationBarTitleDisplayMode(.inline)
        .task { await viewModel.load() }
        .refreshable { await viewModel.load() }
    }
}

struct StoryStepView: View {
    let step: StoryStep

    var body: some View {
        switch step.type.number {
        case StoryType.divider.number:
            Divider().padding(.vertical, 6)

        case StoryType.space.number:
            Spacer().frame(height: 12)

        case StoryType.checkItem.number:
            HStack(alignment: .firstTextBaseline, spacing: 10) {
                Image(systemName: step.checked == true ? "checkmark.square.fill" : "square")
                    .foregroundStyle(step.checked == true ? WrColors.accent : WrColors.textLighter)
                RichText(step: step)
                    .strikethrough(step.checked == true)
                    .foregroundStyle(step.checked == true ? WrColors.textLighter : WrColors.textLight)
            }

        case StoryType.unorderedListItem.number:
            HStack(alignment: .firstTextBaseline, spacing: 10) {
                Text("•").foregroundStyle(WrColors.textLighter)
                RichText(step: step)
            }

        case StoryType.codeBlock.number:
            ScrollView(.horizontal, showsIndicators: false) {
                Text(step.text ?? "")
                    .font(.system(.callout, design: .monospaced))
                    .foregroundStyle(WrColors.textLight)
                    .padding(12)
            }
            .background(WrColors.divider.opacity(0.35), in: RoundedRectangle(cornerRadius: 10))

        case StoryType.image.number:
            if let url = (step.url ?? step.path).flatMap(URL.init(string:)) {
                AsyncImage(url: url) { image in
                    image.resizable().scaledToFit()
                } placeholder: {
                    ProgressView().frame(maxWidth: .infinity, minHeight: 120)
                }
                .clipShape(RoundedRectangle(cornerRadius: 10))
            }

        case StoryType.documentLink.number:
            if let link = step.documentLink {
                NavigationLink(value: DocumentsRoute.document(id: link.id, title: link.title ?? "Untitled")) {
                    Label(link.title ?? step.text ?? "Linked document", systemImage: "arrow.turn.down.right")
                        .font(.body.weight(.medium))
                        .foregroundStyle(WrColors.accent)
                }
            }

        default:
            if step.hasTag("HIGH_LIGHT_BLOCK") {
                RichText(step: step)
                    .padding(12)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(WrColors.accent.opacity(0.12), in: RoundedRectangle(cornerRadius: 10))
            } else if step.hasTag("CODE_BLOCK") {
                Text(step.text ?? "")
                    .font(.system(.callout, design: .monospaced))
                    .padding(12)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(WrColors.divider.opacity(0.35), in: RoundedRectangle(cornerRadius: 10))
            } else if !(step.text ?? "").isEmpty {
                RichText(step: step)
            }
        }
    }
}

/// Text with the inline spans (bold, italic, links...) and heading tags applied.
struct RichText: View {
    let step: StoryStep

    var body: some View {
        Text(StepFormatter.attributed(step))
            .font(font)
            .foregroundStyle(WrColors.textLight)
            .padding(.top, step.headingLevel == nil ? 0 : 8)
            .frame(maxWidth: .infinity, alignment: .leading)
    }

    private var font: Font {
        switch step.headingLevel {
        case 1: .title.bold()
        case 2: .title2.bold()
        case 3: .title3.bold()
        case 4: .headline
        default: .body
        }
    }
}

enum StepFormatter {
    static func attributed(_ step: StoryStep) -> AttributedString {
        let text = step.text ?? ""
        var attributed = AttributedString(text)
        let characters = Array(text.utf16)

        for span in step.spans {
            // Span indices are UTF-16 offsets, like Kotlin strings.
            let start = max(0, min(span.start, characters.count))
            let end = max(start, min(span.end, characters.count))
            guard start < end,
                  let lower = AttributedString.Index(String.Index(utf16Offset: start, in: text), within: attributed),
                  let upper = AttributedString.Index(String.Index(utf16Offset: end, in: text), within: attributed)
            else { continue }

            let range = lower..<upper
            switch span.span {
            case "BOLD":
                attributed[range].inlinePresentationIntent = (attributed[range].inlinePresentationIntent ?? []).union(.stronglyEmphasized)
            case "ITALIC":
                attributed[range].inlinePresentationIntent = (attributed[range].inlinePresentationIntent ?? []).union(.emphasized)
            case "UNDERLINE":
                attributed[range].underlineStyle = .single
            case "HIGHLIGHT":
                attributed[range].backgroundColor = .yellow.opacity(0.35)
            case "HIGHLIGHT_GREEN":
                attributed[range].backgroundColor = .green.opacity(0.3)
            case "HIGHLIGHT_RED":
                attributed[range].backgroundColor = .red.opacity(0.3)
            case "LINK":
                if let extra = span.extra, let url = URL(string: extra) {
                    attributed[range].link = url
                }
            default:
                break
            }
        }

        return attributed
    }
}
