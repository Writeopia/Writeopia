import Foundation
import Testing
@testable import WrData
import WrModels

@Suite struct ComposeDatabaseImportTests {
    let directory = FileManager.default.temporaryDirectory
        .appending(path: "wr compose import \(UUID().uuidString)", directoryHint: .isDirectory)

    var composeUrl: URL { directory.appending(path: "compose/writeopia_1.db") }
    var spaceDirectory: URL { directory.appending(path: "space", directoryHint: .isDirectory) }

    /// A database with the tables of the Compose app, as SQLDelight creates them.
    func makeComposeDatabase() throws -> SQLiteDatabase {
        let db = try SQLiteDatabase(url: composeUrl, journalMode: .delete)
        try db.execute(
            """
            CREATE TABLE documentEntity (
              id TEXT PRIMARY KEY, title TEXT NOT NULL, created_at INTEGER NOT NULL, last_updated_at INTEGER NOT NULL,
              last_synced_at INTEGER, workspace_id TEXT NOT NULL, favorite INTEGER NOT NULL, parent_document_id TEXT NOT NULL,
              icon TEXT, icon_tint INTEGER, is_locked INTEGER NOT NULL, deleted INTEGER NOT NULL
            );
            CREATE TABLE storyStepEntity (
              id TEXT PRIMARY KEY, local_id TEXT NOT NULL, type INTEGER NOT NULL, parent_id TEXT, url TEXT, path TEXT,
              text TEXT, checked INTEGER NOT NULL, position REAL NOT NULL, document_id TEXT NOT NULL, is_group INTEGER NOT NULL,
              has_inner_steps INTEGER NOT NULL, background_color INTEGER, tags TEXT NOT NULL, spans TEXT NOT NULL,
              link_to_document TEXT, last_updated_at INTEGER
            );
            CREATE TABLE folderEntity (
              id TEXT PRIMARY KEY, parent_id TEXT NOT NULL, workspace_id TEXT NOT NULL, title TEXT NOT NULL,
              created_at INTEGER NOT NULL, last_updated_at INTEGER NOT NULL, last_synced_at INTEGER, favorite INTEGER NOT NULL,
              icon TEXT, icon_tint INTEGER, deleted INTEGER NOT NULL DEFAULT 0
            );
            CREATE TABLE documentTagEntity (
              id TEXT PRIMARY KEY, workspace_id TEXT NOT NULL, name TEXT NOT NULL, description TEXT NOT NULL,
              created_at INTEGER NOT NULL, last_updated_at INTEGER NOT NULL, last_synced_at INTEGER, deleted INTEGER NOT NULL DEFAULT 0
            );
            CREATE TABLE documentTagLinkEntity (document_id TEXT NOT NULL, tag_id TEXT NOT NULL, PRIMARY KEY (document_id, tag_id));
            CREATE TABLE presentationEntity (
              id TEXT PRIMARY KEY, document_id TEXT NOT NULL, workspace_id TEXT NOT NULL, title TEXT NOT NULL, created_at INTEGER NOT NULL
            );
            CREATE TABLE presentationStepEntity (
              id TEXT PRIMARY KEY, presentation_id TEXT NOT NULL, slide_index INTEGER NOT NULL, position REAL NOT NULL, content TEXT NOT NULL
            );
            """
        )
        return db
    }

    func insertDocument(_ db: SQLiteDatabase, id: String, title: String, parent: String = "root", updatedAt: Int64 = 2_000) throws {
        try db.run(
            "INSERT INTO documentEntity VALUES (?, ?, 1000, ?, 1500, 'PbTRGRrWwO', 1, ?, 'note', 3, 0, 0)",
            [.text(id), .text(title), .integer(updatedAt), .text(parent)]
        )
    }

    func insertStep(
        _ db: SQLiteDatabase, id: String, document: String, type: Int64, text: String?, position: Int,
        parent: String? = nil, checked: Bool = false, tags: String = "", spans: String = "", link: String? = nil,
        color: Int64? = nil
    ) throws {
        try db.run(
            "INSERT INTO storyStepEntity VALUES (?, ?, ?, ?, NULL, NULL, ?, ?, ?, ?, 0, 0, ?, ?, ?, ?, 1800)",
            [
                .text(id), .text(UUID().uuidString), .integer(type), .text(parent), .text(text), .bool(checked), .real(Double(position)),
                .text(document), .integer(color), .text(tags), .text(spans), .text(link),
            ]
        )
    }

    @Test func importsTheOfflineSpaceOfTheComposeApp() async throws {
        let compose = try makeComposeDatabase()
        try compose.run("INSERT INTO folderEntity VALUES ('f1', 'root', 'PbTRGRrWwO', 'Work', 1000, 2000, 1500, 1, 'folder', 2, 0)")
        try insertDocument(compose, id: "d1", title: "Plan", parent: "f1")
        try insertDocument(compose, id: "d2", title: "Linked")
        try insertStep(compose, id: "s0", document: "d1", type: 11, text: "Plan", position: 0, color: 42)
        try insertStep(compose, id: "s1", document: "d1", type: 0, text: "Hello, world", position: 1, tags: "H2", spans: "0:5:BOLD,7:12:LINK:https://a.com/x,y")
        try insertStep(compose, id: "s2", document: "d1", type: 10, text: "Done", position: 2, checked: true)
        try insertStep(compose, id: "s3", document: "d1", type: 20, text: nil, position: 3, link: "d2")
        try insertStep(compose, id: "g1", document: "d1", type: 0, text: "Group", position: 4)
        try insertStep(compose, id: "c1", document: "d1", type: 0, text: "Inside", position: 0, parent: "g1")
        try compose.run("INSERT INTO documentTagEntity VALUES ('t1', 'PbTRGRrWwO', 'Ideas', '', 1000, 1000, 1500, 0)")
        try compose.run("INSERT INTO documentTagLinkEntity VALUES ('d1', 't1')")
        try compose.run("INSERT INTO presentationEntity VALUES ('p1', 'd1', 'PbTRGRrWwO', 'Talk', 1000)")
        try compose.run(
            "INSERT INTO presentationStepEntity VALUES ('ps1', 'p1', 0, 0, ?)",
            [.text(#"{"id":"ps1","type":{"name":"title","number":11},"text":"Intro","position":0}"#)]
        )

        let repository = LocalDocumentsRepository(directory: spaceDirectory, composeDatabase: composeUrl)

        let root = try await repository.folderContents(folderId: Folder.rootId)
        #expect(root.folders.map(\.title) == ["Work"])
        #expect(root.folders[0].workspaceId == Workspace.localId)
        #expect(root.folders[0].lastSyncedAt == nil)
        #expect(root.documents.map(\.id) == ["d2"], "No tutorials on top of the imported space")

        let document = try await repository.document(id: "d1")
        #expect(document.parentId == "f1")
        #expect(document.workspaceId == Workspace.localId)
        #expect(document.lastSyncedAt == nil)
        #expect(document.isFavorite)
        #expect(document.icon == IconInfo(label: "note", tint: 3))
        #expect(document.tagIds == ["t1"])
        #expect(document.content.map(\.id) == ["s0", "s1", "s2", "s3", "g1"])
        #expect(document.content[0].type == .title)
        #expect(document.content[0].decoration?.backgroundColor == 42)
        #expect(document.content[1].tags == [TagInfo(tag: "H2")])
        #expect(document.content[1].spans == [
            SpanInfo(start: 0, end: 5, span: "BOLD"),
            SpanInfo(start: 7, end: 12, span: "LINK", extra: "https://a.com/x,y"),
        ])
        #expect(document.content[1].checked == nil)
        #expect(document.content[2].type == .checkItem)
        #expect(document.content[2].checked == true)
        #expect(document.content[3].documentLink == DocumentLink(id: "d2", title: "Linked"))
        #expect(document.content[4].steps.map(\.text) == ["Inside"])
        #expect(document.content[4].steps.first?.parentId == "g1")

        #expect(try await repository.tags().map(\.name) == ["Ideas"])
        let presentation = try #require(try await repository.presentation(id: "p1"))
        #expect(presentation.slides.map(\.title) == ["Intro"])
    }

    @Test func importsOnlyOnce() async throws {
        let compose = try makeComposeDatabase()
        try insertDocument(compose, id: "d1", title: "Plan")
        _ = LocalDocumentsRepository(directory: spaceDirectory, composeDatabase: composeUrl)

        try insertDocument(compose, id: "d2", title: "Later")
        let reopened = LocalDocumentsRepository(directory: spaceDirectory, composeDatabase: composeUrl)

        #expect(try await reopened.folderContents(folderId: Folder.rootId).documents.map(\.id) == ["d1"])
    }

    @Test func keepsADocumentChangedLaterInTheMacApp() async throws {
        let space = LocalDocumentsRepository(directory: spaceDirectory, seedsWelcome: false)
        try space.store(WrDocument(id: "d1", title: "Edited on the Mac", workspaceId: Workspace.localId, lastUpdatedAt: 5_000, parentId: Folder.rootId))

        let compose = try makeComposeDatabase()
        try insertDocument(compose, id: "d1", title: "Older", updatedAt: 2_000)
        try insertDocument(compose, id: "d2", title: "Only in Compose")
        let repository = LocalDocumentsRepository(directory: spaceDirectory, seedsWelcome: false, composeDatabase: composeUrl)

        #expect(try await repository.document(id: "d1").title == "Edited on the Mac")
        #expect(try await repository.document(id: "d2").title == "Only in Compose")
    }

    @Test func seedsTheTutorialsWithoutAComposeDatabase() async throws {
        let repository = LocalDocumentsRepository(directory: spaceDirectory, composeDatabase: composeUrl)

        #expect(try await repository.folderContents(folderId: Folder.rootId).documents.count == 5)
    }

    @Test func readsAnOldDatabaseWithoutTheNewerColumns() async throws {
        let db = try SQLiteDatabase(url: composeUrl, journalMode: .delete)
        try db.execute(
            """
            CREATE TABLE documentEntity (id TEXT PRIMARY KEY, title TEXT NOT NULL, created_at INTEGER NOT NULL,
              last_updated_at INTEGER NOT NULL, workspace_id TEXT NOT NULL, favorite INTEGER NOT NULL, parent_document_id TEXT NOT NULL);
            CREATE TABLE storyStepEntity (id TEXT PRIMARY KEY, type INTEGER NOT NULL, text TEXT, checked INTEGER NOT NULL,
              position REAL NOT NULL, document_id TEXT NOT NULL, tags TEXT NOT NULL, spans TEXT NOT NULL);
            INSERT INTO documentEntity VALUES ('d1', 'Old', 1000, 2000, 'w', 0, 'root');
            INSERT INTO storyStepEntity VALUES ('s1', 0, 'Text', 0, 0, 'd1', '', '');
            """
        )

        let repository = LocalDocumentsRepository(directory: spaceDirectory, composeDatabase: composeUrl)

        let document = try await repository.document(id: "d1")
        #expect(document.title == "Old")
        #expect(document.content.map(\.text) == ["Text"])
    }

    @Test func parsesTheSpansOfTheComposeApp() {
        #expect(ComposeDatabase.spans("") == [])
        #expect(ComposeDatabase.spans("3:1:italic,0:2:LINK:null") == [
            SpanInfo(start: 1, end: 3, span: "ITALIC"),
            SpanInfo(start: 0, end: 2, span: "LINK"),
        ])
        #expect(ComposeDatabase.spans("0:4:LINK:https://a.com:8080/p") == [SpanInfo(start: 0, end: 4, span: "LINK", extra: "https://a.com:8080/p")])
    }
}
