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

/// Stores the private space on disk as JSON, one file per folder and document, using the same
/// `.wrdoc.json` / `.wrfolder.json` naming as the desktop app.
public final class LocalDocumentsRepository: DocumentsRepository {
    private let directory: URL
    private let fileManager = FileManager.default
    private let encoder: JSONEncoder = {
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
        return encoder
    }()

    public init(directory: URL = LocalDocumentsRepository.defaultDirectory) {
        self.directory = directory
    }

    public static var defaultDirectory: URL {
        URL.applicationSupportDirectory.appending(path: "Writeopia/PrivateSpace", directoryHint: .isDirectory)
    }

    public func folderContents(folderId: String) async throws -> FolderContents {
        try seedIfNeeded()
        let folders = try allFolders()
        let documents = try allDocuments()

        let childFolders = folders
            .filter { $0.parentId == folderId }
            .map { folder -> Folder in
                var folder = folder
                folder.itemCount = folders.count(where: { $0.parentId == folder.id }) +
                    documents.count(where: { $0.parentId == folder.id })
                return folder
            }
            .sorted { $0.title.localizedCaseInsensitiveCompare($1.title) == .orderedAscending }

        let childDocuments = documents
            .filter { ($0.parentId ?? Folder.rootId) == folderId }
            .sorted { $0.lastUpdatedAt > $1.lastUpdatedAt }

        return FolderContents(folders: childFolders, documents: childDocuments)
    }

    public func document(id: String) async throws -> WrDocument {
        try seedIfNeeded()
        guard let document = try allDocuments().first(where: { $0.id == id }) else {
            throw APIError.notFound
        }
        return document
    }

    public func search(query: String) async throws -> [WrDocument] {
        let trimmed = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return [] }
        try seedIfNeeded()

        return try allDocuments()
            .filter { document in
                document.title.localizedCaseInsensitiveContains(trimmed) ||
                    document.content.contains { $0.text?.localizedCaseInsensitiveContains(trimmed) == true }
            }
            .sorted { $0.lastUpdatedAt > $1.lastUpdatedAt }
    }

    public func createFolder(title: String, parentId: String) async throws -> Folder {
        try seedIfNeeded()
        let folder = Folder(id: UUID().uuidString, parentId: parentId, title: title, workspaceId: Workspace.localId)
        try save(folder)
        return folder
    }

    private func save(_ folder: Folder) throws {
        for url in try files(suffix: "wrfolder") where url.lastPathComponent.contains(folder.id) {
            try fileManager.removeItem(at: url)
        }
        // The item count is derived when listing, so it isn't stored.
        var stored = folder
        stored.itemCount = 0
        try write(stored, name: fileName(title: folder.title, id: folder.id, suffix: "wrfolder"))
    }

    public func createDocument(title: String, parentId: String) async throws -> WrDocument {
        try seedIfNeeded()
        let document = newDocument(title: title, parentId: parentId, workspaceId: Workspace.localId)
        try save(document)
        return document
    }

    public func moveDocument(id: String, toFolder folderId: String) async throws {
        var document = try await document(id: id)
        document.parentId = folderId
        document.lastUpdatedAt = Date.nowMillis
        try save(document)
    }

    public func moveFolder(id: String, toFolder folderId: String) async throws {
        let folders = try allFolders()
        guard var folder = folders.first(where: { $0.id == id }) else { throw APIError.notFound }

        // Walk up from the target: reaching the moved folder means the target is inside it.
        var ancestor: String? = folderId
        while let current = ancestor, current != Folder.rootId {
            if current == id { throw MoveError.folderIntoItself }
            ancestor = folders.first(where: { $0.id == current })?.parentId
        }

        folder.parentId = folderId
        try save(folder)
    }

    public func save(_ document: WrDocument) throws {
        for url in try files(suffix: "wrdoc") where url.lastPathComponent.contains(document.id) {
            try fileManager.removeItem(at: url)
        }
        try write(document, name: fileName(title: document.title, id: document.id, suffix: "wrdoc"))
    }

    // MARK: - Files

    private func allFolders() throws -> [Folder] {
        try files(suffix: "wrfolder").compactMap { try? JSONDecoder().decode(Folder.self, from: Data(contentsOf: $0)) }
    }

    private func allDocuments() throws -> [WrDocument] {
        try files(suffix: "wrdoc").compactMap { try? JSONDecoder().decode(WrDocument.self, from: Data(contentsOf: $0)) }
    }

    private func files(suffix: String) throws -> [URL] {
        guard fileManager.fileExists(atPath: directory.path(percentEncoded: false)) else { return [] }
        return try fileManager
            .contentsOfDirectory(at: directory, includingPropertiesForKeys: nil)
            .filter { $0.lastPathComponent.hasSuffix(".\(suffix).json") }
    }

    private func write<T: Encodable>(_ value: T, name: String) throws {
        try fileManager.createDirectory(at: directory, withIntermediateDirectories: true)
        try encoder.encode(value).write(to: directory.appending(path: name), options: .atomic)
    }

    private func fileName(title: String, id: String, suffix: String) -> String {
        let safeTitle = title
            .components(separatedBy: CharacterSet.alphanumerics.union(.whitespaces).inverted)
            .joined()
            .trimmingCharacters(in: .whitespaces)
        return "\(safeTitle.isEmpty ? "Untitled" : safeTitle)_\(id).\(suffix).json"
    }

    /// Gives a brand new private space something to look at.
    private func seedIfNeeded() throws {
        guard !fileManager.fileExists(atPath: directory.path(percentEncoded: false)) else { return }

        let welcome = WrDocument(
            id: UUID().uuidString,
            title: "Welcome to Writeopia",
            workspaceId: Workspace.localId,
            content: [
                StoryStep(type: .title, text: "Welcome to Writeopia", position: 0),
                StoryStep(
                    type: .message,
                    text: "This is your private space. Everything you write here stays on this device.",
                    position: 1
                ),
                StoryStep(type: .message, text: "Getting started", tags: [TagInfo(tag: "H2")], position: 2),
                StoryStep(type: .checkItem, text: "Create a folder to organise your notes", checked: false, position: 3),
                StoryStep(type: .checkItem, text: "Create your first document", checked: false, position: 4),
                StoryStep(
                    type: .unorderedListItem,
                    text: "Sign in from Settings > Account to sync your notes",
                    position: 5
                ),
            ],
            parentId: Folder.rootId
        )
        try save(welcome)
    }
}
