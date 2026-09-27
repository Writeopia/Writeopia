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
    private(set) var isSearching = false
    private(set) var errorMessage: String?
    private(set) var lastSearchedQuery = ""

    private let repository: DocumentsRepository

    init(repository: DocumentsRepository) {
        self.repository = repository
    }

    var trimmedQuery: String { query.trimmingCharacters(in: .whitespacesAndNewlines) }

    /// Waits for the user to stop typing before hitting the repository. Cancelled by SwiftUI
    /// when the query changes again.
    func search(debounce: Duration = .milliseconds(300)) async {
        let current = trimmedQuery
        guard !current.isEmpty else {
            results = []
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
            let found = try await repository.search(query: current)
            guard !Task.isCancelled else { return }
            results = found
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
            aiClient: session.isOnline ? session.aiAPI : nil,
            publishing: session.publishing,
            isPremium: session.user?.isPremium ?? false
        )
            .id(session.workspace?.id)
    }
}

struct SearchView: View {
    @State private var viewModel: SearchViewModel
    @State private var path: [DocumentsRoute] = []
    private let repository: DocumentsRepository
    private let aiClient: AiStreaming?
    private let publishing: DocumentPublishing?
    private let isPremium: Bool

    init(repository: DocumentsRepository, aiClient: AiStreaming?, publishing: DocumentPublishing?, isPremium: Bool) {
        self.repository = repository
        self.aiClient = aiClient
        self.publishing = publishing
        self.isPremium = isPremium
        _viewModel = State(initialValue: SearchViewModel(repository: repository))
    }

    var body: some View {
        NavigationStack(path: $path) {
            List(viewModel.results) { document in
                NavigationLink(value: DocumentsRoute.document(id: document.id, title: document.displayTitle)) {
                    DocumentRow(document: document)
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
                        isPremium: isPremium
                    ) { link in
                        path.append(.document(id: link.id, title: link.title ?? "Untitled"))
                    }
                case .folder(let folder):
                    Text(folder.displayTitle)
                }
            }
        }
    }

    @ViewBuilder
    private var overlay: some View {
        if viewModel.trimmedQuery.isEmpty {
            ContentUnavailableView(
                "Search your documents",
                systemImage: "magnifyingglass",
                description: Text("Find documents by their title or content.")
            )
        } else if viewModel.isSearching && viewModel.results.isEmpty {
            ProgressView()
        } else if let errorMessage = viewModel.errorMessage {
            ContentUnavailableView("Search failed", systemImage: "exclamationmark.triangle", description: Text(errorMessage))
        } else if viewModel.results.isEmpty && viewModel.lastSearchedQuery == viewModel.trimmedQuery {
            ContentUnavailableView.search(text: viewModel.trimmedQuery)
        }
    }
}
