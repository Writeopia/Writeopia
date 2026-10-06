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
    private let loadPresentation: (String) async -> Presentation?
    /// The presentation shown full screen (the phones; the Mac opens a window).
    @State private var presentationShown: Presentation?
    @Environment(\.openWindow) private var openWindow

    init(
        repository: DocumentsRepository,
        aiClient: AiStreaming?,
        publishing: DocumentPublishing?,
        imageUploader: ImageUploading?,
        isPremium: Bool,
        presentations: PresentationsSource? = nil,
        loadPresentation: @escaping (String) async -> Presentation? = { _ in nil }
    ) {
        self.repository = repository
        self.aiClient = aiClient
        self.publishing = publishing
        self.imageUploader = imageUploader
        self.isPremium = isPremium
        self.presentations = presentations
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
            .autocorrectionDisabled()
            .task(id: viewModel.trimmedQuery) {
                await viewModel.search()
            }
            .navigationDestination(for: DocumentsRoute.self) { route in
                switch route {
                case .document(let id, let title):
                    NoteEditorView(
                        documentId: id,
                        title: title,
                        repository: repository,
                        aiClient: aiClient,
                        publishing: publishing,
                        imageUploader: imageUploader,
                        isPremium: isPremium,
                        presentations: presentations
                    ) { link in
                        path.append(.document(id: link.id, title: link.title ?? "Untitled"))
                    }
                case .folder(let folder):
                    Text(folder.displayTitle)
                case .favorites:
                    // Search only opens documents; the favorites live in the Documents tab.
                    Text("Favorites")
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

    @ViewBuilder
    private var documentRows: some View {
        ForEach(viewModel.results) { document in
            NavigationLink(value: DocumentsRoute.document(id: document.id, title: document.displayTitle)) {
                DocumentRow(document: document)
            }
        }
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
