import DocumentsFeature
import NoteEditor
import Observation
import SwiftUI
import WrData
import WrDesign
import WrModels
import WrNetwork
import WrSession

@Observable
final class SearchViewModel {
    var query = ""
    private(set) var results: [WrDocument] = []
    private(set) var presentations: [Presentation] = []
    private(set) var isSearching = false
    private(set) var errorMessage: String?
    private(set) var lastSearchedQuery = ""

    private let repository: DocumentsRepository

    init(repository: DocumentsRepository) {
        self.repository = repository
    }

    var hasResults: Bool { !results.isEmpty || !presentations.isEmpty }

    var trimmedQuery: String { query.trimmingCharacters(in: .whitespacesAndNewlines) }

    /// The words of the search shown, to mark them in the snippets.
    var terms: SearchTerms { SearchTerms(lastSearchedQuery) }

    /// The text of the step the words were found in, when the document was found by its
    /// content: the backend sends that step as the document's only content, and the device
    /// answers with the whole document. Nil for a document found by its title alone.
    func snippet(of document: WrDocument) -> String? {
        let terms = terms
        guard !terms.isEmpty else { return nil }
        return document.bodySteps
            .first { step in Self.proseTypes.contains(step.type.number) && step.text.map(terms.matches) == true }
            .flatMap(\.text)
    }

    /// Message, check item, list item, code block: the steps the searches look into.
    private static let proseTypes: Set<Int> = [
        StoryType.text.number, StoryType.checkItem.number, StoryType.unorderedListItem.number, StoryType.codeBlock.number,
    ]

    /// Waits for the user to stop typing before hitting the repository. Cancelled by SwiftUI
    /// when the query changes again.
    func search(debounce: Duration = .milliseconds(300)) async {
        let current = trimmedQuery
        guard !current.isEmpty else {
            results = []
            presentations = []
            errorMessage = nil
            lastSearchedQuery = ""
            return
        }

        do {
            try await Task.sleep(for: debounce)
        } catch {
            return
        }

        isSearching = true
        defer { isSearching = false }

        do {
            let found = try await repository.searchAll(query: current)
            guard !Task.isCancelled else { return }
            results = found.documents
            presentations = found.presentations
            lastSearchedQuery = current
            errorMessage = nil
        } catch is CancellationError {
            return
        } catch {
            errorMessage = error.userMessage
        }
    }
}

public extension EnvironmentValues {
    /// Grows every time the search shortcut (Cmd+F) asks for the search: the field takes the
    /// cursor, so the words go straight in.
    @Entry var searchFocusRequest = 0
}

public struct SearchRootView: View {
    @Environment(AppSession.self) private var session

    public init() {}

    public var body: some View {
        SearchView(
            repository: session.documents,
            aiClient: session.aiClient,
            publishing: session.publishing,
            imageUploader: session.imageUploader,
            isPremium: session.user?.isPremium ?? false,
            presentations: session.presentationsSource,
            writingAgent: session.writingAgent,
            headerImages: session.headerImages,
            equations: session.equations,
            loadActivity: session.contentLoad,
            loadPresentation: { id in await session.presentation(id: id) }
        )
            .id("\(session.workspace?.id ?? "")-\(session.documentsVersion)")
    }
}

struct SearchView: View {
    @State private var viewModel: SearchViewModel
    @State private var path: [DocumentsRoute] = []
    private let repository: DocumentsRepository
    private let aiClient: AiStreaming?
    private let publishing: DocumentPublishing?
    private let imageUploader: ImageUploading?
    private let isPremium: Bool
    private let presentations: PresentationsSource?
    private let writingAgent: WritingAgentAPI?
    private let headerImages: HeaderImagesAPI?
    private let equations: EquationAPI?
    private let loadActivity: ContentLoadActivity?
    private let loadPresentation: (String) async -> Presentation?
    /// The presentation shown full screen (the phones; the Mac opens a window).
    @State private var presentationShown: Presentation?
    @Environment(\.openWindow) private var openWindow
    @Environment(\.searchFocusRequest) private var searchFocusRequest
    @FocusState private var isSearchFocused: Bool

    init(
        repository: DocumentsRepository,
        aiClient: AiStreaming?,
        publishing: DocumentPublishing?,
        imageUploader: ImageUploading?,
        isPremium: Bool,
        presentations: PresentationsSource? = nil,
        writingAgent: WritingAgentAPI? = nil,
        headerImages: HeaderImagesAPI? = nil,
        equations: EquationAPI? = nil,
        loadActivity: ContentLoadActivity? = nil,
        loadPresentation: @escaping (String) async -> Presentation? = { _ in nil }
    ) {
        self.repository = repository
        self.aiClient = aiClient
        self.publishing = publishing
        self.imageUploader = imageUploader
        self.isPremium = isPremium
        self.presentations = presentations
        self.writingAgent = writingAgent
        self.headerImages = headerImages
        self.equations = equations
        self.loadActivity = loadActivity
        self.loadPresentation = loadPresentation
        _viewModel = State(initialValue: SearchViewModel(repository: repository))
    }

    var body: some View {
        NavigationStack(path: $path) {
            List {
                if !viewModel.presentations.isEmpty {
                    if !viewModel.results.isEmpty {
                        Section("Documents") {
                            documentRows
                        }
                    }
                    Section("Presentations") {
                        ForEach(viewModel.presentations) { presentation in
                            Button {
                                openPresentation(presentation)
                            } label: {
                                PresentationSearchRow(presentation: presentation)
                            }
                            .buttonStyle(.plain)
                            .accessibilityIdentifier("search.presentation.\(presentation.id)")
                        }
                    }
                } else {
                    documentRows
                }
            }
            .overlay { overlay }
            .navigationTitle("Search")
            .searchable(text: $viewModel.query, prompt: "Search documents")
            .searchFieldFocused($isSearchFocused)
            .autocorrectionDisabled()
            // The shortcut brought the search up: the cursor goes in the field. On the Mac the
            // screen itself is the search, so showing it does the same (a turn later, once the
            // field is in the toolbar).
            .onChange(of: searchFocusRequest) { _, _ in focusField() }
            #if os(macOS)
            .onAppear { focusField() }
            #endif
            .task(id: viewModel.trimmedQuery) {
                await viewModel.search()
            }
            .navigationDestination(for: DocumentsRoute.self) { route in
                switch route {
                case .document(let id, let title, let searchQuery):
                    NoteEditorView(
                        documentId: id,
                        title: title,
                        repository: repository,
                        aiClient: aiClient,
                        publishing: publishing,
                        imageUploader: imageUploader,
                        isPremium: isPremium,
                        presentations: presentations,
                        writingAgent: writingAgent,
                        imageGenerator: writingAgent,
                        headerImages: headerImages,
                        equations: equations,
                        loadActivity: loadActivity,
                        searchQuery: searchQuery
                    ) { link in
                        path.append(.document(id: link.id, title: link.title ?? "Untitled"))
                    }
                case .folder(let folder):
                    Text(folder.displayTitle)
                case .favorites:
                    // Search only opens documents; the favorites live in the Documents tab.
                    Text("Favorites")
                case .tag(let tag):
                    Text(tag.name)
                case .graph:
                    Text("Documents graph")
                }
            }
        }
        #if os(iOS)
        .fullScreenCover(item: $presentationShown) { presentation in
            NavigationStack {
                PresentationView(presentation: presentation)
                    .toolbar {
                        ToolbarItem(placement: .confirmationAction) {
                            Button("Done") { presentationShown = nil }
                                .accessibilityIdentifier("presentation.done")
                        }
                    }
            }
        }
        #endif
    }

    /// A document found by its words opens with those words highlighted; one found by its
    /// title opens as usual.
    @ViewBuilder
    private var documentRows: some View {
        ForEach(viewModel.results) { document in
            let snippet = viewModel.snippet(of: document)
            NavigationLink(
                value: DocumentsRoute.document(
                    id: document.id,
                    title: document.displayTitle,
                    searchQuery: snippet == nil ? nil : viewModel.lastSearchedQuery
                )
            ) {
                SearchDocumentRow(document: document, snippet: snippet, terms: viewModel.terms)
            }
            .accessibilityIdentifier("search.document.\(document.id)")
        }
    }

    private func focusField() {
        DispatchQueue.main.async { isSearchFocused = true }
    }

    /// A presentation opens in its own window on the Mac, and over the whole screen on the phones,
    /// like from the editor. The search only has its title, so the slides are loaded first.
    private func openPresentation(_ presentation: Presentation) {
        #if os(macOS)
        openWindow(value: PresentationWindowRef(presentationId: presentation.id))
        #else
        Task {
            presentationShown = await loadPresentation(presentation.id)
        }
        #endif
    }

    @ViewBuilder
    private var overlay: some View {
        if viewModel.trimmedQuery.isEmpty {
            ContentUnavailableView(
                "Search your documents",
                systemImage: "magnifyingglass",
                description: Text("Find documents by their title or content, and presentations by their title.")
            )
        } else if viewModel.isSearching && !viewModel.hasResults {
            ProgressView()
        } else if let errorMessage = viewModel.errorMessage {
            ContentUnavailableView("Search failed", systemImage: "exclamationmark.triangle", description: Text(errorMessage))
        } else if !viewModel.hasResults && viewModel.lastSearchedQuery == viewModel.trimmedQuery {
            ContentUnavailableView.search(text: viewModel.trimmedQuery)
        }
    }
}

private extension View {
    /// `searchFocused`, where the system has it (iOS 18, macOS 15); older systems keep the
    /// field as it is.
    @ViewBuilder
    func searchFieldFocused(_ binding: FocusState<Bool>.Binding) -> some View {
        if #available(iOS 18, macOS 15, *) {
            searchFocused(binding)
        } else {
            self
        }
    }
}

/// A document found by the search: its title, then the line the words were found in with the
/// words in bold, or the preview of the document when it was found by its title.
struct SearchDocumentRow: View {
    let document: WrDocument
    let snippet: String?
    let terms: SearchTerms

    var body: some View {
        Label {
            VStack(alignment: .leading, spacing: 3) {
                HStack {
                    Text(document.displayTitle)
                        .font(.body.weight(.medium))
                        .foregroundStyle(WrColors.textLight)
                        .lineLimit(1)
                    if document.isFavorite {
                        Image(systemName: "star.fill")
                            .font(.caption)
                            .foregroundStyle(.yellow)
                    }
                }

                if let snippet {
                    Text(highlighted(terms.snippet(of: snippet)))
                        .font(.subheadline)
                        .foregroundStyle(WrColors.textLighter)
                        .lineLimit(1)
                } else if !document.preview.isEmpty {
                    Text(document.preview)
                        .font(.subheadline)
                        .foregroundStyle(WrColors.textLighter)
                        .lineLimit(2)
                }

                if document.lastUpdatedAt > 0 {
                    Text(document.lastUpdatedDate, format: .relative(presentation: .named))
                        .font(.caption)
                        .foregroundStyle(WrColors.textLighter)
                }
            }
        } icon: {
            Image(systemName: "doc.text")
                .foregroundStyle(WrColors.textLighter)
        }
    }

    /// `text` with the words of the search in bold and the main text color.
    private func highlighted(_ text: String) -> AttributedString {
        var attributed = AttributedString(text)
        for range in terms.ranges(in: text) {
            guard let bounds = Range(range, in: attributed) else { continue }
            attributed[bounds].font = .subheadline.weight(.bold)
            attributed[bounds].foregroundColor = WrColors.textLight
        }
        return attributed
    }
}

/// A presentation found by the search: its title and when it was made.
struct PresentationSearchRow: View {
    let presentation: Presentation

    var body: some View {
        Label {
            VStack(alignment: .leading, spacing: 3) {
                Text(presentation.title.isEmpty ? "Untitled" : presentation.title)
                    .font(.body.weight(.medium))
                    .foregroundStyle(WrColors.textLight)
                    .lineLimit(1)
                if presentation.createdAt > 0 {
                    Text(
                        Date(timeIntervalSince1970: TimeInterval(presentation.createdAt) / 1000),
                        format: .relative(presentation: .named)
                    )
                    .font(.caption)
                    .foregroundStyle(WrColors.textLighter)
                }
            }
        } icon: {
            Image(systemName: "play.rectangle")
                .foregroundStyle(WrColors.textLighter)
        }
        .contentShape(Rectangle())
    }
}
