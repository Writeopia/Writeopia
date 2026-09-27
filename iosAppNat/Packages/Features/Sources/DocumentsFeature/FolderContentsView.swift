import Observation
import SwiftUI
import WrData
import WrDesign
import WrModels
import WrNetwork
import WrSession

@Observable
final class FolderContentsViewModel {
    private(set) var folders: [Folder] = []
    private(set) var documents: [WrDocument] = []
    private(set) var isLoading = false
    private(set) var errorMessage: String?
    var actionError: String?

    let folderId: String
    private let repository: DocumentsRepository

    init(folderId: String, repository: DocumentsRepository) {
        self.folderId = folderId
        self.repository = repository
    }

    var isEmpty: Bool { folders.isEmpty && documents.isEmpty }

    func load() async {
        isLoading = true
        defer { isLoading = false }

        do {
            let contents = try await repository.folderContents(folderId: folderId)
            folders = contents.folders
            documents = contents.documents
            errorMessage = nil
        } catch is CancellationError {
            return
        } catch {
            errorMessage = error.userMessage
        }
    }

    func createFolder(title: String) async {
        await perform {
            _ = try await self.repository.createFolder(title: self.normalized(title, fallback: "New folder"), parentId: self.folderId)
        }
    }

    func createDocument(title: String) async -> WrDocument? {
        var created: WrDocument?
        await perform {
            created = try await self.repository.createDocument(
                title: self.normalized(title, fallback: "Untitled"),
                parentId: self.folderId
            )
        }
        return created
    }

    private func perform(_ operation: () async throws -> Void) async {
        do {
            try await operation()
            await load()
        } catch {
            actionError = error.userMessage
        }
    }

    private func normalized(_ title: String, fallback: String) -> String {
        let trimmed = title.trimmingCharacters(in: .whitespacesAndNewlines)
        return trimmed.isEmpty ? fallback : trimmed
    }
}

public enum DocumentsRoute: Hashable {
    case folder(Folder)
    case document(id: String, title: String)
}

/// Documents tab: the folders and documents of the current workspace.
public struct DocumentsRootView: View {
    @Environment(AppSession.self) private var session
    @State private var path = NavigationPath()

    public init() {}

    public var body: some View {
        NavigationStack(path: $path) {
            FolderContentsView(
                folderId: Folder.rootId,
                title: session.workspace?.name ?? "Documents",
                repository: session.documents,
                path: $path
            )
            .navigationDestination(for: DocumentsRoute.self) { route in
                switch route {
                case .folder(let folder):
                    FolderContentsView(
                        folderId: folder.id,
                        title: folder.displayTitle,
                        repository: session.documents,
                        path: $path
                    )
                case .document(let id, let title):
                    DocumentView(documentId: id, title: title, repository: session.documents)
                }
            }
        }
        // A different workspace means a different tree: start again from its root.
        .id(session.workspace?.id)
    }
}

struct FolderContentsView: View {
    @State private var viewModel: FolderContentsViewModel
    @Binding private var path: NavigationPath
    @State private var newItem: NewItem?
    @State private var newItemTitle = ""
    private let title: String

    private enum NewItem: String, Identifiable {
        case folder = "New folder"
        case document = "New document"

        var id: String { rawValue }
    }

    init(folderId: String, title: String, repository: DocumentsRepository, path: Binding<NavigationPath>) {
        _viewModel = State(initialValue: FolderContentsViewModel(folderId: folderId, repository: repository))
        _path = path
        self.title = title
    }

    var body: some View {
        List {
            if !viewModel.folders.isEmpty {
                Section("Folders") {
                    ForEach(viewModel.folders) { folder in
                        NavigationLink(value: DocumentsRoute.folder(folder)) {
                            FolderRow(folder: folder)
                        }
                    }
                }
            }

            if !viewModel.documents.isEmpty {
                Section("Documents") {
                    ForEach(viewModel.documents) { document in
                        NavigationLink(value: DocumentsRoute.document(id: document.id, title: document.displayTitle)) {
                            DocumentRow(document: document)
                        }
                    }
                }
            }
        }
        .overlay {
            WrStateOverlay(
                isLoading: viewModel.isLoading,
                isEmpty: viewModel.isEmpty,
                errorMessage: viewModel.errorMessage,
                emptyTitle: "This folder is empty",
                emptyImage: "folder",
                retry: { Task { await viewModel.load() } }
            )
        }
        .navigationTitle(title)
        .toolbar {
            ToolbarItem(placement: .primaryAction) {
                Menu {
                    Button {
                        present(.document)
                    } label: {
                        Label("New document", systemImage: "doc.badge.plus")
                    }
                    Button {
                        present(.folder)
                    } label: {
                        Label("New folder", systemImage: "folder.badge.plus")
                    }
                } label: {
                    Label("Add", systemImage: "plus")
                }
                .accessibilityIdentifier("documents.add")
            }
        }
        .task { await viewModel.load() }
        .refreshable { await viewModel.load() }
        .alert(
            newItem?.rawValue ?? "",
            isPresented: Binding(get: { newItem != nil }, set: { if !$0 { newItem = nil } }),
            presenting: newItem
        ) { item in
            TextField("Title", text: $newItemTitle)
            Button("Cancel", role: .cancel) {}
            Button("Create") { create(item) }
        }
        .alert(
            "Something went wrong",
            isPresented: Binding(
                get: { viewModel.actionError != nil },
                set: { if !$0 { viewModel.actionError = nil } }
            )
        ) {
            Button("OK", role: .cancel) {}
        } message: {
            Text(viewModel.actionError ?? "")
        }
    }

    private func present(_ item: NewItem) {
        newItemTitle = ""
        newItem = item
    }

    private func create(_ item: NewItem) {
        let title = newItemTitle
        Task {
            switch item {
            case .folder:
                await viewModel.createFolder(title: title)
            case .document:
                if let document = await viewModel.createDocument(title: title) {
                    path.append(DocumentsRoute.document(id: document.id, title: document.displayTitle))
                }
            }
        }
    }
}

struct FolderRow: View {
    let folder: Folder

    var body: some View {
        LabeledContent {
            Text("\(folder.itemCount)")
                .monospacedDigit()
        } label: {
            Label {
                Text(folder.displayTitle)
                    .foregroundStyle(WrColors.textLight)
            } icon: {
                Image(systemName: folder.favorite ? "star.square.on.square" : "folder.fill")
                    .foregroundStyle(WrColors.accent)
            }
        }
    }
}

public struct DocumentRow: View {
    let document: WrDocument

    public init(document: WrDocument) {
        self.document = document
    }

    public var body: some View {
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

                if !document.preview.isEmpty {
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
}
