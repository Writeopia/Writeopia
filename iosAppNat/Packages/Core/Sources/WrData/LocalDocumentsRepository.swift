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
public final class LocalDocumentsRepository: DocumentsRepository, StepStore {
    private let directory: URL
    private let workspaceId: String
    private let seedsWelcome: Bool
    private let db: SQLiteDatabase
    private let encoder = JSONEncoder()
    private let decoder = JSONDecoder()

    public convenience init(
        directory: URL = LocalDocumentsRepository.defaultDirectory,
        workspaceId: String = Workspace.localId,
        seedsWelcome: Bool = true
    ) {
        do {
            try self.init(
                opening: directory,
                workspaceId: workspaceId,
                seedsWelcome: seedsWelcome,
                importsLegacyJson: true,
                journalMode: .wal
            )
        } catch {
            fatalError("Could not open the documents database: \(error)")
        }
    }

    /// The private space in a folder the user picked (the Mac app). The folder is left as it
    /// is: JSON files already there are not imported nor moved, and the database uses a single
    /// file so folders synced by iCloud Drive or Dropbox stay consistent. Throws when the folder
    /// can't be written, so a stale choice can fall back to the default location.
    public static func inUserFolder(_ directory: URL) throws -> LocalDocumentsRepository {
        try LocalDocumentsRepository(
            opening: directory,
            workspaceId: Workspace.localId,
            seedsWelcome: true,
            importsLegacyJson: false,
            journalMode: .delete
        )
    }

    init(
        opening directory: URL,
        workspaceId: String,
        seedsWelcome: Bool,
        importsLegacyJson: Bool,
        journalMode: SQLiteDatabase.JournalMode
    ) throws {
        self.directory = directory
        self.workspaceId = workspaceId
        self.seedsWelcome = seedsWelcome
        db = try SQLiteDatabase(url: directory.appending(path: "writeopia.sqlite"), journalMode: journalMode)
        try createSchema()
        if importsLegacyJson {
            try importJsonFilesIfNeeded()
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
        try store(document)
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

    public func saveEdit(document: WrDocument, changedSteps: [StoryStep], deletedStepIds: [String]) throws {
        try db.transaction {
            try writeMetadata(document)
            for step in changedSteps {
                try writeStep(step, documentId: document.id)
            }
            for id in deletedStepIds {
                try db.run("DELETE FROM story_step WHERE id = ? AND document_id = ?", [.text(id), .text(document.id)])
            }
        }
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

    /// Stores the whole document, replacing its steps.
    public func store(_ document: WrDocument) throws {
        try db.transaction {
            try writeMetadata(document)
            try db.run("DELETE FROM story_step WHERE document_id = ?", [.text(document.id)])
            for step in document.content {
                try writeStep(step, documentId: document.id)
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
            try db.run("DELETE FROM story_step WHERE document_id = ?", [.text(id)])
            try db.run("DELETE FROM document WHERE id = ?", [.text(id)])
        }
    }

    public func hardDeleteFolder(id: String) throws {
        try db.run("DELETE FROM folder WHERE id = ?", [.text(id)])
    }

    // MARK: - Schema

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
                position REAL NOT NULL,
                last_updated_at INTEGER,
                content TEXT NOT NULL,
                PRIMARY KEY (document_id, id)
            );
            CREATE INDEX IF NOT EXISTS story_step_document ON story_step(document_id);

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
            """
        )
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
            icon: row.text(8).map { IconInfo(label: $0, tint: row.integer(9).map(Int.init)) }
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
            step.position = row.real(1) ?? step.position
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
    }

    private func writeStep(_ step: StoryStep, documentId: String) throws {
        let json = String(decoding: try encoder.encode(step), as: UTF8.self)
        try db.run(
            "INSERT OR REPLACE INTO story_step (id, document_id, position, last_updated_at, content) VALUES (?, ?, ?, ?, ?)",
            [.text(step.id), .text(documentId), .real(step.position), .integer(step.lastUpdatedAt), .text(json)]
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
                StoryStep(type: .title, text: String(localized: "Welcome to Writeopia"), position: 0),
                StoryStep(
                    type: .message,
                    text: String(localized: "This is your private space. Everything you write here stays on this device."),
                    position: 1
                ),
                StoryStep(type: .message, text: String(localized: "Getting started"), tags: [TagInfo(tag: "H2")], position: 2),
                StoryStep(type: .checkItem, text: String(localized: "Create a folder to organise your notes"), checked: false, position: 3),
                StoryStep(type: .checkItem, text: String(localized: "Create your first document"), checked: false, position: 4),
                StoryStep(
                    type: .unorderedListItem,
                    text: String(localized: "Sign in from Settings > Account to sync your notes"),
                    position: 5
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
