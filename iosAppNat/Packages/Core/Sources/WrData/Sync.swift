import Foundation
import WrModels
import WrNetwork

// MARK: - Merge

/// Merges the local and the backend copy of a document. Mirrors `DocumentMerger` of the Compose
/// app: steps are matched by id and the newer one wins (local wins ties and missing times),
/// steps found on one side only are kept, and the metadata (title, folder, favorite...) comes
/// from the copy updated last.
public enum DocumentMerger {
    public static func merge(local: WrDocument?, remote: WrDocument?) -> WrDocument? {
        guard let local else { return remote }
        guard let remote else { return local }

        let localSteps = Dictionary(local.content.map { ($0.id, $0) }, uniquingKeysWith: { first, _ in first })
        let remoteSteps = Dictionary(remote.content.map { ($0.id, $0) }, uniquingKeysWith: { first, _ in first })

        var merged: [StoryStep] = []
        for id in Set(localSteps.keys).union(remoteSteps.keys) {
            switch (localSteps[id], remoteSteps[id]) {
            case let (local?, remote?):
                let localTime = local.lastUpdatedAt ?? .max
                let remoteTime = remote.lastUpdatedAt ?? 0
                merged.append(localTime >= remoteTime ? local : remote)
            case let (local?, nil):
                merged.append(local)
            case let (nil, remote?):
                merged.append(remote)
            case (nil, nil):
                break
            }
        }

        var base = local.lastUpdatedAt >= remote.lastUpdatedAt ? local : remote
        base.content = merged.sorted { $0.position < $1.position }
        return base
    }
}

// MARK: - Conflicts

/// Settles the documents changed here and on the backend. Mirrors `DocumentConflictHandler`:
/// the copy updated last wins as a whole and is stored locally (documents deleted on this device
/// are never brought back), and the winners that came from this device are returned to be sent.
public enum DocumentConflictHandler {
    public static func handle(
        local: [WrDocument],
        remote: [WrDocument],
        store: LocalDocumentsRepository
    ) throws -> [WrDocument] {
        let remoteIds = Set(remote.map(\.id))
        var toSend: [WrDocument] = []

        for (id, copies) in Dictionary(grouping: local + remote, by: \.id) {
            guard let winner = copies.max(by: { $0.lastUpdatedAt < $1.lastUpdatedAt }) else { continue }
            let existing = try store.storedDocument(id: id)

            if existing?.deleted != true {
                var stored = winner
                // A document that came from the backend is in sync with it.
                if remoteIds.contains(id), existing.map({ $0.lastUpdatedAt < winner.lastUpdatedAt }) ?? true {
                    stored.lastSyncedAt = max(winner.lastUpdatedAt, winner.lastSyncedAt ?? 0)
                }
                try store.store(stored)
            }

            let localCopy = local.first { $0.id == id }
            if !remoteIds.contains(id) || (localCopy != nil && localCopy?.lastUpdatedAt == winner.lastUpdatedAt && localCopy?.isOutdated == true) {
                toSend.append(winner)
            }
        }
        return toSend
    }
}

/// Settles the folders changed here and on the backend. Mirrors `FolderConflictHandler`.
public enum FolderConflictHandler {
    public static func handle(
        local: [Folder],
        remote: [Folder],
        store: LocalDocumentsRepository
    ) throws -> [Folder] {
        var toSend: [Folder] = []
        let localById = Dictionary(local.map { ($0.id, $0) }, uniquingKeysWith: { first, _ in first })

        for external in remote {
            let stored = try store.storedFolder(id: external.id)
            if stored?.deleted == true { continue }

            // A folder changed here but not sent yet may be outside `local`: when it was moved to
            // another folder, the backend still lists it in the old one. The local change wins, or
            // the move would be undone.
            let changedHere = localById[external.id] ?? stored.flatMap { $0.isOutdated ? $0 : nil }
            if let mine = changedHere,
               (mine.lastUpdatedAt ?? .distantPast) >= (external.lastUpdatedAt ?? .distantPast) {
                toSend.append(mine)
                try store.store(mine)
            } else {
                var synced = external
                synced.lastSyncedAt = Date()
                try store.store(synced)
            }
        }

        let remoteIds = Set(remote.map(\.id))
        toSend += local.filter { !remoteIds.contains($0.id) }
        return toSend
    }
}

// MARK: - API

/// Sync endpoints of the documents service.
public final class SyncAPI {
    private let client: APIClient
    private let workspaceId: String

    public init(client: APIClient, workspaceId: String) {
        self.client = client
        self.workspaceId = workspaceId
    }

    /// Documents of `folderId` synced after `lastSync` (epoch ms), and all its subfolders.
    public func folderDiff(folderId: String, lastSync: Int64) async throws -> FolderContents {
        struct Body: Encodable {
            let folderId: String
            let workspaceId: String
            let lastFolderSync: Int64
            let orderBy = "last_updated_at"
        }
        return try await client.send(
            .post,
            "api/docs/workspace/document/folder/diff",
            body: Body(folderId: folderId, workspaceId: workspaceId, lastFolderSync: lastSync)
        )
    }

    public func sendDocuments(_ documents: [WrDocument]) async throws {
        guard !documents.isEmpty else { return }
        struct Body: Encodable {
            let documents: [WrDocument]
            let workspaceId: String
        }
        let stripped = documents.map { document -> WrDocument in
            var document = document
            document.workspaceId = workspaceId
            return document
        }
        try await client.perform(.post, "api/docs/workspace/document", body: Body(documents: stripped, workspaceId: workspaceId))
    }

    public func sendFolders(_ folders: [Folder]) async throws {
        guard !folders.isEmpty else { return }
        struct Body: Encodable {
            let folders: [FolderApiBody]
            let workspaceId: String
        }
        try await client.perform(.post, "api/docs/workspace/folder", body: Body(folders: folders.map(\.api), workspaceId: workspaceId))
    }

    public func document(id: String) async throws -> WrDocument {
        try await client.get("api/docs/workspace/\(workspaceId)/document/\(id)")
    }

    public struct StepChange: Encodable, Equatable {
        public let storyStep: StoryStep
        public let position: Double
    }

    public struct StepSyncResponse: Decodable {
        public let serverTimestamp: Int64
        public let updatedSteps: [StoryStep]
        public let deletedIds: [String]

        enum CodingKeys: String, CodingKey { case serverTimestamp, updatedSteps, deletedIds }

        public init(from decoder: Decoder) throws {
            let container = try decoder.container(keyedBy: CodingKeys.self)
            serverTimestamp = try container.decode(Int64.self, forKey: .serverTimestamp)
            updatedSteps = try container.decodeIfPresent([StoryStep].self, forKey: .updatedSteps) ?? []
            deletedIds = try container.decodeIfPresent([String].self, forKey: .deletedIds) ?? []
        }
    }

    /// Sends the steps changed and deleted while editing, like `StoryStepSyncApi`.
    public func syncSteps(
        documentId: String,
        lastSyncTimestamp: Int64,
        requestTimestamp: Int64,
        changes: [StepChange],
        deletions: [String]
    ) async throws -> StepSyncResponse {
        struct Body: Encodable {
            let documentId: String
            let workspaceId: String
            let lastSyncTimestamp: Int64
            let requestTimestamp: Int64
            let changes: [StepChange]
            let deletions: [String]
        }
        return try await client.send(
            .post,
            "api/docs/workspace/\(workspaceId)/document/\(documentId)/steps/sync",
            body: Body(
                documentId: documentId,
                workspaceId: workspaceId,
                lastSyncTimestamp: lastSyncTimestamp,
                requestTimestamp: requestTimestamp,
                changes: changes,
                deletions: deletions
            )
        )
    }
}

// MARK: - Synced repository

/// Documents that also live on the backend and can be synced.
public protocol DocumentSyncing: AnyObject {
    /// Brings the folder in sync with the backend, like `FolderSync` of the Compose app.
    func syncFolder(_ folderId: String) async throws
    /// Merges the backend copy into the local one and returns the result when it changed, like
    /// `DocumentLoadUseCase.fetchAndMergeFromBackend`.
    func fetchAndMerge(documentId: String) async throws -> WrDocument?
    /// Sends the steps changed while editing and records the sync time on the document.
    func pushSteps(
        document: WrDocument,
        changes: [StoryStep],
        deletions: [String],
        lastSyncTimestamp: Int64
    ) async throws -> Int64
}

/// The open space: everything is read from and written to the local cache first, then synced
/// with the backend.
public final class SyncedDocumentsRepository: DocumentsRepository, DocumentSyncing, StepStore, PresentationsRepository {
    public let local: LocalDocumentsRepository
    private let remote: RemoteDocumentsRepository
    private let api: SyncAPI
    private var lastFolderSync: [String: Date] = [:]

    public init(local: LocalDocumentsRepository, remote: RemoteDocumentsRepository, api: SyncAPI) {
        self.local = local
        self.remote = remote
        self.api = api
    }

    public var workspaceId: String { remote.workspaceId }

    // MARK: - Presentations: kept in the local cache, never synced.

    public func presentations(ofDocument documentId: String) async throws -> [Presentation] {
        try await local.presentations(ofDocument: documentId)
    }

    public func presentation(id: String) async throws -> Presentation? {
        try await local.presentation(id: id)
    }

    public func savePresentation(_ presentation: Presentation) async throws {
        try await local.savePresentation(presentation)
    }

    public func deletePresentation(id: String) async throws {
        try await local.deletePresentation(id: id)
    }

    public func folderContents(folderId: String) async throws -> FolderContents {
        try await local.folderContents(folderId: folderId)
    }

    public func favorites() async throws -> FolderContents {
        try await local.favorites()
    }

    public func document(id: String) async throws -> WrDocument {
        if let document = try? await local.document(id: id) {
            return document
        }
        // Not synced to this device yet.
        let document = try await remote.document(id: id)
        var synced = document
        synced.lastSyncedAt = document.lastUpdatedAt
        try local.store(synced)
        return synced
    }

    /// The backend searches every document of the workspace; offline, the local cache is used.
    public func search(query: String) async throws -> [WrDocument] {
        do {
            return try await remote.search(query: query)
        } catch {
            return try await local.search(query: query)
        }
    }

    /// Folders are created here with their final id and sent right away; when that fails the next
    /// sync of their parent sends them.
    public func createFolder(title: String, parentId: String) async throws -> Folder {
        var folder = try await local.createFolder(title: title, parentId: parentId)
        if (try? await api.sendFolders([folder])) != nil {
            folder.lastSyncedAt = Date()
            try local.store(folder)
        }
        return folder
    }

    public func createDocument(title: String, parentId: String) async throws -> WrDocument {
        var document = try await local.createDocument(title: title, parentId: parentId)
        document.workspaceId = workspaceId
        try local.store(document)
        try? await sendAndMarkSynced([document])
        return try local.storedDocument(id: document.id) ?? document
    }

    public func moveDocument(id: String, toFolder folderId: String) async throws {
        try await local.moveDocument(id: id, toFolder: folderId)
        // When the backend can't be reached, the next sync of the folder sends the new parent.
        if (try? await remote.moveDocument(id: id, toFolder: folderId)) != nil,
           var document = try local.storedDocument(id: id) {
            document.lastSyncedAt = document.lastUpdatedAt
            try local.store(document)
        }
    }

    public func moveFolder(id: String, toFolder folderId: String) async throws {
        try await local.moveFolder(id: id, toFolder: folderId)
        let moved = try local.storedFolder(id: id)
        do {
            try await remote.moveFolder(id: id, toFolder: folderId)
            if let moved { try markSynced(moved) }
        } catch MoveError.folderIntoItself {
            throw MoveError.folderIntoItself
        } catch {
            // Sent with the next sync.
        }
    }

    public func folder(id: String) async throws -> Folder? {
        try await local.folder(id: id)
    }

    /// Stored here first; when the backend can't be reached, the next sync of the parent folder
    /// sends it (it's newer than its last sync).
    @discardableResult
    public func updateFolder(_ folder: Folder) async throws -> Folder {
        let updated = try await local.updateFolder(folder)
        if (try? await api.sendFolders([updated])) != nil {
            try markSynced(updated)
        }
        return try await local.folder(id: updated.id) ?? updated
    }

    /// Marks the folder as synced when what's stored is still the version that was sent; a
    /// change made while it was being sent stays outdated, to be sent with the next sync.
    private func markSynced(_ sent: Folder) throws {
        guard var stored = try local.storedFolder(id: sent.id) else { return }
        let storedTime = stored.lastUpdatedAt?.millis ?? 0
        guard storedTime <= (sent.lastUpdatedAt?.millis ?? 0) else { return }
        stored.lastSyncedAt = max(Date(), Date(millis: storedTime))
        try local.store(stored)
    }

    /// Saves the editor's copy. The sync time and deleted flag are the ones stored, since the
    /// sync may have updated them while the document was being edited.
    public func save(_ document: WrDocument) async throws {
        var document = document
        if let stored = try local.storedDocument(id: document.id) {
            document.lastSyncedAt = max(stored.lastSyncedAt ?? 0, document.lastSyncedAt ?? 0)
            document.deleted = stored.deleted
        }
        document.workspaceId = workspaceId
        try local.store(document)
    }

    /// Hidden right away and deleted on the backend; when the backend can't be reached the
    /// deletion is sent with the next sync of the folder. Documents deleted here are never
    /// brought back by a sync.
    public func deleteDocument(id: String) async throws {
        try local.softDeleteDocument(id: id)
        try? await sendDeletion(id)
    }

    private func sendDeletion(_ id: String) async throws {
        try await remote.deleteDocument(id: id)
        try local.hardDeleteDocument(id: id)
    }

    private func sendFolderDeletion(_ id: String) async throws {
        try await remote.deleteFolder(id: id)
        try local.deleteFolderTree(id: id, soft: false)
    }

    // MARK: - Selection menu

    /// Copies are created here and sent right away; what can't be sent goes with the next sync.
    public func duplicate(ids: [String]) async throws {
        let copies = try local.duplicateReturningCopies(ids: ids)
        if (try? await api.sendFolders(copies.folders)) != nil {
            for var folder in copies.folders {
                folder.lastSyncedAt = Date()
                try local.store(folder)
            }
        }
        try? await sendAndMarkSynced(copies.documents)
    }

    public func setFavorite(ids: [String], favorite: Bool) async throws {
        try await local.setFavorite(ids: ids, favorite: favorite)
        for id in ids where try local.storedDocument(id: id) != nil {
            try? await remote.setFavorite(documentId: id, favorite: favorite)
        }
        // Folders are sent with the next sync of their parent (the favorite is part of them).
    }

    /// Hidden right away, deleted on the backend, and removed here once it's confirmed.
    public func deleteItems(ids: [String]) async throws {
        for id in ids {
            if try local.storedDocument(id: id) != nil {
                try await deleteDocument(id: id)
            } else if try local.storedFolder(id: id) != nil {
                try local.deleteFolderTree(id: id, soft: true)
                try? await sendFolderDeletion(id)
            }
        }
    }

    public func saveEdit(document: WrDocument, changedSteps: [StoryStep], deletedStepIds: [String]) throws {
        var document = document
        if let stored = try local.storedDocument(id: document.id) {
            document.lastSyncedAt = max(stored.lastSyncedAt ?? 0, document.lastSyncedAt ?? 0)
            document.deleted = stored.deleted
        }
        document.workspaceId = workspaceId
        try local.saveEdit(document: document, changedSteps: changedSteps, deletedStepIds: deletedStepIds)
    }

    // MARK: - DocumentSyncing

    public func syncFolder(_ folderId: String) async throws {
        // Opening the same folder again right away doesn't need another sync.
        if let last = lastFolderSync[folderId], Date().timeIntervalSince(last) < 2 { return }

        // Deletions that couldn't be sent before go first, so the backend doesn't send those
        // documents back.
        var deletedNow: Set<String> = []
        for document in try local.storedDocuments(inFolder: folderId) where document.deleted {
            if (try? await sendDeletion(document.id)) != nil {
                deletedNow.insert(document.id)
            }
        }
        var deletedFoldersNow: Set<String> = []
        for folder in try local.storedFolders(inFolder: folderId) where folder.deleted {
            if (try? await sendFolderDeletion(folder.id)) != nil {
                deletedFoldersNow.insert(folder.id)
            }
        }

        // Like the Compose app, the whole folder is asked for (the backend doesn't return a sync
        // time to continue from), which also shows what was deleted or moved elsewhere.
        var remoteContents = try await api.folderDiff(folderId: folderId, lastSync: 0)
        remoteContents.documents.removeAll { deletedNow.contains($0.id) }
        remoteContents.folders.removeAll { deletedFoldersNow.contains($0.id) }

        let storedDocuments = try local.storedDocuments(inFolder: folderId)
        let storedFolders = try local.storedFolders(inFolder: folderId)
        let outdatedDocuments = storedDocuments.filter { !$0.deleted && $0.isOutdated }
        let outdatedFolders = storedFolders.filter { !$0.deleted && $0.isOutdated }

        let documentsToSend = try DocumentConflictHandler.handle(
            local: outdatedDocuments,
            remote: remoteContents.documents,
            store: local
        )
        let foldersToSend = try FolderConflictHandler.handle(
            local: outdatedFolders,
            remote: remoteContents.folders,
            store: local
        )

        // Synced items the backend doesn't have in this folder anymore were deleted or moved
        // on another device.
        let remoteDocumentIds = Set(remoteContents.documents.map(\.id))
        for document in storedDocuments where document.lastSyncedAt != nil && !document.isOutdated && !remoteDocumentIds.contains(document.id) {
            try local.hardDeleteDocument(id: document.id)
        }
        let remoteFolderIds = Set(remoteContents.folders.map(\.id))
        for folder in storedFolders where folder.lastSyncedAt != nil && !remoteFolderIds.contains(folder.id) && !outdatedFolders.contains(folder) {
            try local.hardDeleteFolder(id: folder.id)
        }

        try await sendAndMarkSynced(documentsToSend)
        try await api.sendFolders(foldersToSend)
        for folder in foldersToSend {
            try markSynced(folder)
        }

        // Changes made in other folders that couldn't be sent yet (a move while offline, for
        // one) go with this sync too, so they don't wait for their folder to be opened. After
        // this folder is handled: they're still outdated while its listing is applied, so a
        // stale copy of a moved item in it can't undo the move.
        await pushPending(outside: folderId)

        lastFolderSync[folderId] = Date()
    }

    public func fetchAndMerge(documentId: String) async throws -> WrDocument? {
        let localCopy = try local.storedDocument(id: documentId)
        let remoteCopy = try await api.document(id: documentId)
        guard remoteCopy.id == documentId else { return nil }

        guard var merged = DocumentMerger.merge(local: localCopy, remote: remoteCopy) else { return nil }
        guard localCopy == nil || merged.content != localCopy?.content || merged.title != localCopy?.title else {
            return nil
        }
        if localCopy?.isOutdated != true {
            merged.lastSyncedAt = max(merged.lastUpdatedAt, merged.lastSyncedAt ?? 0)
        }
        try local.store(merged)
        return merged
    }

    public func pushSteps(
        document: WrDocument,
        changes: [StoryStep],
        deletions: [String],
        lastSyncTimestamp: Int64
    ) async throws -> Int64 {
        let requestTimestamp = Date.nowMillis
        let response = try await api.syncSteps(
            documentId: document.id,
            lastSyncTimestamp: lastSyncTimestamp,
            requestTimestamp: requestTimestamp,
            changes: changes.map { SyncAPI.StepChange(storyStep: $0, position: $0.position) },
            deletions: deletions
        )

        // The document is in sync up to the state that was sent.
        if var stored = try local.storedDocument(id: document.id), stored.lastUpdatedAt <= document.lastUpdatedAt {
            stored.lastSyncedAt = max(requestTimestamp, stored.lastUpdatedAt)
            try local.store(stored)
        }
        return response.serverTimestamp
    }

    /// Sends the outdated documents and folders that aren't in `folderId`, resolving each
    /// document against the backend copy like the folder sync does. Failures are left for the
    /// next sync.
    private func pushPending(outside folderId: String) async {
        let documents = (try? local.storedOutdatedDocuments())?.filter { $0.parentId != folderId } ?? []
        let folders = (try? local.storedOutdatedFolders())?.filter { $0.parentId != folderId } ?? []

        if !documents.isEmpty {
            var toSend: [WrDocument] = []
            for document in documents {
                let remote = (try? await api.document(id: document.id)).map { [$0] } ?? []
                if let resolved = try? DocumentConflictHandler.handle(
                    local: [document],
                    remote: remote.filter { $0.id == document.id },
                    store: local
                ) {
                    toSend += resolved
                }
            }
            try? await sendAndMarkSynced(toSend)
        }

        if !folders.isEmpty, (try? await api.sendFolders(folders)) != nil {
            for folder in folders {
                try? markSynced(folder)
            }
        }
    }

    private func sendAndMarkSynced(_ documents: [WrDocument]) async throws {
        guard !documents.isEmpty else { return }
        try await api.sendDocuments(documents)
        let now = Date.nowMillis
        for document in documents {
            guard var stored = try local.storedDocument(id: document.id) else { continue }
            // Only mark it when it wasn't edited again while being sent.
            if stored.lastUpdatedAt <= document.lastUpdatedAt {
                stored.lastSyncedAt = max(now, stored.lastUpdatedAt)
                try local.store(stored)
            }
        }
    }
}
