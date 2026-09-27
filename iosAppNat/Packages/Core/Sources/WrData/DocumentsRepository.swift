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
}

public enum MoveError: Error, Equatable {
    /// A folder can't be moved into itself or into one of its subfolders.
    case folderIntoItself

    public var userMessage: String {
        switch self {
        case .folderIntoItself: "A folder can't be moved into itself."
        }
    }
}

extension DocumentsRepository {
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
