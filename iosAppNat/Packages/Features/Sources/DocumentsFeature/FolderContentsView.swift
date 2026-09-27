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

    /// Folders and documents shown together in one grid, folders first.
    var items: [FolderItem] {
        folders.map(FolderItem.folder) + documents.map(FolderItem.document)
    }

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

    private let columns = [GridItem(.adaptive(minimum: 150, maximum: 240), spacing: 12)]

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
        ScrollView {
            LazyVGrid(columns: columns, spacing: 12) {
                ForEach(viewModel.items) { item in
                    NavigationLink(value: item.route) {
                        ItemCard(item: item)
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding()
        }
        .background(WrColors.background)
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

enum FolderItem: Identifiable {
    case folder(Folder)
    case document(WrDocument)

    var id: String {
        switch self {
        case .folder(let folder): "folder-\(folder.id)"
        case .document(let document): "document-\(document.id)"
        }
    }

    var route: DocumentsRoute {
        switch self {
        case .folder(let folder): .folder(folder)
        case .document(let document): .document(id: document.id, title: document.displayTitle)
        }
    }
}

/// A folder or a document in the folder grid.
struct ItemCard: View {
    let item: FolderItem

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(alignment: .top) {
                Image(systemName: icon)
                    .font(.title2)
                    .foregroundStyle(isFolder ? WrColors.accent : WrColors.textLighter)
                Spacer()
                if isFavorite {
                    Image(systemName: "star.fill")
                        .font(.caption)
                        .foregroundStyle(.yellow)
                }
            }

            Text(title)
                .font(.headline)
                .foregroundStyle(WrColors.textLight)
                .lineLimit(2)
                .multilineTextAlignment(.leading)

            if let preview, !preview.isEmpty {
                Text(preview)
                    .font(.caption)
                    .foregroundStyle(WrColors.textLighter)
                    .lineLimit(3)
                    .multilineTextAlignment(.leading)
            }

            Spacer(minLength: 0)

            footer
                .font(.caption2)
                .foregroundStyle(WrColors.textLighter)
        }
        .padding(14)
        .frame(maxWidth: .infinity, minHeight: 150, alignment: .topLeading)
        .background(WrColors.surface, in: RoundedRectangle(cornerRadius: 16))
        .overlay {
            RoundedRectangle(cornerRadius: 16)
                .strokeBorder(WrColors.divider)
        }
        .contentShape(RoundedRectangle(cornerRadius: 16))
    }

    private var isFolder: Bool {
        if case .folder = item { true } else { false }
    }

    private var icon: String {
        isFolder ? "folder.fill" : "doc.text"
    }

    private var title: String {
        switch item {
        case .folder(let folder): folder.displayTitle
        case .document(let document): document.displayTitle
        }
    }

    private var isFavorite: Bool {
        switch item {
        case .folder(let folder): folder.favorite
        case .document(let document): document.isFavorite
        }
    }

    private var preview: String? {
        if case .document(let document) = item { document.preview } else { nil }
    }

    @ViewBuilder
    private var footer: some View {
        switch item {
        case .folder(let folder):
            Text(folder.itemCount == 1 ? "1 item" : "\(folder.itemCount) items")
        case .document(let document):
            if document.lastUpdatedAt > 0 {
                Text(document.lastUpdatedDate, format: .relative(presentation: .named))
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
