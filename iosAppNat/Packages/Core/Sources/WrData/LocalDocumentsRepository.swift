import Foundation
import WrModels
import WrNetwork

/// Saves an edit of a document step by step, like `OnUpdateDocumentTracker` of the Compose
/// app: only the steps that changed are written.
public protocol StepStore: AnyObject {
    func saveEdit(document: WrDocument, changedSteps: [StoryStep], deletedStepIds: [String]) throws
}

/// Documents and folders in a SQLite database, with a schema that mirrors the Compose app
/// (`documentEntity`, `storyStepEntity`, `folderEntity`). It's the whole storage of the private
/// space, and the local cache of each workspace of the open space.
///
/// Steps are rows of their own so an edit writes only what changed; the content of a step is
/// kept as JSON, which carries nested steps, tags, spans and links as they are.
public final class LocalDocumentsRepository: DocumentsRepository, StepStore, PresentationsStore {
    private let directory: URL
    private let workspaceId: String
    private let seedsWelcome: Bool
    private let db: SQLiteDatabase
    private let encoder = JSONEncoder()
    private let decoder = JSONDecoder()

    public convenience init(
        directory: URL = LocalDocumentsRepository.defaultDirectory,
        workspaceId: String = Workspace.localId,
        seedsWelcome: Bool = true,
        composeDatabase: URL? = nil
    ) {
        do {
            try self.init(
                opening: directory,
                workspaceId: workspaceId,
                seedsWelcome: seedsWelcome,
                importsLegacyJson: true,
                journalMode: .wal,
                composeDatabase: composeDatabase
            )
        } catch {
            fatalError("Could not open the documents database: \(error)")
        }
    }

    /// The private space in a folder the user picked (the Mac app). The folder is left as it
    /// is: JSON files already there are not imported nor moved, and the database uses a single
    /// file so folders synced by iCloud Drive or Dropbox stay consistent. Throws when the folder
    /// can't be written, so a stale choice can fall back to the default location.
    public static func inUserFolder(_ directory: URL, composeDatabase: URL? = nil) throws -> LocalDocumentsRepository {
        try LocalDocumentsRepository(
            opening: directory,
            workspaceId: Workspace.localId,
            seedsWelcome: true,
            importsLegacyJson: false,
            journalMode: .delete,
            composeDatabase: composeDatabase
        )
    }

    /// The database of the Compose desktop app, whose offline space the private space of the
    /// Mac app takes over. Nil on iOS.
    public static var composeDatabaseLocation: URL? {
        #if os(macOS)
        ComposeDatabase.defaultLocation
        #else
        nil
        #endif
    }

    init(
        opening directory: URL,
        workspaceId: String,
        seedsWelcome: Bool,
        importsLegacyJson: Bool,
        journalMode: SQLiteDatabase.JournalMode,
        composeDatabase: URL? = nil
    ) throws {
        self.directory = directory
        self.workspaceId = workspaceId
        self.seedsWelcome = seedsWelcome
        db = try SQLiteDatabase(url: directory.appending(path: "writeopia.sqlite"), journalMode: journalMode)
        try createSchema()
        try migratePositionsIfNeeded()
        if importsLegacyJson {
            try importJsonFilesIfNeeded()
        }
        if let composeDatabase {
            // An unreadable database is tried again on the next launch; the app opens anyway.
            try? importComposeDatabaseIfNeeded(from: composeDatabase)
        }
    }

    /// Where the documents are kept.
    public var location: URL { directory }

    public static var defaultDirectory: URL {
        URL.applicationSupportDirectory.appending(path: "Writeopia/PrivateSpace", directoryHint: .isDirectory)
    }

    /// Local cache of a workspace of the open space.
    public static func cache(forWorkspace workspaceId: String) -> LocalDocumentsRepository {
        LocalDocumentsRepository(
            directory: URL.applicationSupportDirectory.appending(path: "Writeopia/Workspaces/\(workspaceId)", directoryHint: .isDirectory),
            workspaceId: workspaceId,
            seedsWelcome: false
        )
    }

    // MARK: - DocumentsRepository

    public func folderContents(folderId: String) async throws -> FolderContents {
        try seedIfNeeded()

        let folders = try db.query(
            "\(Self.folderColumns) FROM folder WHERE parent_id = ? AND deleted = 0",
            [.text(folderId)],
            row: folder
        ).map { folder -> Folder in
            var folder = folder
            folder.itemCount = (try? itemCount(of: folder.id)) ?? 0
            return folder
        }
        .sorted { $0.title.localizedCaseInsensitiveCompare($1.title) == .orderedAscending }

        let documents = try db.query(
            "\(Self.documentColumns) FROM document WHERE parent_document_id = ? AND deleted = 0 ORDER BY last_updated_at DESC",
            [.text(folderId)],
            row: documentRow
        ).map(withContent)

        return FolderContents(folders: folders, documents: documents)
    }

    public func favorites() async throws -> FolderContents {
        try seedIfNeeded()

        let folders = try db.query(
            "\(Self.folderColumns) FROM folder WHERE favorite = 1 AND deleted = 0",
            row: folder
        ).map { folder -> Folder in
            var folder = folder
            folder.itemCount = (try? itemCount(of: folder.id)) ?? 0
            return folder
        }
        .sorted { $0.title.localizedCaseInsensitiveCompare($1.title) == .orderedAscending }

        let documents = try db.query(
            "\(Self.documentColumns) FROM document WHERE favorite = 1 AND deleted = 0 ORDER BY last_updated_at DESC",
            row: documentRow
        ).map(withContent)

        return FolderContents(folders: folders, documents: documents)
    }

    public func document(id: String) async throws -> WrDocument {
        try seedIfNeeded()
        guard let document = try storedDocument(id: id), !document.deleted else {
            throw APIError.notFound
        }
        return document
    }

    public func search(query: String) async throws -> [WrDocument] {
        let trimmed = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return [] }
        try seedIfNeeded()

        let pattern = "%\(trimmed)%"
        let byTitle = try db.query(
            "SELECT id FROM document WHERE deleted = 0 AND title LIKE ?",
            [.text(pattern)],
            row: { $0.text(0) }
        )
        // The JSON also matches keys, so the steps found are checked again below.
        let byContent = try db.query(
            "SELECT DISTINCT document_id FROM story_step WHERE content LIKE ?",
            [.text(pattern)],
            row: { $0.text(0) }
        )

        return try Set(byTitle + byContent)
            .compactMap(storedDocument)
            .filter { !$0.deleted }
            .filter { document in
                document.title.localizedCaseInsensitiveContains(trimmed) ||
                    document.content.contains { step in
                        step.type.number != StoryType.drawing.number &&
                            step.text?.localizedCaseInsensitiveContains(trimmed) == true
                    }
            }
            .sorted { $0.lastUpdatedAt > $1.lastUpdatedAt }
    }

    public func createFolder(title: String, parentId: String) async throws -> Folder {
        try seedIfNeeded()
        let folder = Folder(id: UUID().uuidString, parentId: parentId, title: title, workspaceId: workspaceId)
        try store(folder)
        return folder
    }

    public func createDocument(title: String, parentId: String) async throws -> WrDocument {
        try seedIfNeeded()
        let document = newDocument(title: title, parentId: parentId, workspaceId: workspaceId)
        try store(document)
        return document
    }

    public func moveDocument(id: String, toFolder folderId: String) async throws {
        guard let stored = try storedDocument(id: id) else { throw APIError.notFound }
        // Always later than the last sync, so a move right after a sync is still sent.
        let time = max(Date.nowMillis, (stored.lastSyncedAt ?? 0) + 1)
        try db.run(
            "UPDATE document SET parent_document_id = ?, last_updated_at = ? WHERE id = ?",
            [.text(folderId), .integer(time), .text(id)]
        )
    }

    public func moveFolder(id: String, toFolder folderId: String) async throws {
        guard let stored = try storedFolder(id: id) else { throw APIError.notFound }

        // Walk up from the target: reaching the moved folder means the target is inside it.
        var ancestor: String? = folderId
        while let current = ancestor, current != Folder.rootId {
            if current == id { throw MoveError.folderIntoItself }
            ancestor = try storedFolder(id: current)?.parentId
        }

        try db.run(
            "UPDATE folder SET parent_id = ?, last_updated_at = ? WHERE id = ?",
            [.text(folderId), .integer(Self.changeTime(after: stored).millis), .text(id)]
        )
    }

    /// When a change of `folder` happens now. Always later than its last sync (times are stored
    /// in milliseconds), so a change made right after a sync still counts as not sent.
    static func changeTime(after folder: Folder) -> Date {
        guard let lastSyncedAt = folder.lastSyncedAt else { return Date() }
        return max(Date(), Date(millis: lastSyncedAt.millis + 1))
    }

    public func save(_ document: WrDocument) async throws {
        try store(keepingStoredIcon(document))
    }

    /// The editor's copy with the icon that is stored: the icon is chosen in the sidebar, maybe
    /// while the document is open, and saving the editor's older copy must not undo it.
    func keepingStoredIcon(_ document: WrDocument) throws -> WrDocument {
        guard let stored = try storedDocument(id: document.id) else { return document }
        var document = document
        document.icon = stored.icon
        return document
    }

    public func updateDocumentIcon(id: String, icon: IconInfo?) async throws {
        guard let stored = try storedDocument(id: id), !stored.deleted else { throw APIError.notFound }
        // Newer than its last sync, so the next sync sends it, like the Compose app.
        let changedAt = max(Date.nowMillis, (stored.lastSyncedAt ?? 0) + 1, stored.lastUpdatedAt + 1)
        try db.run(
            "UPDATE document SET icon = ?, icon_tint = ?, last_updated_at = ? WHERE id = ?",
            [.text(icon?.label), .integer(icon?.tint.map(Int64.init)), .integer(changedAt), .text(id)]
        )
        NotificationCenter.default.post(
            name: .documentIconChanged,
            object: id,
            userInfo: icon.map { ["icon": $0] } ?? [:]
        )
    }

    /// The private space has nothing to sync, so the document is removed for good.
    public func deleteDocument(id: String) async throws {
        try hardDeleteDocument(id: id)
    }

    // MARK: - Edition menu

    public func folder(id: String) async throws -> Folder? {
        guard var folder = try storedFolder(id: id), !folder.deleted else { return nil }
        folder.itemCount = (try? itemCount(of: folder.id)) ?? 0
        return folder
    }

    /// Only the title and the icon are taken from `folder`; the rest is what's stored, so an edit
    /// doesn't undo a move or a sync that happened meanwhile.
    @discardableResult
    public func updateFolder(_ folder: Folder) async throws -> Folder {
        guard var stored = try storedFolder(id: folder.id), !stored.deleted else { throw APIError.notFound }
        stored.title = folder.title
        stored.icon = folder.icon
        stored.lastUpdatedAt = Self.changeTime(after: stored)
        try store(stored)
        stored.itemCount = (try? itemCount(of: stored.id)) ?? 0
        return stored
    }

    // MARK: - Selection menu

    public func duplicate(ids: [String]) async throws {
        try duplicateReturningCopies(ids: ids)
    }

    /// Copies the documents and folders with new ids (steps too), keeping their titles like the
    /// Compose app. Returns the new documents and folders, for the sync to send.
    @discardableResult
    public func duplicateReturningCopies(ids: [String]) throws -> (documents: [WrDocument], folders: [Folder]) {
        var documents: [WrDocument] = []
        var folders: [Folder] = []

        func copyDocument(_ document: WrDocument, into parentId: String?) throws {
            let now = Date.nowMillis
            let copy = WrDocument(
                id: UUID().uuidString,
                title: document.title,
                workspaceId: workspaceId,
                content: document.content.map { step in
                    StoryStep(
                        id: UUID().uuidString, type: step.type, text: step.text, checked: step.checked, url: step.url,
                        path: step.path, steps: step.steps, tags: step.tags, spans: step.spans, position: step.position,
                        documentLink: step.documentLink, parentId: step.parentId, decoration: step.decoration, lastUpdatedAt: now
                    )
                },
                createdAt: now,
                lastUpdatedAt: now,
                isFavorite: document.isFavorite,
                parentId: parentId ?? document.parentId,
                icon: document.icon
            )
            try store(copy)
            documents.append(copy)
        }

        func copyFolder(_ folder: Folder, into parentId: String) throws {
            let copy = Folder(
                id: UUID().uuidString,
                parentId: parentId,
                title: folder.title,
                workspaceId: workspaceId,
                favorite: folder.favorite,
                icon: folder.icon
            )
            try store(copy)
            folders.append(copy)
            for document in try storedDocuments(inFolder: folder.id) where !document.deleted {
                try copyDocument(document, into: copy.id)
            }
            for inner in try storedFolders(inFolder: folder.id) where !inner.deleted {
                try copyFolder(inner, into: copy.id)
            }
        }

        for id in ids {
            if let document = try storedDocument(id: id), !document.deleted {
                try copyDocument(document, into: nil)
            } else if let folder = try storedFolder(id: id), !folder.deleted {
                try copyFolder(folder, into: folder.parentId)
            }
        }
        return (documents, folders)
    }

    public func setFavorite(ids: [String], favorite: Bool) async throws {
        let now = Date.nowMillis
        try db.transaction {
            for id in ids {
                try db.run("UPDATE document SET favorite = ?, last_updated_at = ? WHERE id = ?", [.bool(favorite), .integer(now), .text(id)])
                try db.run("UPDATE folder SET favorite = ?, last_updated_at = ? WHERE id = ?", [.bool(favorite), .integer(now), .text(id)])
            }
        }
    }

    /// Makes the favorite documents those of `ids`, as the backend has them. The timestamps
    /// stay: nothing is to be sent because of it. Folders keep theirs, they travel whole.
    public func applyFavorites(_ ids: Set<String>) throws {
        try db.transaction {
            try db.run("UPDATE document SET favorite = 0 WHERE favorite = 1")
            for id in ids {
                try db.run("UPDATE document SET favorite = 1 WHERE id = ?", [.text(id)])
            }
        }
    }

    /// The private space has nothing to sync, so everything is removed for good.
    public func deleteItems(ids: [String]) async throws {
        for id in ids {
            if try storedDocument(id: id) != nil {
                try hardDeleteDocument(id: id)
            } else if try storedFolder(id: id) != nil {
                try deleteFolderTree(id: id, soft: false)
            }
        }
    }

    /// Deletes a folder and everything inside it. Soft deletes keep the rows, marked, until the
    /// backend confirms, like the Compose app.
    public func deleteFolderTree(id: String, soft: Bool) throws {
        for inner in try storedFolders(inFolder: id) {
            try deleteFolderTree(id: inner.id, soft: soft)
        }
        for document in try storedDocuments(inFolder: id) {
            if soft { try softDeleteDocument(id: document.id) } else { try hardDeleteDocument(id: document.id) }
        }
        if soft {
            try db.run("UPDATE folder SET deleted = 1, last_updated_at = ? WHERE id = ?", [.integer(Date.nowMillis), .text(id)])
        } else {
            try hardDeleteFolder(id: id)
        }
    }

    /// Marks the document as deleted, like the Compose app, until the backend confirms it.
    public func softDeleteDocument(id: String) throws {
        try db.run("UPDATE document SET deleted = 1, last_updated_at = ? WHERE id = ?", [.integer(Date.nowMillis), .text(id)])
    }

    // MARK: - StepStore

    /// The editor's word: a step it deletes leaves a tombstone, so no sync brings it back until
    /// the backend is told; a step it writes again (an undo, say) loses its tombstone.
    public func saveEdit(document: WrDocument, changedSteps: [StoryStep], deletedStepIds: [String]) throws {
        let document = try keepingStoredIcon(document)
        let now = Date.nowMillis
        try db.transaction {
            try writeMetadata(document)
            for step in changedSteps {
                try db.run("DELETE FROM story_step_tombstone WHERE step_id = ?", [.text(step.id)])
                try writeStep(step, documentId: document.id)
            }
            for id in deletedStepIds {
                try db.run("DELETE FROM story_step WHERE id = ? AND document_id = ?", [.text(id), .text(document.id)])
                try db.run(
                    "INSERT OR REPLACE INTO story_step_tombstone (step_id, document_id, workspace_id, deleted_at) VALUES (?, ?, ?, ?)",
                    [.text(id), .text(document.id), .text(document.workspaceId.isEmpty ? workspaceId : document.workspaceId), .integer(now)]
                )
            }
        }
    }

    // MARK: - Step tombstones, used by the sync

    /// The steps deleted here that the backend has not been told about yet, oldest first.
    public func storedStepTombstones() throws -> [StepTombstone] {
        try db.query(
            "SELECT step_id, document_id, workspace_id, deleted_at FROM story_step_tombstone ORDER BY deleted_at"
        ) { row in
            guard let stepId = row.text(0) else { return nil }
            return StepTombstone(
                stepId: stepId,
                documentId: row.text(1) ?? "",
                workspaceId: row.text(2) ?? workspaceId,
                deletedAt: row.integer(3) ?? 0
            )
        }
    }

    /// Forgets the tombstones of `stepIds`, once the backend has them.
    public func deleteStepTombstones(_ stepIds: [String]) throws {
        for id in stepIds {
            try db.run("DELETE FROM story_step_tombstone WHERE step_id = ?", [.text(id)])
        }
    }

    /// Removes steps deleted elsewhere. No tombstone is kept: the backend has one already.
    public func deleteSteps(ids stepIds: [String]) throws {
        try db.transaction {
            for id in stepIds {
                try db.run("DELETE FROM story_step WHERE id = ?", [.text(id)])
            }
        }
    }

    /// The ids among `stepIds` with a tombstone: a sync must not write them.
    func tombstonedIds(among stepIds: [String]) throws -> Set<String> {
        var found: Set<String> = []
        for id in stepIds {
            let rows = try db.query("SELECT step_id FROM story_step_tombstone WHERE step_id = ?", [.text(id)]) { $0.text(0) }
            if !rows.isEmpty { found.insert(id) }
        }
        return found
    }

    /// Where the next request for the deletions made elsewhere starts from: the backend's time
    /// (epoch milliseconds) before it answered the last one; nil before the first time.
    public func stepDeletionsSyncedAt() throws -> Int64? {
        try meta("step_deletions_synced_at").flatMap(Int64.init)
    }

    public func setStepDeletionsSyncedAt(_ millis: Int64) throws {
        try setMeta("step_deletions_synced_at", String(millis))
    }

    /// Where the next request for what changed in the whole workspace starts from (epoch
    /// milliseconds, this device's clock before the last request); nil before the first time.
    public func workspaceContentsSyncedAt() throws -> Int64? {
        try meta("workspace_contents_synced_at").flatMap(Int64.init)
    }

    public func setWorkspaceContentsSyncedAt(_ millis: Int64) throws {
        try setMeta("workspace_contents_synced_at", String(millis))
    }

    /// Where the next request for the deletions and moves made elsewhere (the backend's sync
    /// events) starts from: the backend's time (epoch milliseconds) when it answered the last
    /// one, like `Workspace.lastEventSync` of the Compose app; nil before the first time.
    public func eventsSyncedAt() throws -> Int64? {
        try meta("events_synced_at").flatMap(Int64.init)
    }

    public func setEventsSyncedAt(_ millis: Int64) throws {
        try setMeta("events_synced_at", String(millis))
    }

    // MARK: - Direct access, used by the sync

    /// The document with `id`, including soft deleted ones.
    public func storedDocument(id: String) throws -> WrDocument? {
        try db.query("\(Self.documentColumns) FROM document WHERE id = ?", [.text(id)], row: documentRow)
            .first
            .map(withContent)
    }

    public func storedFolder(id: String) throws -> Folder? {
        try db.query("\(Self.folderColumns) FROM folder WHERE id = ?", [.text(id)], row: folder).first
    }

    /// Documents directly inside `folderId`, soft deleted ones included.
    public func storedDocuments(inFolder folderId: String) throws -> [WrDocument] {
        try db.query(
            "\(Self.documentColumns) FROM document WHERE parent_document_id = ?",
            [.text(folderId)],
            row: documentRow
        ).map(withContent)
    }

    /// Documents changed since they were last sent, wherever they are (edited, created or moved
    /// while the backend couldn't be reached).
    public func storedOutdatedDocuments() throws -> [WrDocument] {
        try db.query(
            "\(Self.documentColumns) FROM document WHERE deleted = 0 AND (last_synced_at IS NULL OR last_updated_at > last_synced_at)",
            row: documentRow
        ).map(withContent)
    }

    /// Folders changed since they were last sent, wherever they are.
    public func storedOutdatedFolders() throws -> [Folder] {
        try db.query(
            "\(Self.folderColumns) FROM folder WHERE deleted = 0 AND (last_synced_at IS NULL OR last_updated_at > last_synced_at)",
            row: folder
        )
    }

    /// Folders directly inside `folderId`, soft deleted ones included.
    public func storedFolders(inFolder folderId: String) throws -> [Folder] {
        try db.query("\(Self.folderColumns) FROM folder WHERE parent_id = ?", [.text(folderId)], row: folder)
    }

    /// Documents deleted here that the backend hasn't confirmed yet, wherever they are.
    public func storedDeletedDocuments() throws -> [WrDocument] {
        try db.query("\(Self.documentColumns) FROM document WHERE deleted = 1", row: documentRow).map(withContent)
    }

    /// Folders deleted here that the backend hasn't confirmed yet, wherever they are.
    public func storedDeletedFolders() throws -> [Folder] {
        try db.query("\(Self.folderColumns) FROM folder WHERE deleted = 1", row: folder)
    }

    /// A move made elsewhere, received from the backend: the document goes to its new folder
    /// as it is, with no change of its own to send, unlike `moveDocument`.
    public func applyReceivedMove(documentId: String, toFolder folderId: String) throws {
        try db.run("UPDATE document SET parent_document_id = ? WHERE id = ?", [.text(folderId), .text(documentId)])
    }

    /// A move made elsewhere, received from the backend, like `applyReceivedMove`.
    public func applyReceivedMove(folderId: String, toFolder parentId: String) throws {
        try db.run("UPDATE folder SET parent_id = ? WHERE id = ?", [.text(parentId), .text(folderId)])
    }

    /// Stores the whole document, replacing its steps. A step deleted here (it has a tombstone
    /// the backend doesn't know of yet) is never written back.
    public func store(_ document: WrDocument) throws {
        let tombstoned = try tombstonedIds(among: document.content.map(\.id))
        try db.transaction {
            try writeMetadata(document)
            try db.run("DELETE FROM story_step WHERE document_id = ?", [.text(document.id)])
            for step in document.content where !tombstoned.contains(step.id) {
                try writeStep(step, documentId: document.id)
            }
        }
    }

    /// Stores the document's metadata and tags; its steps are left as they are.
    public func storeMetadata(_ document: WrDocument) throws {
        try db.transaction {
            try writeMetadata(document)
        }
    }

    /// Writes these steps of the document, the rest untouched; a step deleted here is skipped.
    public func storeSteps(_ steps: [StoryStep], documentId: String) throws {
        let tombstoned = try tombstonedIds(among: steps.map(\.id))
        try db.transaction {
            for step in steps where !tombstoned.contains(step.id) {
                try writeStep(step, documentId: documentId)
            }
        }
    }

    public func store(_ folder: Folder) throws {
        try db.run(
            """
            INSERT OR REPLACE INTO folder
            (id, parent_id, workspace_id, title, created_at, last_updated_at, last_synced_at, favorite, icon, icon_tint, deleted)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            [
                .text(folder.id), .text(folder.parentId), .text(folder.workspaceId), .text(folder.title),
                .integer(folder.createdAt?.millis), .integer(folder.lastUpdatedAt?.millis), .integer(folder.lastSyncedAt?.millis),
                .bool(folder.favorite), .text(folder.icon?.label), .integer(folder.icon?.tint.map(Int64.init)),
                .bool(folder.deleted),
            ]
        )
    }

    /// Removes the document from this device for good (it was deleted elsewhere).
    public func hardDeleteDocument(id: String) throws {
        try db.transaction {
            try db.run("DELETE FROM document_tag_link WHERE document_id = ?", [.text(id)])
            try db.run("DELETE FROM story_step WHERE document_id = ?", [.text(id)])
            try db.run("DELETE FROM story_step_tombstone WHERE document_id = ?", [.text(id)])
            try db.run("DELETE FROM document WHERE id = ?", [.text(id)])
            try db.run(
                "DELETE FROM presentation_step WHERE presentation_id IN (SELECT id FROM presentation WHERE document_id = ?)",
                [.text(id)]
            )
            try db.run("DELETE FROM presentation WHERE document_id = ?", [.text(id)])
        }
    }

    // MARK: - PresentationsStore

    public func presentations(ofDocument documentId: String) async throws -> [Presentation] {
        try db.query(
            "SELECT id, document_id, title, created_at FROM presentation WHERE document_id = ? ORDER BY created_at DESC",
            [.text(documentId)],
            row: presentationRow
        )
        .map { presentation in
            var presentation = presentation
            presentation.slides = (try? slides(of: presentation.id)) ?? []
            return presentation
        }
    }

    public func presentation(id: String) async throws -> Presentation? {
        guard var presentation = try db.query(
            "SELECT id, document_id, title, created_at FROM presentation WHERE id = ?",
            [.text(id)],
            row: presentationRow
        ).first else { return nil }
        presentation.slides = try slides(of: id)
        return presentation
    }

    /// The documents, and the presentations the local AI made.
    public func searchAll(query: String) async throws -> SearchResults {
        SearchResults(
            documents: try await search(query: query),
            presentations: try await searchPresentations(query: query)
        )
    }

    /// The presentations whose title has `query`, newest first, without their slides.
    public func searchPresentations(query: String) async throws -> [Presentation] {
        let trimmed = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return [] }
        return try db.query(
            "SELECT id, document_id, title, created_at FROM presentation WHERE title LIKE ? ORDER BY created_at DESC LIMIT 20",
            [.text("%\(trimmed)%")],
            row: presentationRow
        )
    }

    /// Writes the presentation like a document: one row per step, the title of each slide as a
    /// `title` step at position 0 of its slide.
    public func savePresentation(_ presentation: Presentation) async throws {
        try db.transaction {
            try db.run(
                """
                INSERT INTO presentation (id, document_id, title, created_at) VALUES (?, ?, ?, ?)
                ON CONFLICT(id) DO UPDATE SET
                    document_id = excluded.document_id, title = excluded.title, created_at = excluded.created_at
                """,
                [.text(presentation.id), .text(presentation.documentId), .text(presentation.title), .integer(presentation.createdAt)]
            )
            try db.run("DELETE FROM presentation_step WHERE presentation_id = ?", [.text(presentation.id)])
            for (index, slide) in presentation.slides.enumerated() {
                let steps = slide.asDocument(id: "\(presentation.id)-\(index)", now: presentation.createdAt).content
                for (stepIndex, step) in steps.enumerated() {
                    let json = String(decoding: try encoder.encode(step), as: UTF8.self)
                    try db.run(
                        "INSERT OR REPLACE INTO presentation_step (id, presentation_id, slide_index, position, content) VALUES (?, ?, ?, ?, ?)",
                        [.text(step.id), .text(presentation.id), .integer(Int64(index)), .real(Double(stepIndex)), .text(json)]
                    )
                }
            }
        }
    }

    public func deletePresentation(id: String) async throws {
        try db.transaction {
            try db.run("DELETE FROM presentation_step WHERE presentation_id = ?", [.text(id)])
            try db.run("DELETE FROM presentation WHERE id = ?", [.text(id)])
        }
    }

    private func presentationRow(_ row: SQLiteDatabase.Row) -> Presentation? {
        guard let id = row.text(0) else { return nil }
        return Presentation(
            id: id,
            documentId: row.text(1) ?? "",
            title: row.text(2) ?? "",
            createdAt: row.integer(3) ?? 0,
            slides: []
        )
    }

    /// The slides of a presentation, rebuilt from their rows: the `title` step names the slide,
    /// the other steps are its content.
    private func slides(of presentationId: String) throws -> [Slide] {
        let rows = try db.query(
            "SELECT slide_index, content, position FROM presentation_step WHERE presentation_id = ? ORDER BY slide_index, position",
            [.text(presentationId)]
        ) { row -> (index: Int64, step: StoryStep)? in
            guard let index = row.integer(0), let json = row.text(1),
                  var step = try? decoder.decode(StoryStep.self, from: Data(json.utf8))
            else { return nil }
            step.position = row.real(2).map { Rank.at(Int($0.rounded(.down))) } ?? step.position
            return (index, step)
        }

        var slides: [Slide] = []
        var currentIndex: Int64?
        for (index, step) in rows {
            if index != currentIndex {
                slides.append(Slide(title: ""))
                currentIndex = index
            }
            if step.type.number == StoryType.title.number {
                slides[slides.count - 1].title = step.text ?? ""
            } else {
                slides[slides.count - 1].steps.append(step)
            }
        }
        return slides
    }

    public func hardDeleteFolder(id: String) throws {
        try db.run("DELETE FROM folder WHERE id = ?", [.text(id)])
    }

    // MARK: - Schema

    /// Story step positions became ranks (`Rank`): ten zero-padded digits plus an optional base-36
    /// fraction, kept in a TEXT column; before, the column held numbers: indexes (0, 1, 2...),
    /// fractions (1.5) or whole numbers 10000 apart. Once, the steps of every document are
    /// renumbered in their order to the ranks of 0, 1, 2... (children are nested in the JSON, so
    /// only the top-level rows are here) and the table is rebuilt, since SQLite can't change the
    /// type of a column. The JSON of a row may still carry the old number; the column wins.
    private func migratePositionsIfNeeded() throws {
        guard try meta("positions_rank") == nil else { return }

        try db.transaction {
            try db.execute(
                """
                CREATE TABLE story_step_ranks (
                    id TEXT NOT NULL,
                    document_id TEXT NOT NULL,
                    position TEXT NOT NULL,
                    last_updated_at INTEGER,
                    content TEXT NOT NULL,
                    PRIMARY KEY (document_id, id)
                );
                INSERT INTO story_step_ranks (id, document_id, position, last_updated_at, content)
                SELECT s.id, s.document_id,
                    printf('%010d', 1000000 + (SELECT COUNT(*) FROM story_step o
                     WHERE o.document_id = s.document_id
                       AND (o.position < s.position OR (o.position = s.position AND o.id < s.id)))),
                    s.last_updated_at, s.content
                FROM story_step s;
                DROP TABLE story_step;
                ALTER TABLE story_step_ranks RENAME TO story_step;
                CREATE INDEX IF NOT EXISTS story_step_document ON story_step(document_id);
                """
            )
            try setMeta("positions_rank", "1")
        }
    }

    private func createSchema() throws {
        try db.execute(
            """
            CREATE TABLE IF NOT EXISTS document (
                id TEXT PRIMARY KEY NOT NULL,
                title TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                last_updated_at INTEGER NOT NULL,
                last_synced_at INTEGER,
                workspace_id TEXT NOT NULL,
                favorite INTEGER NOT NULL DEFAULT 0,
                parent_document_id TEXT NOT NULL,
                icon TEXT,
                icon_tint INTEGER,
                is_locked INTEGER NOT NULL DEFAULT 0,
                published INTEGER NOT NULL DEFAULT 0,
                deleted INTEGER NOT NULL DEFAULT 0
            );
            CREATE INDEX IF NOT EXISTS document_parent ON document(parent_document_id);

            CREATE TABLE IF NOT EXISTS story_step (
                id TEXT NOT NULL,
                document_id TEXT NOT NULL,
                position TEXT NOT NULL,
                last_updated_at INTEGER,
                content TEXT NOT NULL,
                PRIMARY KEY (document_id, id)
            );
            CREATE INDEX IF NOT EXISTS story_step_document ON story_step(document_id);

            CREATE TABLE IF NOT EXISTS story_step_tombstone (
                step_id TEXT PRIMARY KEY NOT NULL,
                document_id TEXT NOT NULL,
                workspace_id TEXT NOT NULL,
                deleted_at INTEGER NOT NULL
            );

            CREATE TABLE IF NOT EXISTS folder (
                id TEXT PRIMARY KEY NOT NULL,
                parent_id TEXT NOT NULL,
                workspace_id TEXT NOT NULL,
                title TEXT NOT NULL,
                created_at INTEGER,
                last_updated_at INTEGER,
                last_synced_at INTEGER,
                favorite INTEGER NOT NULL DEFAULT 0,
                icon TEXT,
                icon_tint INTEGER,
                deleted INTEGER NOT NULL DEFAULT 0
            );
            CREATE INDEX IF NOT EXISTS folder_parent ON folder(parent_id);

            CREATE TABLE IF NOT EXISTS meta (key TEXT PRIMARY KEY NOT NULL, value TEXT);

            CREATE TABLE IF NOT EXISTS presentation (
                id TEXT PRIMARY KEY NOT NULL,
                document_id TEXT NOT NULL,
                title TEXT NOT NULL,
                created_at INTEGER NOT NULL
            );
            CREATE INDEX IF NOT EXISTS presentation_document ON presentation(document_id);

            CREATE TABLE IF NOT EXISTS presentation_step (
                id TEXT NOT NULL,
                presentation_id TEXT NOT NULL,
                slide_index INTEGER NOT NULL,
                position REAL NOT NULL,
                content TEXT NOT NULL,
                PRIMARY KEY (presentation_id, id)
            );
            CREATE INDEX IF NOT EXISTS presentation_step_presentation ON presentation_step(presentation_id);

            CREATE TABLE IF NOT EXISTS document_tag (
                id TEXT PRIMARY KEY NOT NULL,
                workspace_id TEXT NOT NULL,
                name TEXT NOT NULL,
                description TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                last_updated_at INTEGER NOT NULL,
                last_synced_at INTEGER,
                deleted INTEGER NOT NULL DEFAULT 0
            );
            CREATE INDEX IF NOT EXISTS document_tag_workspace ON document_tag(workspace_id);

            CREATE TABLE IF NOT EXISTS document_tag_link (
                document_id TEXT NOT NULL,
                tag_id TEXT NOT NULL,
                PRIMARY KEY (document_id, tag_id)
            );
            CREATE INDEX IF NOT EXISTS document_tag_link_document ON document_tag_link(document_id);
            """
        )
    }

    // MARK: - Tags

    private static let tagColumns = """
        SELECT id, workspace_id, name, description, created_at, last_updated_at, last_synced_at, deleted
        """

    private func tag(_ row: SQLiteDatabase.Row) -> DocumentTag? {
        guard let id = row.text(0) else { return nil }
        return DocumentTag(
            id: id,
            workspaceId: row.text(1) ?? workspaceId,
            name: row.text(2) ?? "",
            description: row.text(3) ?? "",
            createdAt: row.integer(4) ?? 0,
            lastUpdatedAt: row.integer(5) ?? 0,
            lastSyncedAt: row.integer(6),
            deleted: row.bool(7)
        )
    }

    public func tags() async throws -> [DocumentTag] {
        try storedTags()
    }

    public func documents(withTag tagId: String) async throws -> [WrDocument] {
        try db.query(
            """
            \(Self.documentColumns) FROM document
            WHERE deleted = 0 AND id IN (SELECT document_id FROM document_tag_link WHERE tag_id = ?)
            ORDER BY last_updated_at DESC
            """,
            [.text(tagId)],
            row: documentRow
        ).map(withContent)
    }

    public func allContents() async throws -> FolderContents {
        try seedIfNeeded()
        let folders = try db.query("\(Self.folderColumns) FROM folder WHERE deleted = 0", row: folder)
        let documents = try db.query(
            "\(Self.documentColumns) FROM document WHERE deleted = 0 ORDER BY last_updated_at DESC",
            row: documentRow
        )
        return FolderContents(folders: folders, documents: documents)
    }

    public func saveTag(_ tag: DocumentTag) async throws {
        try storeTag(tag)
    }

    /// The tags of the workspace that are not tombstones, by name.
    public func storedTags() throws -> [DocumentTag] {
        try db.query("\(Self.tagColumns) FROM document_tag WHERE deleted = 0 ORDER BY name", row: tag)
    }

    /// Every tag, tombstones included: what the sync compares.
    public func storedAllTags() throws -> [DocumentTag] {
        try db.query("\(Self.tagColumns) FROM document_tag ORDER BY name", row: tag)
    }

    /// Tags changed since they were last sent, tombstones included.
    public func storedOutdatedTags() throws -> [DocumentTag] {
        try db.query(
            "\(Self.tagColumns) FROM document_tag WHERE last_synced_at IS NULL OR last_updated_at > last_synced_at",
            row: tag
        )
    }

    public func storedTag(id: String) throws -> DocumentTag? {
        try db.query("\(Self.tagColumns) FROM document_tag WHERE id = ?", [.text(id)], row: tag).first
    }

    public func storeTag(_ tag: DocumentTag) throws {
        try db.run(
            """
            INSERT OR REPLACE INTO document_tag
            (id, workspace_id, name, description, created_at, last_updated_at, last_synced_at, deleted)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """,
            [
                .text(tag.id), .text(tag.workspaceId.isEmpty ? workspaceId : tag.workspaceId), .text(tag.name),
                .text(tag.description), .integer(tag.createdAt), .integer(tag.lastUpdatedAt),
                .integer(tag.lastSyncedAt), .bool(tag.deleted),
            ]
        )
    }

    /// Marks the tag deleted and bumps its time, so the removal syncs like an edit.
    public func softDeleteTag(id: String) throws {
        try db.run("UPDATE document_tag SET deleted = 1, last_updated_at = ? WHERE id = ?", [.integer(Date.nowMillis), .text(id)])
    }

    private func tagIds(of documentId: String) throws -> [String] {
        try db.query("SELECT tag_id FROM document_tag_link WHERE document_id = ? ORDER BY tag_id", [.text(documentId)]) { $0.text(0) }
    }

    /// The document's links are replaced whole, like its steps.
    private func writeTagLinks(_ document: WrDocument) throws {
        try db.run("DELETE FROM document_tag_link WHERE document_id = ?", [.text(document.id)])
        for tagId in document.tagIds {
            try db.run("INSERT OR IGNORE INTO document_tag_link (document_id, tag_id) VALUES (?, ?)", [.text(document.id), .text(tagId)])
        }
    }

    private static let documentColumns = """
        SELECT id, title, created_at, last_updated_at, last_synced_at, workspace_id, favorite,
        parent_document_id, icon, icon_tint, is_locked, published, deleted
        """

    private static let folderColumns = """
        SELECT id, parent_id, workspace_id, title, created_at, last_updated_at, last_synced_at,
        favorite, icon, icon_tint, deleted
        """

    private func documentRow(_ row: SQLiteDatabase.Row) -> WrDocument? {
        guard let id = row.text(0) else { return nil }
        let parentId = row.text(7)
        return WrDocument(
            id: id,
            title: row.text(1) ?? "",
            workspaceId: row.text(5) ?? workspaceId,
            createdAt: row.integer(2) ?? 0,
            lastUpdatedAt: row.integer(3) ?? 0,
            isFavorite: row.bool(6),
            parentId: parentId,
            lastSyncedAt: row.integer(4),
            isLocked: row.bool(10),
            published: row.bool(11),
            deleted: row.bool(12),
            icon: row.text(8).map { IconInfo(label: $0, tint: row.integer(9).map(Int.init)) },
            tagIds: (try? tagIds(of: id)) ?? []
        )
    }

    private func folder(_ row: SQLiteDatabase.Row) -> Folder? {
        guard let id = row.text(0) else { return nil }
        return Folder(
            id: id,
            parentId: row.text(1) ?? Folder.rootId,
            title: row.text(3) ?? "",
            workspaceId: row.text(2) ?? workspaceId,
            favorite: row.bool(7),
            icon: row.text(8).map { IconInfo(label: $0, tint: row.integer(9).map(Int.init)) },
            createdAt: row.integer(4).map(Date.init(millis:)),
            lastUpdatedAt: row.integer(5).map(Date.init(millis:)),
            lastSyncedAt: row.integer(6).map(Date.init(millis:)),
            deleted: row.bool(10)
        )
    }

    private func withContent(_ document: WrDocument) -> WrDocument {
        var document = document
        document.content = (try? steps(of: document.id)) ?? []
        return document
    }

    private func steps(of documentId: String) throws -> [StoryStep] {
        try db.query(
            "SELECT content, position, last_updated_at FROM story_step WHERE document_id = ? ORDER BY position",
            [.text(documentId)]
        ) { row in
            guard let json = row.text(0), var step = try? decoder.decode(StoryStep.self, from: Data(json.utf8)) else {
                return nil
            }
            step.position = row.text(1) ?? step.position
            step.lastUpdatedAt = row.integer(2) ?? step.lastUpdatedAt
            return step
        }
    }

    private func writeMetadata(_ document: WrDocument) throws {
        let parentId = document.parentId.flatMap { $0.isEmpty ? nil : $0 } ?? Folder.rootId
        try db.run(
            """
            INSERT INTO document
            (id, title, created_at, last_updated_at, last_synced_at, workspace_id, favorite, parent_document_id,
             icon, icon_tint, is_locked, published, deleted)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
                title = excluded.title, created_at = excluded.created_at, last_updated_at = excluded.last_updated_at,
                last_synced_at = excluded.last_synced_at, workspace_id = excluded.workspace_id,
                favorite = excluded.favorite, parent_document_id = excluded.parent_document_id,
                icon = excluded.icon, icon_tint = excluded.icon_tint, is_locked = excluded.is_locked,
                published = excluded.published, deleted = excluded.deleted
            """,
            [
                .text(document.id), .text(document.title), .integer(document.createdAt), .integer(document.lastUpdatedAt),
                .integer(document.lastSyncedAt), .text(document.workspaceId.isEmpty ? workspaceId : document.workspaceId),
                .bool(document.isFavorite), .text(parentId), .text(document.icon?.label),
                .integer(document.icon?.tint.map(Int64.init)), .bool(document.isLocked), .bool(document.published),
                .bool(document.deleted),
            ]
        )
        try writeTagLinks(document)
    }

    private func writeStep(_ step: StoryStep, documentId: String) throws {
        let json = String(decoding: try encoder.encode(step), as: UTF8.self)
        try db.run(
            "INSERT OR REPLACE INTO story_step (id, document_id, position, last_updated_at, content) VALUES (?, ?, ?, ?, ?)",
            [.text(step.id), .text(documentId), .text(step.position), .integer(step.lastUpdatedAt), .text(json)]
        )
    }

    private func itemCount(of folderId: String) throws -> Int {
        let folders = try db.query("SELECT COUNT(*) FROM folder WHERE parent_id = ? AND deleted = 0", [.text(folderId)]) { $0.integer(0) }
        let documents = try db.query("SELECT COUNT(*) FROM document WHERE parent_document_id = ? AND deleted = 0", [.text(folderId)]) { $0.integer(0) }
        return Int((folders.first ?? 0) + (documents.first ?? 0))
    }

    // MARK: - Meta

    private func meta(_ key: String) throws -> String? {
        try db.query("SELECT value FROM meta WHERE key = ?", [.text(key)]) { $0.text(0) }.first
    }

    private func setMeta(_ key: String, _ value: String) throws {
        try db.run("INSERT OR REPLACE INTO meta (key, value) VALUES (?, ?)", [.text(key), .text(value)])
    }

    /// Earlier versions kept each document and folder in a JSON file. They're imported once and
    /// moved to `imported-json`, so nothing is lost.
    private func importJsonFilesIfNeeded() throws {
        guard try meta("json_imported") == nil else { return }
        defer { try? setMeta("json_imported", "1") }

        let fileManager = FileManager.default
        guard let files = try? fileManager.contentsOfDirectory(at: directory, includingPropertiesForKeys: nil) else { return }
        let jsonFiles = files.filter { $0.lastPathComponent.hasSuffix(".wrdoc.json") || $0.lastPathComponent.hasSuffix(".wrfolder.json") }
        guard !jsonFiles.isEmpty else { return }

        for url in jsonFiles {
            guard let data = try? Data(contentsOf: url) else { continue }
            if url.lastPathComponent.hasSuffix(".wrdoc.json"), let document = try? decoder.decode(WrDocument.self, from: data) {
                try store(document)
            } else if let folder = try? decoder.decode(Folder.self, from: data) {
                try store(folder)
            }
        }
        // A private space that had files was already seeded.
        try setMeta("seeded", "1")

        let archive = directory.appending(path: "imported-json", directoryHint: .isDirectory)
        try? fileManager.createDirectory(at: archive, withIntermediateDirectories: true)
        for url in jsonFiles {
            try? fileManager.moveItem(at: url, to: archive.appending(path: url.lastPathComponent))
        }
    }

    /// Brings over, once, the offline space of the Compose desktop app. Its database is left as
    /// it is. A document or folder that is already here and was changed later is kept.
    func importComposeDatabaseIfNeeded(from url: URL) throws {
        guard try meta("compose_db_imported") == nil else { return }
        guard let compose = try ComposeDatabase(url: url) else {
            try setMeta("compose_db_imported", "1")
            return
        }
        let contents = try compose.contents(workspaceId: workspaceId)

        try db.transaction {
            for folder in contents.folders {
                if let stored = try storedFolder(id: folder.id),
                   (stored.lastUpdatedAt ?? .distantPast) >= (folder.lastUpdatedAt ?? .distantPast) {
                    continue
                }
                try store(folder)
            }
            for tag in contents.tags where try storedTag(id: tag.id) == nil {
                try storeTag(tag)
            }
            for document in contents.documents {
                if let stored = try storedDocument(id: document.id), stored.lastUpdatedAt >= document.lastUpdatedAt {
                    continue
                }
                try writeMetadata(document)
                try db.run("DELETE FROM story_step WHERE document_id = ?", [.text(document.id)])
                for step in document.content {
                    try writeStep(step, documentId: document.id)
                }
            }
            for presentation in contents.presentations {
                try db.run(
                    "INSERT OR IGNORE INTO presentation (id, document_id, title, created_at) VALUES (?, ?, ?, ?)",
                    [.text(presentation.id), .text(presentation.documentId), .text(presentation.title), .integer(presentation.createdAt)]
                )
            }
            for step in contents.presentationSteps {
                try db.run(
                    "INSERT OR IGNORE INTO presentation_step (id, presentation_id, slide_index, position, content) VALUES (?, ?, ?, ?, ?)",
                    [.text(step.id), .text(step.presentationId), .integer(step.slideIndex), .real(Double(step.position)), .text(step.content)]
                )
            }
            // The Compose app already gave this space its tutorials.
            if !contents.isEmpty {
                try setMeta("seeded", "1")
            }
            try setMeta("compose_db_imported", "1")
        }
    }

    /// Gives a brand new private space the tutorial notes of the Compose app, so both apps
    /// start the same way; a single welcome note when they can't be read.
    private func seedIfNeeded() throws {
        guard seedsWelcome, try meta("seeded") == nil else { return }
        try setMeta("seeded", "1")
        guard try db.query("SELECT COUNT(*) FROM document", row: { $0.integer(0) }).first == 0 else { return }

        let tutorials = Self.tutorialDocuments(workspaceId: workspaceId)
        guard tutorials.isEmpty else {
            for document in tutorials {
                try store(document)
            }
            return
        }

        let welcome = WrDocument(
            id: UUID().uuidString,
            title: String(localized: "Welcome to Writeopia"),
            workspaceId: workspaceId,
            content: [
                StoryStep(type: .title, text: String(localized: "Welcome to Writeopia"), position: Rank.first),
                StoryStep(
                    type: .message,
                    text: String(localized: "This is your private space. Everything you write here stays on this device."),
                    position: Rank.at(1)
                ),
                StoryStep(type: .message, text: String(localized: "Getting started"), tags: [TagInfo(tag: "H2")], position: Rank.at(2)),
                StoryStep(type: .checkItem, text: String(localized: "Create a folder to organise your notes"), checked: false, position: Rank.at(3)),
                StoryStep(type: .checkItem, text: String(localized: "Create your first document"), checked: false, position: Rank.at(4)),
                StoryStep(
                    type: .unorderedListItem,
                    text: String(localized: "Sign in from Settings > Account to sync your notes"),
                    position: Rank.at(5)
                ),
            ],
            parentId: Folder.rootId
        )
        try store(welcome)
    }

    /// The tutorial notes bundled with the app (`Resources/Tutorials`, the same JSON as the
    /// `tutorials` module of the Compose app), newest first so the welcome note leads the list.
    static func tutorialDocuments(workspaceId: String) -> [WrDocument] {
        guard let urls = Bundle.module.urls(forResourcesWithExtension: "json", subdirectory: "Tutorials") else { return [] }
        let decoder = JSONDecoder()
        let now = Date.nowMillis
        return urls
            .sorted { $0.lastPathComponent < $1.lastPathComponent }
            .compactMap { url -> WrDocument? in
                guard let data = try? Data(contentsOf: url), let document = try? decoder.decode(WrDocument.self, from: data) else { return nil }
                return document
            }
            .enumerated()
            .map { index, document in
                var document = document
                document.workspaceId = workspaceId
                document.parentId = Folder.rootId
                document.createdAt = now - Int64(index)
                document.lastUpdatedAt = now - Int64(index)
                return document
            }
    }
}
