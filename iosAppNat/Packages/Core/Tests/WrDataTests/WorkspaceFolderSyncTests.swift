import Foundation
import Testing
@testable import WrData
import WrModels

/// The folder of the computer a workspace is saved to and synced with, as the JSON files the
/// Compose desktop app writes (`DocumentToJson`) and reads (`WriteopiaJsonParser`).
@Suite struct WorkspaceFolderSyncTests {
    let directory = FileManager.default.temporaryDirectory
        .appending(path: "wr tests \(UUID().uuidString)", directoryHint: .isDirectory)
    let folder = FileManager.default.temporaryDirectory
        .appending(path: "wr folder \(UUID().uuidString)", directoryHint: .isDirectory)

    private func makeStore() -> LocalDocumentsRepository {
        LocalDocumentsRepository(directory: directory, seedsWelcome: false)
    }

    private func sync(_ store: LocalDocumentsRepository) -> WorkspaceFolderSync {
        WorkspaceFolderSync(store: store, workspaceId: Workspace.localId)
    }

    /// The visible files of the folder (the manifest is hidden).
    private func fileNames(in directory: URL? = nil) throws -> Set<String> {
        Set(try FileManager.default.contentsOfDirectory(atPath: (directory ?? folder).path(percentEncoded: false)).filter { !$0.hasPrefix(".") })
    }

    private func json(_ name: String) throws -> [String: Any] {
        let data = try Data(contentsOf: folder.appending(path: name))
        return try #require(JSONSerialization.jsonObject(with: data) as? [String: Any])
    }

    @Test func savesEveryFolderAndDocumentAsTheKotlinSdkNamesThem() async throws {
        let store = makeStore()
        let ideas = try await store.createFolder(title: "Ideas", parentId: Folder.rootId)
        let draft = try await store.createDocument(title: "Book draft", parentId: ideas.id)
        let plain = try await store.createDocument(title: "Plain", parentId: Folder.rootId)

        let result = try await sync(store).save(to: folder)

        #expect(result.savedDocuments == 2)
        #expect(result.savedFolders == 1)
        #expect(try fileNames() == [
            "Ideas_\(ideas.id).wrfolder.json",
            "Book_draft_\(draft.id).wrdoc.json",
            "Plain_\(plain.id).wrdoc.json",
            "writeopia_config_file.json",
        ])
        let config = try json("writeopia_config_file.json")
        #expect((config["lastUpdateTable"] as? Int64 ?? 0) > 0)
        #expect(config.count == 1)
    }

    @Test func filesCarryTheFieldsOfTheKotlinApi() async throws {
        let store = makeStore()
        let ideas = try await store.createFolder(title: "Ideas", parentId: Folder.rootId)
        let draft = try await store.createDocument(title: "Draft", parentId: ideas.id)
        try await sync(store).save(to: folder)

        // `FolderApi` has exactly these fields, and the Kotlin JSON rejects unknown ones.
        let folderJson = try json("Ideas_\(ideas.id).wrfolder.json")
        #expect(Set(folderJson.keys) == ["id", "parentId", "title", "createdAt", "lastUpdatedAt", "workspaceId", "favorite", "itemCount"])
        #expect((folderJson["createdAt"] as? String)?.hasSuffix("Z") == true)

        // `DocumentApi` and `StoryStepApi` have these fields (and a few more with defaults).
        let documentJson = try json("Draft_\(draft.id).wrdoc.json")
        let documentFields: Set<String> = [
            "id", "title", "workspaceId", "content", "createdAt", "lastUpdatedAt", "isFavorite", "lastSyncedAt",
            "parentId", "isLocked", "icon", "deleted", "published", "commentConversations", "tagIds",
        ]
        #expect(Set(documentJson.keys).isSubset(of: documentFields))
        #expect(documentJson["parentId"] as? String == ideas.id)
        let steps = try #require(documentJson["content"] as? [[String: Any]])
        let stepFields: Set<String> = [
            "id", "type", "parentId", "url", "path", "text", "checked", "steps", "tags", "spans", "decoration",
            "position", "documentLink", "lastUpdatedAt",
        ]
        for step in steps {
            #expect(Set(step.keys).isSubset(of: stepFields))
            #expect(step["position"] is String)
        }
        #expect(steps.contains { $0["text"] as? String == "Draft" })
    }

    @Test func syncLoadsTheFilesThatAreNewerAndKeepsTheDocumentsThatAre() async throws {
        let store = makeStore()
        let changedOutside = try await store.createDocument(title: "Changed outside", parentId: Folder.rootId)
        let changedHere = try await store.createDocument(title: "Changed here", parentId: Folder.rootId)
        let engine = sync(store)
        try await engine.save(to: folder)

        // The desktop app edited one file: a new line and a later time.
        var outside = try await store.document(id: changedOutside.id)
        outside.content.append(StoryStep(type: .text, text: "From the desktop", position: Rank.at(1)))
        outside.lastUpdatedAt += 1_000
        try JSONEncoder().encode(outside).write(to: folder.appending(path: "Changed_outside_\(outside.id).wrdoc.json"))
        // Another file is older than what the app has.
        var here = try await store.document(id: changedHere.id)
        here.title = "Old title"
        here.lastUpdatedAt -= 1_000
        try JSONEncoder().encode(here).write(to: folder.appending(path: "Changed_here_\(here.id).wrdoc.json"))

        let result = try await engine.sync(with: folder)

        #expect(result.loadedDocuments == 1)
        #expect(result.savedDocuments == 2)
        let loaded = try await store.document(id: changedOutside.id)
        #expect(loaded.content.map(\.text) == ["Changed outside", "From the desktop"])
        #expect(loaded.lastUpdatedAt == outside.lastUpdatedAt)
        #expect(try await store.document(id: changedHere.id).title == "Changed here")
        // The older file was written over with what the app has.
        let rewritten = try json("Changed_here_\(here.id).wrdoc.json")
        #expect(rewritten["title"] as? String == "Changed here")
    }

    @Test func syncLoadsWhatTheDesktopAppWrote() async throws {
        let store = makeStore()
        try FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
        // A folder and a document as `DocumentToJson` writes them: ISO dates in the folder,
        // numeric positions and the comments of an older document.
        let folderJson = """
        {"id":"f1","parentId":"root","title":"Ideas","createdAt":"2026-10-01T10:00:00Z","lastUpdatedAt":"2026-10-02T10:00:00.123Z","workspaceId":"other","favorite":true,"itemCount":1}
        """
        try Data(folderJson.utf8).write(to: folder.appending(path: "Ideas_f1.wrfolder.json"))
        let documentJson = """
        {"id":"d1","title":"Plan","workspaceId":"other","content":[
          {"id":"s1","type":{"name":"title","number":11},"text":"Plan","position":0,"decoration":{}},
          {"id":"s2","type":{"name":"message","number":0},"text":"First","position":1,"tags":[{"tag":"H1","position":0}],"spans":[]}
        ],"createdAt":1700000000000,"lastUpdatedAt":1700000001000,"isFavorite":false,"parentId":"f1","commentConversations":[]}
        """
        try Data(documentJson.utf8).write(to: folder.appending(path: "Plan_d1.wrdoc.json"))

        let result = try await sync(store).sync(with: folder)

        #expect(result.loadedFolders == 1)
        #expect(result.loadedDocuments == 1)
        let ideas = try #require(try await store.folder(id: "f1"))
        #expect(ideas.title == "Ideas")
        #expect(ideas.favorite)
        #expect(ideas.workspaceId == Workspace.localId)
        #expect(ideas.lastUpdatedAt?.millis == 1_790_935_200_123)
        let inside = try await store.folderContents(folderId: "f1")
        #expect(inside.documents.map(\.id) == ["d1"])
        let plan = try await store.document(id: "d1")
        #expect(plan.workspaceId == Workspace.localId)
        #expect(plan.content.map(\.position) == [Rank.at(0), Rank.at(1)])
        #expect(plan.content[1].hasTag("H1"))
        #expect(plan.lastSyncedAt == nil)
    }

    @Test func aRenamedDocumentReplacesItsFileWhereItWas() async throws {
        let store = makeStore()
        let document = try await store.createDocument(title: "Draft", parentId: Folder.rootId)
        let engine = sync(store)
        try await engine.save(to: folder)
        // The user tidied the file into a subfolder.
        let archive = folder.appending(path: "archive", directoryHint: .isDirectory)
        try FileManager.default.createDirectory(at: archive, withIntermediateDirectories: true)
        try FileManager.default.moveItem(
            at: folder.appending(path: "Draft_\(document.id).wrdoc.json"),
            to: archive.appending(path: "Draft_\(document.id).wrdoc.json")
        )

        var renamed = try await store.document(id: document.id)
        renamed.title = "Final / v2"
        renamed.lastUpdatedAt += 1
        try await store.save(renamed)
        try await engine.save(to: folder)

        #expect(try fileNames() == ["archive", "writeopia_config_file.json"])
        #expect(try fileNames(in: archive) == ["Final___v2_\(document.id).wrdoc.json"])
    }

    @Test func syncRemovesTheFilesOfWhatWasDeletedAndSkipsTheRest() async throws {
        let store = makeStore()
        let kept = try await store.createDocument(title: "Kept", parentId: Folder.rootId)
        let deleted = try await store.createDocument(title: "Gone", parentId: Folder.rootId)
        let emptied = try await store.createFolder(title: "Emptied", parentId: Folder.rootId)
        let engine = sync(store)
        try await engine.save(to: folder)
        try await store.deleteDocument(id: deleted.id)
        try await store.deleteItems(ids: [emptied.id])
        try Data("not json".utf8).write(to: folder.appending(path: "notes.wrdoc.json"))
        try Data(#"{"name":"no id"}"#.utf8).write(to: folder.appending(path: "settings.json"))

        let result = try await engine.sync(with: folder)

        #expect(result.loadedDocuments == 0)
        #expect(result.loadedFolders == 0)
        #expect(result.unreadableFiles == ["notes.wrdoc.json", "settings.json"])
        #expect(try fileNames() == ["Kept_\(kept.id).wrdoc.json", "notes.wrdoc.json", "settings.json", "writeopia_config_file.json"])
        #expect(try await store.folder(id: emptied.id) == nil)
    }

    @Test func syncLoadsAFileAddedFromOutsideAndOneEditedAfterTheDeletion() async throws {
        let store = makeStore()
        let deleted = try await store.createDocument(title: "Edited after", parentId: Folder.rootId)
        let engine = sync(store)
        try await engine.save(to: folder)
        try await store.deleteDocument(id: deleted.id)

        // A document exported elsewhere, dropped into the folder with an old date.
        let dropped = WrDocument(id: "dropped", title: "Dropped in", workspaceId: "other", createdAt: 1_700_000_000_000, lastUpdatedAt: 1_700_000_000_000)
        let droppedURL = folder.appending(path: "Dropped_in_dropped.wrdoc.json")
        try JSONEncoder().encode(dropped).write(to: droppedURL)
        try FileManager.default.setAttributes([.modificationDate: Date(millis: 1_700_000_000_000)], ofItemAtPath: droppedURL.path(percentEncoded: false))
        // The desktop app edited the deleted document's file after the Mac wrote it.
        let editedURL = folder.appending(path: "Edited_after_\(deleted.id).wrdoc.json")
        var edited = try JSONDecoder().decode(WrDocument.self, from: Data(contentsOf: editedURL))
        edited.lastUpdatedAt += 1_000
        try JSONEncoder().encode(edited).write(to: editedURL)
        try FileManager.default.setAttributes([.modificationDate: Date().addingTimeInterval(5)], ofItemAtPath: editedURL.path(percentEncoded: false))

        let result = try await engine.sync(with: folder)

        #expect(result.loadedDocuments == 2)
        #expect(try await store.document(id: "dropped").workspaceId == Workspace.localId)
        #expect(try await store.document(id: deleted.id).lastUpdatedAt == edited.lastUpdatedAt)
        #expect(try fileNames() == ["Dropped_in_dropped.wrdoc.json", "Edited_after_\(deleted.id).wrdoc.json", "writeopia_config_file.json"])
    }

    @Test func syncLoadsTheNewestOfTwoFilesWithTheSameId() async throws {
        let store = makeStore()
        let engine = sync(store)
        let stale = Date(millis: 1_700_000_000_000)
        try FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)

        // Two copies of a document: an old one at the root and a newer one in a subfolder.
        let old = WrDocument(id: "twice", title: "Old copy", workspaceId: "other", createdAt: stale.millis, lastUpdatedAt: stale.millis)
        try JSONEncoder().encode(old).write(to: folder.appending(path: "Old_copy_twice.wrdoc.json"))
        let archive = folder.appending(path: "archive", directoryHint: .isDirectory)
        try FileManager.default.createDirectory(at: archive, withIntermediateDirectories: true)
        let new = WrDocument(id: "twice", title: "New copy", workspaceId: "other", createdAt: stale.millis, lastUpdatedAt: stale.millis + 1_000)
        try JSONEncoder().encode(new).write(to: archive.appending(path: "New_copy_twice.wrdoc.json"))
        // And two copies of a folder, the newer one first in path order.
        var oldFolder = Folder(id: "f", parentId: Folder.rootId, title: "Old folder", workspaceId: "other")
        oldFolder.lastUpdatedAt = stale
        var newFolder = oldFolder
        newFolder.title = "New folder"
        newFolder.lastUpdatedAt = stale.addingTimeInterval(1)
        try JSONEncoder().encode(newFolder.api).write(to: folder.appending(path: "New_folder_f.wrfolder.json"))
        try JSONEncoder().encode(oldFolder.api).write(to: folder.appending(path: "Old_folder_f.wrfolder.json"))

        let result = try await engine.sync(with: folder)

        #expect(result.loadedDocuments == 1)
        #expect(result.loadedFolders == 1)
        #expect(try await store.document(id: "twice").title == "New copy")
        #expect(try await store.folder(id: "f")?.title == "New folder")
        // The save keeps one file per item, where the copy first in path order was (the root
        // copy of the document, as uppercase sorts before "archive").
        #expect(try fileNames() == ["New_copy_twice.wrdoc.json", "New_folder_f.wrfolder.json", "writeopia_config_file.json", "archive"])
        #expect(try fileNames(in: archive) == [])
    }

    @Test func syncLooksAtEveryCopyOfADeletedItem() async throws {
        let store = makeStore()
        let copied = try await store.createDocument(title: "Copied", parentId: Folder.rootId)
        let gone = try await store.createDocument(title: "Gone", parentId: Folder.rootId)
        let engine = sync(store)
        try await engine.save(to: folder)
        let archive = folder.appending(path: "archive", directoryHint: .isDirectory)
        try FileManager.default.createDirectory(at: archive, withIntermediateDirectories: true)

        // "Copied" has the file the app wrote and a copy made by hand later (a newer file).
        let copiedURL = folder.appending(path: "Copied_\(copied.id).wrdoc.json")
        let copiedCopyURL = archive.appending(path: "Copied_\(copied.id).wrdoc.json")
        try FileManager.default.copyItem(at: copiedURL, to: copiedCopyURL)
        try FileManager.default.setAttributes([.modificationDate: Date().addingTimeInterval(5)], ofItemAtPath: copiedCopyURL.path(percentEncoded: false))
        // "Gone" has two copies, both untouched since the app wrote them.
        let goneURL = folder.appending(path: "Gone_\(gone.id).wrdoc.json")
        let goneCopyURL = archive.appending(path: "Gone_\(gone.id).wrdoc.json")
        try FileManager.default.copyItem(at: goneURL, to: goneCopyURL)
        let written = try #require(try goneURL.resourceValues(forKeys: [.contentModificationDateKey]).contentModificationDate)
        try FileManager.default.setAttributes([.modificationDate: written], ofItemAtPath: goneCopyURL.path(percentEncoded: false))

        try await store.deleteDocument(id: copied.id)
        try await store.deleteDocument(id: gone.id)
        let result = try await engine.sync(with: folder)

        // The copy changed from outside is loaded back; every untouched copy is removed.
        #expect(result.loadedDocuments == 1)
        #expect(try await store.document(id: copied.id).title == "Copied")
        #expect(try store.storedDocument(id: gone.id)?.deleted ?? true)
        #expect(try fileNames() == ["writeopia_config_file.json", "archive"])
        #expect(try fileNames(in: archive) == ["Copied_\(copied.id).wrdoc.json"])
    }

    @Test func lastSaveComesFromTheConfigFile() async throws {
        let store = makeStore()
        let engine = sync(store)
        #expect(engine.lastSave(in: folder) == nil)

        let before = Date()
        try await engine.save(to: folder)

        let lastSave = try #require(engine.lastSave(in: folder))
        #expect(lastSave >= Date(millis: before.millis))
    }
}
