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

/// Settles the tag catalogue changed here and on the backend. Mirrors `DocumentTagConflictHandler`
/// of the Kotlin app: the copy updated last wins, a tag deleted here is never brought back (its
/// tombstone goes up instead), and what came from this device is returned to be sent.
public enum TagConflictHandler {
    public static func handle(
        local: [DocumentTag],
        remote: [DocumentTag],
        store: LocalDocumentsRepository
    ) throws -> [DocumentTag] {
        var toSend: [DocumentTag] = []
        let localById = Dictionary(local.map { ($0.id, $0) }, uniquingKeysWith: { first, _ in first })

        for external in remote {
            let existing = try localById[external.id] ?? store.storedTag(id: external.id)
            guard let existing else {
                var synced = external
                synced.lastSyncedAt = external.lastUpdatedAt
                try store.storeTag(synced)
                continue
            }
            if existing.lastUpdatedAt >= external.lastUpdatedAt {
                if let mine = localById[external.id] { toSend.append(mine) }
            } else {
                var synced = external
                synced.lastSyncedAt = external.lastUpdatedAt
                try store.storeTag(synced)
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

    /// The ids of the documents the user marked as favorites in the workspace. Favorites are
    /// per user and kept apart from the documents on the backend, so a document favorited
    /// elsewhere doesn't come back changed in the folder diff.
    public func favoriteDocumentIds() async throws -> [String] {
        try await client.get("api/docs/workspace/\(workspaceId)/user/favorites")
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

    /// Upserts tags of the workspace catalogue.
    public func sendTags(_ tags: [DocumentTag]) async throws {
        guard !tags.isEmpty else { return }
        struct Body: Encodable {
            let tags: [DocumentTag]
            let workspaceId: String
        }
        let owned = tags.map { tag -> DocumentTag in
            var tag = tag
            tag.workspaceId = workspaceId
            return tag
        }
        try await client.perform(.post, "api/docs/workspace/tag", body: Body(tags: owned, workspaceId: workspaceId))
    }

    public func document(id: String) async throws -> WrDocument {
        try await client.get("api/docs/workspace/\(workspaceId)/document/\(id)")
    }

    /// Tells the backend which steps this device deleted, like `DocumentsApi.deleteStorySteps`.
    /// Sent before any content, so a step the backend still has is deleted there instead of
    /// coming back here.
    public func deleteSteps(_ tombstones: [StepTombstone]) async throws {
        guard !tombstones.isEmpty else { return }
        struct Tombstone: Encodable {
            let stepId: String
            let documentId: String
        }
        struct Body: Encodable {
            let tombstones: [Tombstone]
        }
        try await client.perform(
            .post,
            "api/docs/workspace/\(workspaceId)/steps/delete",
            body: Body(tombstones: tombstones.map { Tombstone(stepId: $0.stepId, documentId: $0.documentId) })
        )
    }

    public struct DeletedStepsResponse: Decodable {
        public let stepIds: [String]
        public let serverTimestamp: Int64
    }

    /// The ids of the steps deleted in the workspace, on any device, after `since` (epoch ms),
    /// with the backend's time before it looked: the next request asks from there, in the
    /// backend's clock.
    public func deletedSteps(since: Int64) async throws -> DeletedStepsResponse {
        try await client.get(
            "api/docs/workspace/\(workspaceId)/steps/deleted",
            query: [URLQueryItem(name: "since", value: String(since))]
        )
    }

    public struct StepChange: Encodable, Equatable {
        public let storyStep: StoryStep
        public let position: Int
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

    public struct LinkCardDescriptionResponse: Decodable {
        public let storyStep: StoryStep?
        public let error: String?
    }

    /// Asks the backend to describe the page of a link card with the AI and to save the
    /// description in the card, like `DocumentsApi.describeLinkCard` of the Compose app. The
    /// backend is updated before this answers, so a sync from `requestTimestamp` on returns the
    /// described card.
    public func describeLinkCard(
        documentId: String,
        step: StoryStep,
        requestTimestamp: Int64
    ) async throws -> StoryStep {
        struct Body: Encodable {
            let storyStep: StoryStep
            let position: Int
            let requestTimestamp: Int64
        }
        let response: LinkCardDescriptionResponse = try await client.send(
            .post,
            "api/docs/workspace/\(workspaceId)/document/\(documentId)/link-card/describe",
            body: Body(storyStep: step, position: step.position, requestTimestamp: requestTimestamp)
        )
        guard let described = response.storyStep else {
            throw APIError.badRequest(response.error ?? "The link could not be described")
        }
        return described
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

// MARK: - Step sync

/// Syncs documents with the backend one step at a time. Mirrors `DocumentSyncService` of the
/// Compose app, so the two apps never disagree about a document: a document is never
/// replaced whole on either side.
///
/// Sending, in this order:
/// 1. `sendDeletedSteps`: the steps deleted here go first, as ids only, for the whole
///    workspace. The backend deletes them and keeps the tombstones for the other devices;
///    once it has them, they are dropped here.
/// 2. `syncDocuments`: for each document changed here, the steps edited since it was last
///    sent (the backend keeps the newer copy of each step), then the metadata of all of them
///    in one request. Only then are they stamped as synced, at the time taken before the
///    send: whatever was edited meanwhile is still newer than that and goes with the next sync.
///
/// Receiving:
/// 3. `receiveDeletedSteps`: the steps deleted on other devices since the last time.
/// 4. `mergeReceived`: the steps changed on other devices, merged step by step; a step
///    deleted here is never written back.
public final class DocumentStepSync {
    /// Steps per request: keeps the payload small and the request reliable.
    static let stepsPerBatch = 50
    /// Ids per deletion request.
    static let tombstonesPerBatch = 500

    private let local: LocalDocumentsRepository
    private let api: SyncAPI

    public init(local: LocalDocumentsRepository, api: SyncAPI) {
        self.local = local
        self.api = api
    }

    /// Sends the deletions pending in the workspace, in batches, forgetting the ones the
    /// backend accepted, and returns their ids: a listing fetched before they went may still
    /// carry the steps. Throws when some could not be sent; they stay pending.
    @discardableResult
    public func sendDeletedSteps() async throws -> Set<String> {
        let tombstones = try local.storedStepTombstones()
        guard !tombstones.isEmpty else { return [] }

        for start in stride(from: 0, to: tombstones.count, by: Self.tombstonesPerBatch) {
            let batch = Array(tombstones[start..<min(start + Self.tombstonesPerBatch, tombstones.count)])
            try await api.deleteSteps(batch)
            try local.deleteStepTombstones(batch.map(\.stepId))
        }
        return Set(tombstones.map(\.stepId))
    }

    /// Sends the documents changed here. For each one its edited steps go first, one request
    /// per batch; then the metadata of all of them (title, folder, icon, tags) goes in one
    /// request, without content; only then are they stamped as synced, at the time taken
    /// before anything was sent, so an edit made meanwhile is still newer and goes next time.
    /// Nothing is stamped while the metadata is not on the backend: a rename or a move would
    /// be lost otherwise, and steps sent again are harmless (the backend keeps the newer copy).
    ///
    /// A document whose steps could not be sent is left out, to be tried again next time.
    /// Throws when the metadata could not be sent.
    ///
    /// - Returns: the documents now in sync.
    @discardableResult
    public func syncDocuments(_ documents: [WrDocument]) async throws -> [WrDocument] {
        guard !documents.isEmpty else { return [] }

        // The time of the send. Every step edited after it is newer than the stamp below.
        let sentAt = Date.nowMillis
        var toSend: [WrDocument] = []
        var sentSteps: [String: [StoryStep]] = [:]
        for document in documents {
            guard let steps = try? await sendSteps(of: document, requestTimestamp: sentAt) else { continue }
            toSend.append(document)
            sentSteps[document.id] = steps
        }
        guard !toSend.isEmpty else { return [] }

        try await api.sendDocuments(toSend.map(Self.withoutContent))

        for document in toSend {
            try stampSent(document, steps: sentSteps[document.id] ?? [], at: sentAt)
        }
        return toSend
    }

    /// The document's metadata alone: its steps travel through the step sync.
    private static func withoutContent(_ document: WrDocument) -> WrDocument {
        var document = document
        document.content = []
        return document
    }

    /// Sends the steps of the document edited since it was last sent, in batches, with
    /// `requestTimestamp` as their edit time on the backend. Returns the steps sent (none when
    /// the document was already in sync); throws when the backend could not be reached.
    private func sendSteps(of document: WrDocument, requestTimestamp: Int64) async throws -> [StoryStep] {
        let lastSync = document.lastSyncedAt ?? 0
        let outdated = Self.outdatedSteps(of: document, lastSync: lastSync)
        guard !outdated.isEmpty else { return [] }

        var currentSync = lastSync

        for (index, start) in stride(from: 0, to: outdated.count, by: Self.stepsPerBatch).enumerated() {
            let batch = Array(outdated[start..<min(start + Self.stepsPerBatch, outdated.count)])
            let response = try await api.syncSteps(
                documentId: document.id,
                lastSyncTimestamp: currentSync,
                requestTimestamp: requestTimestamp,
                changes: batch.map { SyncAPI.StepChange(storyStep: $0, position: $0.position) },
                deletions: []
            )
            // What the backend had that this device didn't; once, the first answer has it all.
            if index == 0 { try applyServerUpdates(response, to: document) }
            currentSync = response.serverTimestamp
        }

        return outdated
    }

    /// What `receiveDeletedSteps` brought: the ids, and where the next request starts from.
    public struct ReceivedDeletions {
        public let stepIds: Set<String>
        /// The backend's time before it looked, in its own clock.
        public let serverTimestamp: Int64
    }

    /// Removes the steps deleted on other devices after `since` and returns their ids, so a
    /// listing fetched before the deletions doesn't bring them back, with the backend's time
    /// to ask from next (its clock, not this device's: a deletion made while the clocks
    /// disagree would be skipped for good otherwise). No tombstone is kept for them: the
    /// backend has one.
    @discardableResult
    public func receiveDeletedSteps(since: Int64) async throws -> ReceivedDeletions {
        let response = try await api.deletedSteps(since: since)
        if !response.stepIds.isEmpty { try local.deleteSteps(ids: response.stepIds) }
        return ReceivedDeletions(stepIds: Set(response.stepIds), serverTimestamp: response.serverTimestamp)
    }

    /// Merges a document received from the backend into the local copy, step by step: a step
    /// only there is added, a step newer there replaces the local one, the rest stays. The
    /// steps deleted here and not yet sent are left out, like the ones in `excluding` (deleted
    /// elsewhere, just learned of). A document deleted here is not brought back.
    ///
    /// - Returns: the document as stored when something was written, nil otherwise.
    @discardableResult
    public func mergeReceived(_ received: WrDocument, excluding: Set<String> = []) throws -> WrDocument? {
        let local = try local.storedDocument(id: received.id)
        if local?.deleted == true { return nil }

        let tombstoned = try self.local.tombstonedIds(among: received.content.map(\.id))
        var incoming = received
        incoming.content.removeAll { tombstoned.contains($0.id) || excluding.contains($0.id) }

        guard let local else {
            try self.local.store(incoming)
            return incoming
        }

        guard let merged = DocumentMerger.merge(local: local, remote: incoming) else { return nil }
        let localById = Dictionary(local.content.map { ($0.id, $0) }, uniquingKeysWith: { first, _ in first })
        // The merger hands back the local copy when it keeps it, so the rest came from the backend.
        let stepsToWrite = merged.content.filter { localById[$0.id] != $0 }
        let tagsChanged = merged.tagIds != local.tagIds
        let backendIsNewer = incoming.lastUpdatedAt > local.lastUpdatedAt

        if stepsToWrite.isEmpty, !tagsChanged, !backendIsNewer { return nil }

        var stored = merged
        if local.isOutdated {
            // Still to be sent: it keeps looking outdated, with its own timestamps.
            stored.lastUpdatedAt = local.lastUpdatedAt
            stored.lastSyncedAt = local.lastSyncedAt
        } else {
            // Nothing pending here: in sync with what was just received.
            stored.lastSyncedAt = Date.nowMillis
        }

        try self.local.storeMetadata(stored)
        if !stepsToWrite.isEmpty { try self.local.storeSteps(stepsToWrite, documentId: stored.id) }
        return stored
    }

    /// The steps edited since the document was last sent, by position; everything when it was
    /// never sent.
    static func outdatedSteps(of document: WrDocument, lastSync: Int64) -> [StoryStep] {
        document.content
            .filter { lastSync == 0 || ($0.lastUpdatedAt ?? 0) > lastSync }
            .sorted { $0.position < $1.position }
    }

    /// What the backend answered with: its deletions, then its steps newer than the local ones.
    private func applyServerUpdates(_ response: SyncAPI.StepSyncResponse, to document: WrDocument) throws {
        if !response.deletedIds.isEmpty { try local.deleteSteps(ids: response.deletedIds) }
        guard !response.updatedSteps.isEmpty else { return }

        let localById = Dictionary(document.content.map { ($0.id, $0) }, uniquingKeysWith: { first, _ in first })
        let newer = response.updatedSteps.filter { serverStep in
            guard let localStep = localById[serverStep.id] else { return true }
            return (serverStep.lastUpdatedAt ?? 0) > (localStep.lastUpdatedAt ?? .max)
        }
        if !newer.isEmpty { try local.storeSteps(newer, documentId: document.id) }
    }

    /// The document is in sync as of `sentAt`: that goes on the document, and on the steps
    /// sent, so they are no longer newer than it. A step edited since keeps its newer time.
    /// The copy sent is covered even when its change time is a millisecond ahead (a move
    /// right after a sync is stamped that way); the stamp never goes back in time.
    private func stampSent(_ document: WrDocument, steps sent: [StoryStep], at sentAt: Int64) throws {
        guard var current = try local.storedDocument(id: document.id) else { return }
        let coveredUntil = current.lastUpdatedAt <= document.lastUpdatedAt ? max(sentAt, current.lastUpdatedAt) : sentAt
        current.lastSyncedAt = max(current.lastSyncedAt ?? coveredUntil, coveredUntil)
        try local.storeMetadata(current)

        let sentTimes = Dictionary(sent.map { ($0.id, $0.lastUpdatedAt) }, uniquingKeysWith: { first, _ in first })
        let stamped = current.content
            .filter { step in sentTimes[step.id].map { $0 == step.lastUpdatedAt } ?? false }
            .map { step -> StoryStep in
                var step = step
                step.lastUpdatedAt = sentAt
                return step
            }
        if !stamped.isEmpty { try local.storeSteps(stamped, documentId: document.id) }
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

/// Documents whose link cards the backend AI can describe.
public protocol LinkCardDescribing: AnyObject {
    /// Has the backend describe the page of `step`, a link card of the document, and save the
    /// description in the card; then syncs the document from the time of the request on and
    /// returns the card as the backend has it, with the description as its text.
    func describeLinkCard(documentId: String, step: StoryStep) async throws -> StoryStep
}

/// The open space: everything is read from and written to the local cache first, then synced
/// with the backend.
public final class SyncedDocumentsRepository: DocumentsRepository, DocumentSyncing, StepStore, PresentationsStore, LinkCardDescribing {
    public let local: LocalDocumentsRepository
    private let remote: RemoteDocumentsRepository
    private let api: SyncAPI
    private var lastFolderSync: [String: Date] = [:]

    private let stepSync: DocumentStepSync

    public init(local: LocalDocumentsRepository, remote: RemoteDocumentsRepository, api: SyncAPI) {
        self.local = local
        self.remote = remote
        self.api = api
        self.stepSync = DocumentStepSync(local: local, api: api)
    }

    public var workspaceId: String { remote.workspaceId }

    // MARK: - Tags: the catalogue lives on this device and goes up with the folder sync.

    public func tags() async throws -> [DocumentTag] {
        try local.storedTags()
    }

    public func documents(withTag tagId: String) async throws -> [WrDocument] {
        try await local.documents(withTag: tagId)
    }

    public func allContents() async throws -> FolderContents {
        try await local.allContents()
    }

    /// Stores the tag and sends it right away; when that fails the next folder sync sends it.
    public func saveTag(_ tag: DocumentTag) async throws {
        var tag = tag
        tag.workspaceId = workspaceId
        try local.storeTag(tag)
        do {
            try await api.sendTags([tag])
            try markSynced(tag)
        } catch {
            // The tag stays outdated and goes with the next sync.
        }
    }

    private func markSynced(_ sent: DocumentTag) throws {
        guard var stored = try local.storedTag(id: sent.id), stored.lastUpdatedAt <= sent.lastUpdatedAt else { return }
        stored.lastSyncedAt = max(Date.nowMillis, stored.lastUpdatedAt)
        try local.storeTag(stored)
    }

    // MARK: - Presentations on this device: the ones of the local AI and copies of the cloud ones, never synced.

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

    /// The backend finds the documents and its presentations in one request; the presentations of
    /// the local AI, never synced, come from this device. Offline, everything comes from here.
    public func searchAll(query: String) async throws -> SearchResults {
        do {
            var results = try await remote.searchAll(query: query)
            let onDevice = (try? await local.searchPresentations(query: query)) ?? []
            let known = Set(results.presentations.map(\.id))
            results.presentations += onDevice.filter { !known.contains($0.id) }
            return results
        } catch {
            return try await local.searchAll(query: query)
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

    /// Stored here and sent with the next sync of the document, which resolves it against the
    /// backend copy, like the Compose app: sending the whole document right away could put an
    /// older copy of its content over a newer one.
    public func updateDocumentIcon(id: String, icon: IconInfo?) async throws {
        try await local.updateDocumentIcon(id: id, icon: icon)
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
            // Chosen in the sidebar, maybe while the editor had the document open.
            document.icon = stored.icon
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
            document.icon = stored.icon
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

        // The folder as it is before anything is sent: what is outdated now was not deleted
        // elsewhere, it just hasn't reached the backend yet.
        let storedDocuments = try local.storedDocuments(inFolder: folderId)
        let storedFolders = try local.storedFolders(inFolder: folderId)
        let outdatedDocumentIds = Set(storedDocuments.filter { !$0.deleted && $0.isOutdated }.map(\.id))
        let outdatedFolders = storedFolders.filter { !$0.deleted && $0.isOutdated }

        // Documents travel one step at a time, like `FolderSync` of the Compose app: first the
        // steps deleted here, then, for every document of the workspace changed here since it
        // was sent, its edited steps and then its metadata (title, folder, icon...), stamped
        // as synced only once both are on the backend.
        let deletedHere = try await stepSync.sendDeletedSteps()
        try await stepSync.syncDocuments(try local.storedOutdatedDocuments())

        let foldersToSend = try FolderConflictHandler.handle(
            local: outdatedFolders,
            remote: remoteContents.folders,
            store: local
        )

        // Synced items the backend doesn't have in this folder anymore were deleted or moved
        // on another device.
        let remoteDocumentIds = Set(remoteContents.documents.map(\.id))
        for document in storedDocuments
        where document.lastSyncedAt != nil && !outdatedDocumentIds.contains(document.id) && !remoteDocumentIds.contains(document.id) {
            try local.hardDeleteDocument(id: document.id)
        }
        let remoteFolderIds = Set(remoteContents.folders.map(\.id))
        for folder in storedFolders where folder.lastSyncedAt != nil && !remoteFolderIds.contains(folder.id) && !outdatedFolders.contains(folder) {
            try local.hardDeleteFolder(id: folder.id)
        }

        try await api.sendFolders(foldersToSend)
        for folder in foldersToSend {
            try markSynced(folder)
        }

        // The whole tag catalogue of the workspace comes with every folder.
        let tagsToSend = try TagConflictHandler.handle(
            local: try local.storedOutdatedTags(),
            remote: remoteContents.tags,
            store: local
        )
        try await api.sendTags(tagsToSend)
        for tag in tagsToSend {
            try markSynced(tag)
        }

        // Now what changed elsewhere: the steps deleted since the last time go first, so
        // nothing received recreates them; then the documents, merged step by step. The
        // listing predates the deletions sent above, so it may still carry those steps too.
        let received = try await stepSync.receiveDeletedSteps(since: try local.stepDeletionsSyncedAt() ?? 0)
        for document in remoteContents.documents {
            try stepSync.mergeReceived(document, excluding: received.stepIds.union(deletedHere))
        }
        // Workspace-wide, in the backend's clock.
        try local.setStepDeletionsSyncedAt(received.serverTimestamp)

        // The favorites of the documents don't travel with them: they come apart, and the
        // backend wins. A favorite set here that could not be sent is undone. When the backend
        // can't be asked they stay as they are; a cancelled sync stops here like anywhere else.
        let favoriteIds: [String]?
        do {
            favoriteIds = try await api.favoriteDocumentIds()
        } catch is CancellationError {
            throw CancellationError()
        } catch {
            favoriteIds = nil
        }
        if let favoriteIds {
            try local.applyFavorites(Set(favoriteIds))
        }

        // Folders changed in other folders that couldn't be sent yet (a move while offline, for
        // one) go with this sync too, so they don't wait for their folder to be opened.
        await pushPendingFolders(outside: folderId)

        lastFolderSync[folderId] = Date()
    }

    /// Opens the document: the backend copy is merged into the local one step by step and the
    /// result returned when something changed. The deletions pending here go first, so the
    /// copy comes without the steps deleted on this device; the ones the backend could not
    /// take are left out of it by the merge, which skips the tombstoned steps.
    public func fetchAndMerge(documentId: String) async throws -> WrDocument? {
        _ = try? await stepSync.sendDeletedSteps()
        let remoteCopy = try await api.document(id: documentId)
        guard remoteCopy.id == documentId else { return nil }
        return try stepSync.mergeReceived(remoteCopy)
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

    public func describeLinkCard(documentId: String, step: StoryStep) async throws -> StoryStep {
        let requestTimestamp = Date.nowMillis
        let described = try await api.describeLinkCard(documentId: documentId, step: step, requestTimestamp: requestTimestamp)

        // The backend has the description now: a sync from just before the request returns it.
        let synced = try await api.syncSteps(
            documentId: documentId,
            lastSyncTimestamp: requestTimestamp - 1,
            requestTimestamp: Date.nowMillis,
            changes: [],
            deletions: []
        )
        return synced.updatedSteps.first { $0.id == step.id && !($0.text ?? "").isEmpty } ?? described
    }

    /// Sends the outdated folders that aren't in `folderId`. Failures are left for the next sync.
    private func pushPendingFolders(outside folderId: String) async {
        let folders = (try? local.storedOutdatedFolders())?.filter { $0.parentId != folderId } ?? []
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
