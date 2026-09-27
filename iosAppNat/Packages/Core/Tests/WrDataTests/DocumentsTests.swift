import Foundation
import Testing
@testable import WrData
import WrModels

@Suite struct LocalDocumentsRepositoryTests {
    let directory = FileManager.default.temporaryDirectory
        .appending(path: "wr tests \(UUID().uuidString)", directoryHint: .isDirectory)

    @Test func seedsWelcomeDocumentOnlyOnce() async throws {
        let repository = LocalDocumentsRepository(directory: directory)

        _ = try await repository.folderContents(folderId: Folder.rootId)
        let contents = try await repository.folderContents(folderId: Folder.rootId)

        #expect(contents.documents.count == 1)
        #expect(contents.documents[0].title == "Welcome to Writeopia")
    }

    @Test func createsFoldersAndDocumentsInsideThem() async throws {
        let repository = LocalDocumentsRepository(directory: directory)

        let folder = try await repository.createFolder(title: "Ideas", parentId: Folder.rootId)
        let document = try await repository.createDocument(title: "Book / draft", parentId: folder.id)

        let root = try await repository.folderContents(folderId: Folder.rootId)
        #expect(root.folders.map(\.title) == ["Ideas"])
        #expect(root.folders[0].itemCount == 1)

        let inside = try await repository.folderContents(folderId: folder.id)
        #expect(inside.documents.map(\.id) == [document.id])

        let loaded = try await repository.document(id: document.id)
        #expect(loaded.content.first?.type == .title)
        #expect(loaded.content.first?.text == "Book / draft")
    }

    @Test func searchesTitleAndContent() async throws {
        let repository = LocalDocumentsRepository(directory: directory)
        _ = try await repository.createDocument(title: "Groceries", parentId: Folder.rootId)

        #expect(try await repository.search(query: "grocer").map(\.title) == ["Groceries"])
        #expect(try await repository.search(query: "private space").map(\.title) == ["Welcome to Writeopia"])
        #expect(try await repository.search(query: "  ").isEmpty)
    }
}

@Suite struct DecodingTests {
    @Test func decodesBackendFolderContents() throws {
        let json = #"""
        {
          "folders": [{"id":"f1","parentId":"root","title":"Work","createdAt":"2026-01-01T10:00:00Z",
                       "lastUpdatedAt":"2026-01-01T10:00:00Z","workspaceId":"w","favorite":false,"icon":null,"itemCount":3}],
          "documents": [{"id":"d1","title":"Plan","workspaceId":"w","createdAt":1700000000000,"lastUpdatedAt":1700000000000,
                         "isFavorite":true,"lastSyncedAt":null,"parentId":"root","isLocked":false,"icon":null,
                         "deleted":false,"published":false,
                         "content":[{"id":"s1","type":{"name":"title","number":11},"text":"Plan","position":0.0,
                                     "checked":false,"steps":[],"tags":[],"spans":[],"decoration":{"backgroundColor":null}},
                                    {"id":"s2","type":{"name":"message","number":0},"text":"Hello world","position":1.0,
                                     "spans":[{"start":0,"end":5,"span":"BOLD"}],"tags":[{"tag":"H2","position":0}]}]}]
        }
        """#

        let contents = try JSONDecoder().decode(FolderContents.self, from: Data(json.utf8))

        #expect(contents.folders[0].itemCount == 3)
        let document = contents.documents[0]
        #expect(document.isFavorite)
        #expect(document.bodySteps.map(\.id) == ["s2"])
        #expect(document.bodySteps[0].headingLevel == 2)
        #expect(document.preview == "Hello world")
    }

    @Test func encodedDocumentOnlyUsesKnownBackendKeys() throws {
        let document = WrDocument(
            id: "d",
            title: "T",
            workspaceId: "w",
            content: [StoryStep(type: .title, text: "T", position: 0)],
            parentId: "root"
        )

        let object = try JSONSerialization.jsonObject(with: JSONEncoder().encode(document)) as! [String: Any]
        let allowed: Set = ["id", "title", "workspaceId", "content", "createdAt", "lastUpdatedAt", "isFavorite", "parentId"]
        #expect(Set(object.keys).isSubset(of: allowed))

        let step = (object["content"] as! [[String: Any]])[0]
        let allowedStep: Set = ["id", "type", "parentId", "url", "path", "text", "checked", "steps", "tags",
                                "spans", "decoration", "position", "documentLink", "lastUpdatedAt"]
        #expect(Set(step.keys).isSubset(of: allowedStep))
    }
}
