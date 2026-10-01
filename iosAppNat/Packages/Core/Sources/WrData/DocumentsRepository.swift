import Foundation
import WrModels
import WrNetwork

/// Source of folders and documents for a single workspace. The open space reads from the
/// backend and the private space from files on the device.
public protocol DocumentsRepository: AnyObject {
    func folderContents(folderId: String) async throws -> FolderContents
    func document(id: String) async throws -> WrDocument
    func search(query: String) async throws -> [WrDocument]
    func createFolder(title: String, parentId: String) async throws -> Folder
    func createDocument(title: String, parentId: String) async throws -> WrDocument
    func moveDocument(id: String, toFolder folderId: String) async throws
    func moveFolder(id: String, toFolder folderId: String) async throws
    /// Stores the document as it is now in the editor.
    func save(_ document: WrDocument) async throws
    func deleteDocument(id: String) async throws

    // Edition menu of a folder (Compose `EditFileDialog` and icon picker).

    /// The folder with `id`, or nil when it doesn't exist (or was deleted).
    func folder(id: String) async throws -> Folder?
    /// Stores a new title or icon of the folder, and sends it to the backend when there's one.
    @discardableResult
    func updateFolder(_ folder: Folder) async throws -> Folder

    // Selection menu of the documents list (Compose `NotesSelectionMenu`).

    /// Copies documents with new ids; folders are copied with what's inside them.
    func duplicate(ids: [String]) async throws
    /// Marks documents and folders as favorites, or removes them from the favorites.
    func setFavorite(ids: [String], favorite: Bool) async throws
    /// Deletes documents, and folders with everything inside them.
    func deleteItems(ids: [String]) async throws

    /// Every favorite folder and document of the workspace, wherever they are, for the
    /// "Favorites" screen of the Compose app.
    func favorites() async throws -> FolderContents
}

public enum MoveError: Error, Equatable {
    /// A folder can't be moved into itself or into one of its subfolders.
    case folderIntoItself

    public var userMessage: String {
        switch self {
        case .folderIntoItself: String(localized: "A folder can't be moved into itself.")
        }
    }
}

extension DocumentsRepository {
    // Sources without folders of their own (editor previews and tests) don't need to offer these.
    public func folder(id: String) async throws -> Folder? { nil }

    @discardableResult
    public func updateFolder(_ folder: Folder) async throws -> Folder { throw APIError.notFound }

    /// The folders from the root down to `folderId`, the folder included. Empty for the root.
    public func folderPath(to folderId: String) async throws -> [Folder] {
        var path: [Folder] = []
        var current = folderId
        var visited: Set<String> = []
        while current != Folder.rootId, visited.insert(current).inserted, let folder = try await folder(id: current) {
            path.insert(folder, at: 0)
            current = folder.parentId
        }
        return path
    }

    /// Walks the tree from the root and keeps the favorites. Stores with a database answer
    /// this with a query instead.
    public func favorites() async throws -> FolderContents {
        var result = FolderContents()
        var pending = [Folder.rootId]
        var visited: Set<String> = []
        var seen: Set<String> = []
        while let folderId = pending.first {
            pending.removeFirst()
            guard visited.insert(folderId).inserted else { continue }
            let contents = try await folderContents(folderId: folderId)
            result.folders += contents.folders.filter { $0.favorite && seen.insert($0.id).inserted }
            result.documents += contents.documents.filter { $0.isFavorite && seen.insert($0.id).inserted }
            pending += contents.folders.map(\.id)
        }
        return result
    }

    /// A new document is stored with its title as the first step, like the Compose editor does.
    func newDocument(title: String, parentId: String, workspaceId: String) -> WrDocument {
        WrDocument(
            id: UUID().uuidString,
            title: title,
            workspaceId: workspaceId,
            content: [StoryStep(type: .title, text: title, position: 0)],
            parentId: parentId
        )
    }
}

public final class RemoteDocumentsRepository: DocumentsRepository {
    private let client: APIClient
    public let workspaceId: String

    public init(client: APIClient, workspaceId: String) {
        self.client = client
        self.workspaceId = workspaceId
    }

    private var base: String { "api/docs/workspace/\(workspaceId)" }

    public func folderContents(folderId: String) async throws -> FolderContents {
        let contents: FolderContents = try await client.get("\(base)/folder/\(folderId)/contents")
        return FolderContents(
            folders: contents.folders.sorted { $0.title.localizedCaseInsensitiveCompare($1.title) == .orderedAscending },
            documents: contents.documents.filter { !$0.id.isEmpty }.sorted { $0.lastUpdatedAt > $1.lastUpdatedAt }
        )
    }

    public func document(id: String) async throws -> WrDocument {
        try await client.get("\(base)/document/\(id)")
    }

    public func search(query: String) async throws -> [WrDocument] {
        let trimmed = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return [] }
        return try await client.get("\(base)/document/search", query: [URLQueryItem(name: "q", value: trimmed)])
    }

    public func createFolder(title: String, parentId: String) async throws -> Folder {
        struct Body: Encodable { let title: String }
        return try await client.send(.post, "\(base)/folder/\(parentId)/create", body: Body(title: title))
    }

    public func createDocument(title: String, parentId: String) async throws -> WrDocument {
        struct Body: Encodable { let document: WrDocument }
        let document = newDocument(title: title, parentId: parentId, workspaceId: workspaceId)
        return try await client.send(.post, "\(base)/document/upsert", body: Body(document: document))
    }

    public func moveDocument(id: String, toFolder folderId: String) async throws {
        try await client.perform(.post, "\(base)/document/\(id)/move", body: MoveBody(targetParentId: folderId))
    }

    public func deleteDocument(id: String) async throws {
        try await deleteDocuments(ids: [id])
    }

    public func deleteDocuments(ids: [String]) async throws {
        struct Body: Encodable { let documentIds: [String] }
        try await client.perform(.post, "\(base)/document/delete", body: Body(documentIds: ids))
    }

    public func deleteFolder(id: String) async throws {
        try await client.perform(.delete, "\(base)/folder/\(id)")
    }

    public func setFavorite(documentId: String, favorite: Bool) async throws {
        struct Body: Encodable { let favorite: Bool }
        try await client.perform(.post, "\(base)/document/\(documentId)/favorite", body: Body(favorite: favorite))
    }

    // Only the synced repository offers these; the remote one isn't used on its own.
    public func duplicate(ids: [String]) async throws { throw APIError.notFound }
    public func setFavorite(ids: [String], favorite: Bool) async throws {
        for id in ids { try await setFavorite(documentId: id, favorite: favorite) }
    }
    public func deleteItems(ids: [String]) async throws { try await deleteDocuments(ids: ids) }

    // The backend has no endpoint for a single folder; the synced repository reads its cache.
    public func folder(id: String) async throws -> Folder? { nil }

    public func updateFolder(_ folder: Folder) async throws -> Folder {
        struct Body: Encodable {
            let folders: [FolderApiBody]
            let workspaceId: String
        }
        var folder = folder
        folder.lastUpdatedAt = Date()
        try await client.perform(.post, "api/docs/workspace/folder", body: Body(folders: [folder.api], workspaceId: workspaceId))
        return folder
    }

    public func save(_ document: WrDocument) async throws {
        struct Body: Encodable { let document: WrDocument }
        try await client.perform(.post, "\(base)/document/upsert", body: Body(document: document))
    }

    public func moveFolder(id: String, toFolder folderId: String) async throws {
        guard id != folderId else { throw MoveError.folderIntoItself }

        do {
            try await client.perform(.post, "\(base)/folder/\(id)/move", body: MoveBody(targetParentId: folderId))
        } catch APIError.badRequest {
            // The backend answers 400 when the target is inside the moved folder.
            throw MoveError.folderIntoItself
        }
    }

    private struct MoveBody: Encodable {
        let targetParentId: String
    }
}
