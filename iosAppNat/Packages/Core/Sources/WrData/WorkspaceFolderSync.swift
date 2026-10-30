import Foundation
import WrModels

/// What the local folder sync needs from the workspace: every folder and document, and a way
/// to put back the ones read from the files.
public protocol WorkspaceFolderStore: AnyObject {
    /// Every folder and document of the workspace; the documents without their steps.
    func allContents() async throws -> FolderContents
    /// The document with its steps.
    func document(id: String) async throws -> WrDocument
    /// Stores a folder read from a file, new or changed. When there's a backend, it goes up
    /// with the next sync.
    func storeFromFile(_ folder: Folder) async throws
    /// Stores a document read from a file, new or changed, steps included. When there's a
    /// backend, it goes up with the next sync.
    func storeFromFile(_ document: WrDocument) async throws
}

/// A copy of the workspace in a folder the user picked, as JSON files: one `.wrfolder.json`
/// per folder and one `.wrdoc.json` per document, named `{Title}_{id}` like `DocumentToJson`
/// of the Kotlin SDK, plus `writeopia_config_file.json` with the time of the last save. The
/// desktop app in Compose reads and writes the same files (`WriteopiaJsonParser`), so one
/// folder can be shared between the two apps, or kept in iCloud Drive or Dropbox.
public final class WorkspaceFolderSync {
    public static let configFileName = "writeopia_config_file.json"
    public static let folderSuffix = ".wrfolder.json"
    public static let documentSuffix = ".wrdoc.json"

    /// Mirrors `WorkspaceStorageConfig` of the Kotlin SDK.
    public struct Config: Codable, Equatable, Sendable {
        /// Epoch milliseconds of the last save.
        public var lastUpdateTable: Int64

        public init(lastUpdateTable: Int64) {
            self.lastUpdateTable = lastUpdateTable
        }
    }

    public struct Result: Equatable, Sendable {
        /// Written to the folder.
        public var savedDocuments = 0
        public var savedFolders = 0
        /// Read from the folder into the workspace, because the file was newer or new.
        public var loadedDocuments = 0
        public var loadedFolders = 0
        /// JSON files that are not a document or a folder, left as they are.
        public var unreadableFiles: [String] = []

        public init() {}
    }

    private let store: WorkspaceFolderStore
    private let workspaceId: String
    private let encoder: JSONEncoder = {
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
        return encoder
    }()
    private let decoder = JSONDecoder()
    private let fileManager = FileManager.default

    public init(store: WorkspaceFolderStore, workspaceId: String) {
        self.store = store
        self.workspaceId = workspaceId
    }

    // MARK: - Save

    /// Writes every folder and document of the workspace to `folder`, over the files already
    /// there, and the config file. A document renamed since the last save gets a new file and
    /// loses the old one, in the same place.
    @discardableResult
    public func save(to folder: URL) async throws -> Result {
        var result = Result()
        try write(try await fullContents(), to: folder, result: &result)
        return result
    }

    /// Everything in the workspace, the documents with their steps.
    private func fullContents() async throws -> FolderContents {
        var contents = try await store.allContents()
        var documents: [WrDocument] = []
        for document in contents.documents {
            documents.append(try await store.document(id: document.id))
        }
        contents.documents = documents
        return contents
    }

    /// Loads from `folder` what is newer there (documents and folders changed by the desktop
    /// app or by hand, or not in the workspace yet), then saves the workspace to it. The file of
    /// a document or folder deleted in the app is removed instead of loaded, as long as nothing
    /// else wrote it since the app did.
    public func sync(with folder: URL) async throws -> Result {
        var result = Result()
        let files = try readFiles(in: folder, result: &result)
        let manifest = readManifest(in: folder)
        let before = try await store.allContents()

        let storedFolders = Dictionary(before.folders.map { ($0.id, $0) }, uniquingKeysWith: { first, _ in first })
        for file in files.folders {
            if let stored = storedFolders[file.folder.id] {
                guard isNewer(file.folder, than: stored) else { continue }
            } else if wasDeletedInApp(file.url, id: file.folder.id, manifest: manifest) {
                try? fileManager.removeItem(at: file.url)
                continue
            }
            try await store.storeFromFile(file.folder)
            result.loadedFolders += 1
        }

        let storedDocuments = Dictionary(before.documents.map { ($0.id, $0) }, uniquingKeysWith: { first, _ in first })
        for file in files.documents {
            if let stored = storedDocuments[file.document.id] {
                guard isNewer(file.document, than: stored) else { continue }
            } else if wasDeletedInApp(file.url, id: file.document.id, manifest: manifest) {
                try? fileManager.removeItem(at: file.url)
                continue
            }
            try await store.storeFromFile(file.document)
            result.loadedDocuments += 1
        }

        try write(try await fullContents(), to: folder, result: &result)
        return result
    }

    // MARK: - Manifest

    /// When the app last wrote each file, by document or folder id. Kept in the folder as a
    /// hidden file without extension, which the reader of the Kotlin SDK skips. It tells the
    /// file of something deleted in the app (written by the app, untouched since) from a file
    /// added to the folder from outside, which is loaded.
    struct Manifest: Codable, Equatable {
        /// Epoch milliseconds.
        var writtenAt: [String: Int64] = [:]
    }

    public static let manifestFileName = ".writeopia_saved"

    private func readManifest(in folder: URL) -> Manifest {
        guard let data = try? Data(contentsOf: folder.appending(path: Self.manifestFileName)),
              let manifest = try? decoder.decode(Manifest.self, from: data) else { return Manifest() }
        return manifest
    }

    /// The app wrote the file and nothing wrote it since, but the item is gone from the app.
    private func wasDeletedInApp(_ url: URL, id: String, manifest: Manifest) -> Bool {
        guard let writtenAt = manifest.writtenAt[id],
              let modified = try? url.resourceValues(forKeys: [.contentModificationDateKey]).contentModificationDate
        else { return false }
        // A second of room for file systems that keep whole seconds.
        return modified.millis <= writtenAt + 1_000
    }

    // MARK: - Files

    /// The time of the last save written in the config file of `folder`, if any.
    public func lastSave(in folder: URL) -> Date? {
        guard let data = try? Data(contentsOf: folder.appending(path: Self.configFileName)),
              let config = try? decoder.decode(Config.self, from: data) else { return nil }
        return Date(millis: config.lastUpdateTable)
    }

    struct FolderFile {
        let url: URL
        let folder: Folder
    }

    struct DocumentFile {
        let url: URL
        let document: WrDocument
    }

    /// The folders and documents in `folder` and its subfolders, like `KmpFileReader.readDirectory`:
    /// `.wrfolder` files are folders and every other JSON file a document, the config file aside.
    func readFiles(in folder: URL, result: inout Result) throws -> (folders: [FolderFile], documents: [DocumentFile]) {
        var folders: [FolderFile] = []
        var documents: [DocumentFile] = []
        for url in jsonFiles(in: folder) {
            let name = url.lastPathComponent
            guard name != Self.configFileName else { continue }
            guard let data = try? Data(contentsOf: url) else {
                result.unreadableFiles.append(name)
                continue
            }
            if name.contains(".wrfolder") {
                if let folder = try? decoder.decode(Folder.self, from: data), !folder.id.isEmpty {
                    folders.append(FolderFile(url: url, folder: folder))
                } else {
                    result.unreadableFiles.append(name)
                }
            } else if let document = try? decoder.decode(WrDocument.self, from: data), !document.id.isEmpty {
                documents.append(DocumentFile(url: url, document: document))
            } else {
                result.unreadableFiles.append(name)
            }
        }
        return (folders, documents)
    }

    private func write(_ contents: FolderContents, to folder: URL, result: inout Result) throws {
        try fileManager.createDirectory(at: folder, withIntermediateDirectories: true)
        let existing = existingFiles(in: folder)

        for item in contents.folders {
            try write(encoder.encode(item.api), id: item.id, title: item.title, suffix: Self.folderSuffix, in: folder, existing: existing)
            result.savedFolders += 1
        }
        for document in contents.documents {
            try write(encoder.encode(document), id: document.id, title: document.title, suffix: Self.documentSuffix, in: folder, existing: existing)
            result.savedDocuments += 1
        }

        // Files written before and still there keep their time; the ones just written take now,
        // after the writes, so their modification date is never later.
        let now = Date.nowMillis
        var manifest = readManifest(in: folder)
        let present = Set(existing.keys.map { key in
            [Self.folderSuffix, Self.documentSuffix].reduce(key) { $0.hasSuffix($1) ? String($0.dropLast($1.count)) : $0 }
        })
        manifest.writtenAt = manifest.writtenAt.filter { present.contains($0.key) }
        for item in contents.folders {
            manifest.writtenAt[item.id] = now
        }
        for document in contents.documents {
            manifest.writtenAt[document.id] = now
        }
        try encoder.encode(manifest).write(to: folder.appending(path: Self.manifestFileName), options: .atomic)

        let config = Config(lastUpdateTable: now)
        try encoder.encode(config).write(to: folder.appending(path: Self.configFileName), options: .atomic)
    }

    /// Writes the item where its previous file was (so a file moved into a subfolder stays
    /// there) and removes the previous file when the title changed its name.
    private func write(_ data: Data, id: String, title: String, suffix: String, in folder: URL, existing: [String: [URL]]) throws {
        let previous = existing[id + suffix] ?? []
        let directory = previous.first?.deletingLastPathComponent() ?? folder
        let url = directory.appending(path: Self.fileName(title: title, id: id, suffix: suffix))
        try data.write(to: url, options: .atomic)
        for old in previous where old.standardizedFileURL != url.standardizedFileURL {
            try? fileManager.removeItem(at: old)
        }
    }

    /// `{Title}_{id}.wrdoc.json`, the name `DocumentToJson` of the Kotlin SDK gives the file.
    /// Characters a file name can't carry become `_` too.
    public static func fileName(title: String, id: String, suffix: String) -> String {
        let name = title.trimmingCharacters(in: .whitespacesAndNewlines)
            .map { character -> Character in
                switch character {
                case " ", "/", ":", "\\": "_"
                default: character
                }
            }
        return String(name) + "_" + id + suffix
    }

    /// The JSON files of the folder by the `{id}{suffix}` their name ends with.
    private func existingFiles(in folder: URL) -> [String: [URL]] {
        var files: [String: [URL]] = [:]
        for url in jsonFiles(in: folder) {
            let name = url.lastPathComponent
            for suffix in [Self.folderSuffix, Self.documentSuffix] where name.hasSuffix(suffix) {
                let stem = String(name.dropLast(suffix.count))
                guard let underscore = stem.lastIndex(of: "_") else { continue }
                let id = String(stem[stem.index(after: underscore)...])
                guard !id.isEmpty else { continue }
                files[id + suffix, default: []].append(url)
            }
        }
        return files
    }

    private func jsonFiles(in folder: URL) -> [URL] {
        guard let enumerator = fileManager.enumerator(
            at: folder,
            includingPropertiesForKeys: [.isRegularFileKey],
            options: [.skipsHiddenFiles, .skipsPackageDescendants]
        ) else { return [] }
        var urls: [URL] = []
        for case let url as URL in enumerator where url.pathExtension == "json" {
            if (try? url.resourceValues(forKeys: [.isRegularFileKey]).isRegularFile) == true {
                urls.append(url)
            }
        }
        return urls.sorted { $0.path() < $1.path() }
    }

    private func isNewer(_ file: Folder, than stored: Folder?) -> Bool {
        guard let stored else { return true }
        return (file.lastUpdatedAt ?? .distantPast) > (stored.lastUpdatedAt ?? .distantPast)
    }

    private func isNewer(_ file: WrDocument, than stored: WrDocument?) -> Bool {
        guard let stored else { return true }
        return file.lastUpdatedAt > stored.lastUpdatedAt
    }
}

// MARK: - Stores

extension LocalDocumentsRepository: WorkspaceFolderStore {
    public func storeFromFile(_ folder: Folder) async throws {
        try store(folderFromFile(folder, workspaceId: workspaceId))
    }

    public func storeFromFile(_ document: WrDocument) async throws {
        try store(documentFromFile(document, workspaceId: workspaceId, lastSyncedAt: nil))
    }
}

extension SyncedDocumentsRepository: WorkspaceFolderStore {
    /// Stored as never synced, so the next sync sends it to the backend.
    public func storeFromFile(_ folder: Folder) async throws {
        try local.store(folderFromFile(folder, workspaceId: workspaceId))
    }

    /// A document already in the workspace keeps its sync time, so the next sync sends the
    /// newer steps; a new one is sent whole.
    public func storeFromFile(_ document: WrDocument) async throws {
        let stored = try local.storedDocument(id: document.id)
        try local.store(documentFromFile(document, workspaceId: workspaceId, lastSyncedAt: stored?.lastSyncedAt))
    }
}

/// The folder as read from a file, made part of this workspace and waiting for a sync.
private func folderFromFile(_ folder: Folder, workspaceId: String) -> Folder {
    Folder(
        id: folder.id,
        parentId: folder.parentId.isEmpty ? Folder.rootId : folder.parentId,
        title: folder.title,
        workspaceId: workspaceId,
        favorite: folder.favorite,
        itemCount: folder.itemCount,
        icon: folder.icon,
        createdAt: folder.createdAt,
        lastUpdatedAt: folder.lastUpdatedAt,
        lastSyncedAt: nil,
        deleted: false
    )
}

private func documentFromFile(_ document: WrDocument, workspaceId: String, lastSyncedAt: Int64?) -> WrDocument {
    var document = document
    document.workspaceId = workspaceId
    document.lastSyncedAt = lastSyncedAt
    document.deleted = false
    return document
}
