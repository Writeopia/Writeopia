import Foundation
import WrData
import WrModels

/// What the agent needs to file the open document: where it is, where it could go, and how to
/// move it. The editor provides this; the agent decides when to offer it.
public protocol DocumentFolders: AnyObject {
    /// The id of the folder the document is in; `Folder.rootId` at the root.
    func currentFolderId() -> String
    /// The folders the document could move to, at most `limit`, in the order the list shows them.
    func candidates(limit: Int) async throws -> [FolderCandidate]
    func moveTo(folderId: String) async throws
    /// Makes a folder for the document, named by the AI from its title and text, and moves the
    /// document there. Returns the name of the new folder.
    func createFolderAndMove(documentTitle: String, documentText: String) async throws -> String
}

/// Files the open document with the app's documents repository. The new folder is created at the
/// root of the workspace, named by the AI the user picked.
public final class RepositoryDocumentFolders: DocumentFolders {
    private let documentId: String
    private let parentId: () -> String
    private let repository: DocumentsRepository
    private let resolveAi: () -> AiStreaming?
    /// Tells the editor the document moved, so what it saves next keeps the new folder.
    private let onMoved: (String) -> Void

    public init(
        documentId: String,
        parentId: @escaping () -> String,
        repository: DocumentsRepository,
        resolveAi: @escaping () -> AiStreaming?,
        onMoved: @escaping (String) -> Void = { _ in }
    ) {
        self.documentId = documentId
        self.parentId = parentId
        self.repository = repository
        self.resolveAi = resolveAi
        self.onMoved = onMoved
    }

    public func currentFolderId() -> String { parentId() }

    public func candidates(limit: Int) async throws -> [FolderCandidate] {
        try await repository.folderContents(folderId: Folder.rootId).folders
            .filter { !$0.deleted && !$0.title.isEmpty }
            .prefix(limit)
            .map { FolderCandidate(id: $0.id, name: $0.title) }
    }

    public func moveTo(folderId: String) async throws {
        WritingActionExecutor.logger.info("moving document \(self.documentId, privacy: .public) to folder \(folderId, privacy: .public)")
        try await repository.moveDocument(id: documentId, toFolder: folderId)
        onMoved(folderId)
    }

    public func createFolderAndMove(documentTitle: String, documentText: String) async throws -> String {
        guard let ai = resolveAi() else { throw WritingAgentError.noAi }
        let name = try await WritingActionExecutor.oneLine(
            ai,
            instructions: WritingActionExecutor.folderNamePrompt,
            text: "Title: \(documentTitle)\n\n\(documentText)"
        )
        guard !name.isEmpty else { throw WritingAgentError.emptyAnswer }

        let folder = try await repository.createFolder(title: name, parentId: Folder.rootId)
        WritingActionExecutor.logger.info("created folder \"\(name, privacy: .public)\" (\(folder.id, privacy: .public))")
        try await moveTo(folderId: folder.id)
        return name
    }
}
