import Foundation
import Testing
@testable import WrData
import WrModels
import WrNetwork
import WrStorage

@Suite struct LocalDocumentsRepositoryTests {
    let directory = FileManager.default.temporaryDirectory
        .appending(path: "wr tests \(UUID().uuidString)", directoryHint: .isDirectory)

    @Test func seedsTheTutorialsOnlyOnce() async throws {
        let repository = LocalDocumentsRepository(directory: directory)

        _ = try await repository.folderContents(folderId: Folder.rootId)
        let contents = try await repository.folderContents(folderId: Folder.rootId)

        #expect(contents.documents.count == 5)
        #expect(contents.documents.map(\.title) == ["Welcome!", "Using AI", "Saving Notes", "Using Commands", "Video Tutorial"])
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
        #expect(try await repository.search(query: "free place").map(\.title) == ["Welcome!"])
        #expect(try await repository.search(query: "  ").isEmpty)
    }
}

@Suite struct MoveTests {
    let directory = FileManager.default.temporaryDirectory
        .appending(path: "wr move \(UUID().uuidString)", directoryHint: .isDirectory)

    @Test func movesDocumentIntoFolder() async throws {
        let repository = LocalDocumentsRepository(directory: directory)
        let folder = try await repository.createFolder(title: "Ideas", parentId: Folder.rootId)
        let document = try await repository.createDocument(title: "Draft", parentId: Folder.rootId)

        try await repository.moveDocument(id: document.id, toFolder: folder.id)

        let root = try await repository.folderContents(folderId: Folder.rootId)
        #expect(!root.documents.contains { $0.id == document.id })
        #expect(root.folders.first { $0.id == folder.id }?.itemCount == 1)
        #expect(try await repository.folderContents(folderId: folder.id).documents.map(\.id) == [document.id])
    }

    @Test func movesFolderAndRejectsCycles() async throws {
        let repository = LocalDocumentsRepository(directory: directory)
        let parent = try await repository.createFolder(title: "Parent", parentId: Folder.rootId)
        let child = try await repository.createFolder(title: "Child", parentId: Folder.rootId)

        try await repository.moveFolder(id: child.id, toFolder: parent.id)
        #expect(try await repository.folderContents(folderId: parent.id).folders.map(\.id) == [child.id])

        await #expect(throws: MoveError.folderIntoItself) {
            try await repository.moveFolder(id: parent.id, toFolder: child.id)
        }
        await #expect(throws: MoveError.folderIntoItself) {
            try await repository.moveFolder(id: parent.id, toFolder: parent.id)
        }
        #expect(try await repository.folderContents(folderId: Folder.rootId).folders.map(\.id) == [parent.id])
    }

    @Test func remoteMoveCallsEndpoints() async throws {
        let transport = RecordingTransport()
        let client = APIClient(transport: transport, tokenStore: InMemoryTokenStore(accessToken: "a"), baseURL: URL(string: "https://x.io")!)
        let repository = RemoteDocumentsRepository(client: client, workspaceId: "w1")

        try await repository.moveDocument(id: "d1", toFolder: "f1")
        try await repository.moveFolder(id: "f2", toFolder: "f1")

        #expect(transport.requests.map { $0.url!.path() } == [
            "/api/docs/workspace/w1/document/d1/move",
            "/api/docs/workspace/w1/folder/f2/move",
        ])
        #expect(transport.requests.allSatisfy { $0.httpMethod == "POST" })
        #expect(String(data: transport.requests[0].httpBody!, encoding: .utf8) == #"{"targetParentId":"f1"}"#)
    }
}

final class RecordingTransport: HTTPTransport {
    private(set) var requests: [URLRequest] = []

    func data(for request: URLRequest) async throws -> (Data, URLResponse) {
        requests.append(request)
        return (Data("Document moved successfully".utf8), HTTPURLResponse(url: request.url!, statusCode: 200, httpVersion: nil, headerFields: nil)!)
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
        let allowed: Set = [
            "id", "title", "workspaceId", "content", "createdAt", "lastUpdatedAt", "isFavorite", "parentId",
            "lastSyncedAt", "isLocked", "published", "deleted", "icon",
        ]
        #expect(Set(object.keys).isSubset(of: allowed))

        let step = (object["content"] as! [[String: Any]])[0]
        let allowedStep: Set = ["id", "type", "parentId", "url", "path", "text", "checked", "steps", "tags",
                                "spans", "decoration", "position", "documentLink", "lastUpdatedAt"]
        #expect(Set(step.keys).isSubset(of: allowedStep))
    }
}
