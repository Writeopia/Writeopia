import Foundation
import Testing
@testable import ClaudeCodeFeature

private final class FakeDocuments: LiveDocumentsWriting {
    var appended: [(String?, String)] = []

    var folders: [(String, String?)] = []

    func create(title: String, markdown: String?, folderId: String?) async -> LiveOutcome {
        .written(documentId: "doc1", title: title, blocks: 2)
    }

    func finish() async -> LiveOutcome { .finished }

    func setHeaderImage(documentId: String?, description: String?) async -> LiveOutcome {
        .headerImageSet(documentId: documentId ?? "doc1", title: "Trip", url: "https://cdn/p.png")
    }

    func currentPlace(includeContent: Bool) async -> LiveOutcome { .place(includeContent ? "with content" : "without") }

    func listTags() async -> LiveOutcome {
        .tags([LiveTag(id: "f", name: "Food"), LiveTag(id: "t", name: "Travel", description: "Trips and places")])
    }

    func addTags(documentId: String?, names: [String]) async -> LiveOutcome {
        .tagged(
            documentId: "doc",
            title: "Lisbon",
            added: [LiveTagged(name: "Travel", created: false), LiveTagged(name: "Portugal", created: true)],
            already: ["Food"]
        )
    }

    func listFolders() async -> LiveOutcome {
        .folders([LiveFolder(id: "work", name: "Work", parentId: "root")], currentFolderId: "work")
    }

    func createFolder(name: String, parentId: String?) async -> LiveOutcome {
        folders.append((name, parentId))
        return .folderCreated(folderId: "f1", name: name, parentId: parentId ?? "root")
    }

    func append(documentId: String?, markdown: String) async -> LiveOutcome {
        appended.append((documentId, markdown))
        return documentId == nil ? .failed("No document is open in Writeopia.") : .written(documentId: documentId!, title: "T", blocks: 1)
    }
}

private func json(_ data: Data?) -> [String: Any] {
    (try? JSONSerialization.jsonObject(with: data ?? Data())) as? [String: Any] ?? [:]
}

@Suite struct McpHandlerTests {
    private let documents = FakeDocuments()
    private var handler: McpHandler { McpHandler(documents: documents, serverVersion: "test") }

    private func call(_ body: String) async -> [String: Any] {
        json(await handler.handle(Data(body.utf8)))
    }

    @Test func initializeAnswersTheVersionAskedForWhenKnown() async {
        let result = await call(#"{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-03-26"}}"#)["result"] as? [String: Any]

        #expect(result?["protocolVersion"] as? String == "2025-03-26")
        #expect((result?["serverInfo"] as? [String: Any])?["name"] as? String == "writeopia")
    }

    @Test func initializeFallsBackToTheNewestVersion() async {
        let result = await call(#"{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"1999-01-01"}}"#)["result"] as? [String: Any]

        #expect(result?["protocolVersion"] as? String == McpHandler.supportedVersions[0])
    }

    @Test func notificationsGetNoAnswer() async {
        #expect(await handler.handle(Data(#"{"jsonrpc":"2.0","method":"notifications/initialized"}"#.utf8)) == nil)
    }

    @Test func listsTheTwoTools() async {
        let tools = (await call(#"{"jsonrpc":"2.0","id":"a","method":"tools/list"}"#)["result"] as? [String: Any])?["tools"] as? [[String: Any]]

        #expect(tools?.compactMap { $0["name"] as? String } == ["create_document", "append_to_document", "set_header_image", "get_current_place", "list_tags", "add_tags", "finish_writing", "list_folders", "create_folder"])
    }

    @Test func createAnswersWithTheDocumentId() async {
        let result = await call(
            #"{"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"create_document","arguments":{"title":"Trip","markdown":"Hi"}}}"#
        )["result"] as? [String: Any]
        let text = ((result?["content"] as? [[String: Any]])?.first?["text"] as? String) ?? ""

        #expect(result?["isError"] as? Bool == false)
        #expect(text == #"Wrote 2 blocks to "Trip" (document_id: doc1). To add more, call append_to_document with this document_id."#)
    }

    @Test func createFolderAnswersWithTheFolderId() async {
        let result = await call(
            #"{"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"create_folder","arguments":{"name":"Trips","parent_folder_id":"p"}}}"#
        )["result"] as? [String: Any]
        let text = ((result?["content"] as? [[String: Any]])?.first?["text"] as? String) ?? ""

        #expect(documents.folders.count == 1)
        #expect(documents.folders.first?.0 == "Trips")
        #expect(documents.folders.first?.1 == "p")
        #expect(text.hasPrefix(#"Created the folder "Trips" (folder_id: f1) inside p."#))
    }

    @Test func failuresAndMissingArgumentsAreToolErrors() async {
        let failed = await call(#"{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"append_to_document","arguments":{"markdown":"Hi"}}}"#)
        let missing = await call(#"{"jsonrpc":"2.0","id":4,"method":"tools/call","params":{"name":"create_document","arguments":{}}}"#)

        #expect((failed["result"] as? [String: Any])?["isError"] as? Bool == true)
        #expect((missing["result"] as? [String: Any])?["isError"] as? Bool == true)
    }

    @Test func unknownMethodsAndBadBodiesAreJsonRpcErrors() async {
        #expect((await call(#"{"jsonrpc":"2.0","id":5,"method":"resources/list"}"#)["error"] as? [String: Any])?["code"] as? Int == -32601)
        #expect((await call("not json")["error"] as? [String: Any])?["code"] as? Int == -32700)
    }
}

@Suite struct HTTPRequestTests {
    @Test func readsAWholeRequest() {
        let raw = "POST /mcp?x=1 HTTP/1.1\r\nHost: 127.0.0.1\r\nContent-Length: 4\r\nAuthorization: Bearer abc\r\n\r\nbody"

        #expect(HTTPRequest.parse(Data(raw.utf8)) == .request(HTTPRequest(
            method: "POST",
            path: "/mcp",
            headers: ["host": "127.0.0.1", "content-length": "4", "authorization": "Bearer abc"],
            body: Data("body".utf8)
        )))
    }

    @Test func waitsForTheRestOfTheBody() {
        #expect(HTTPRequest.parse(Data("POST /mcp HTTP/1.1\r\nContent-Length: 10\r\n\r\nabc".utf8)) == .incomplete)
        #expect(HTTPRequest.parse(Data("POST /mcp HTTP/1.1\r\nContent-Len".utf8)) == .incomplete)
    }
}

@Suite struct ClaudeCodeServerTests {
    private func server() -> ClaudeCodeServer {
        let defaults = UserDefaults(suiteName: "ClaudeCodeServerTests-\(UUID().uuidString)")!
        let server = ClaudeCodeServer(defaults: defaults)
        server.state = .running
        return server
    }

    private func request(_ headers: [String: String], method: String = "POST", path: String = "/mcp") -> HTTPRequest {
        HTTPRequest(method: method, path: path, headers: headers, body: Data(#"{"jsonrpc":"2.0","id":1,"method":"ping"}"#.utf8))
    }

    private func status(_ data: Data) -> String {
        String(decoding: data, as: UTF8.self).components(separatedBy: "\r\n")[0]
    }

    private let local = ["host": "127.0.0.1:47615"]

    @Test func refusesWebPagesAndOtherHostsWithoutAnySecret() async {
        let server = server()

        #expect(status(await server.respond(to: request(local.merging(["origin": "https://evil.example"]) { $1 }))) == "HTTP/1.1 403 Forbidden")
        // A site pointing its own name at 127.0.0.1 (DNS rebinding) sends its name.
        #expect(status(await server.respond(to: request(["host": "evil.example:47615"]))) == "HTTP/1.1 403 Forbidden")
        #expect(status(await server.respond(to: request([:]))) == "HTTP/1.1 403 Forbidden")
        #expect(status(await server.respond(to: request(local, method: "GET"))) == "HTTP/1.1 405 Method Not Allowed")
        #expect(status(await server.respond(to: request(local, path: "/other"))) == "HTTP/1.1 404 Not Found")
    }

    @Test func aServerTurnedOffAnswersNothing() async {
        let server = server()
        server.state = .off

        #expect(status(await server.respond(to: request(local))) == "HTTP/1.1 503 Service Unavailable")
    }

    @Test func localHostsAndOrigins() {
        #expect(ClaudeCodeServer.isLocalHost("127.0.0.1:47615"))
        #expect(ClaudeCodeServer.isLocalHost("localhost:47615"))
        #expect(ClaudeCodeServer.isLocalHost("[::1]:47615"))
        #expect(!ClaudeCodeServer.isLocalHost("127.0.0.1"))
        #expect(!ClaudeCodeServer.isLocalHost("evil.example:47615"))
        #expect(ClaudeCodeServer.isLocalOrigin("http://localhost:3000"))
        #expect(!ClaudeCodeServer.isLocalOrigin("http://127.0.0.1.evil.com"))
    }
}

@Suite struct StoppedTests {
    @Test func aStoppedCallTellsClaudeToStop() async {
        final class Stopped: LiveDocumentsWriting {
            func create(title: String, markdown: String?, folderId: String?) async -> LiveOutcome { .stopped }
            func append(documentId: String?, markdown: String) async -> LiveOutcome { .stopped }
            func createFolder(name: String, parentId: String?) async -> LiveOutcome { .stopped }
            func listFolders() async -> LiveOutcome { .stopped }
            func finish() async -> LiveOutcome { .finished }
            func setHeaderImage(documentId: String?, description: String?) async -> LiveOutcome { .stopped }
            func listTags() async -> LiveOutcome { .stopped }
            func currentPlace(includeContent: Bool) async -> LiveOutcome { .stopped }
            func addTags(documentId: String?, names: [String]) async -> LiveOutcome { .stopped }
        }
        let answer = json(await McpHandler(documents: Stopped(), serverVersion: "t").handle(Data(
            #"{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"append_to_document","arguments":{"markdown":"Hi"}}}"#.utf8
        )))
        let result = answer["result"] as? [String: Any]
        let text = ((result?["content"] as? [[String: Any]])?.first?["text"] as? String) ?? ""

        #expect(result?["isError"] as? Bool == true)
        #expect(text.contains("pressed Stop"))
    }
}

/// The same cases as `LiveFoldersTest.kt`: both apps answer list_folders word for word.
@Suite struct FolderTreeTests {
    @Test func sortsSiblingsByNameAndIndentsChildren() {
        let tree = FolderTree.describe([
            LiveFolder(id: "work", name: "Work", parentId: "root"),
            LiveFolder(id: "b", name: "beta", parentId: "work"),
            LiveFolder(id: "a", name: "Alpha", parentId: "work"),
            LiveFolder(id: "orphan", name: "Lost", parentId: "gone"),
        ], currentFolderId: "root")

        #expect(tree == """
        Folders in Writeopia (4):
        - Lost (folder_id: orphan)
        - Work (folder_id: work)
          - Alpha (folder_id: a)
          - beta (folder_id: b)

        The app has the top level open: new documents and folders go there unless you pass a folder_id.
        """)
    }

    @Test func noFoldersYet() {
        #expect(FolderTree.describe([], currentFolderId: "root")
            == "Writeopia has no folders yet: everything is in the top level. Make one with create_folder.")
    }

    @Test func listFoldersAnswersTheTreeWithTheOpenFolder() async {
        let answer = json(await McpHandler(documents: FakeDocuments(), serverVersion: "t").handle(Data(
            #"{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"list_folders","arguments":{}}}"#.utf8
        )))
        let text = (((answer["result"] as? [String: Any])?["content"] as? [[String: Any]])?.first?["text"] as? String) ?? ""

        #expect(text.hasSuffix(#"The app has "Work" (folder_id: work) open: new documents and folders go there unless you pass a folder_id."#))
    }
}

@Suite struct FinishTests {
    @Test func finishWritingAnswersThatAiModeEnded() async {
        let answer = json(await McpHandler(documents: FakeDocuments(), serverVersion: "t").handle(Data(
            #"{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"finish_writing","arguments":{}}}"#.utf8
        )))
        let result = answer["result"] as? [String: Any]
        let text = ((result?["content"] as? [[String: Any]])?.first?["text"] as? String) ?? ""

        #expect(result?["isError"] as? Bool == false)
        #expect(text == "Writeopia left AI mode: the person can edit the documents again.")
    }
}

@Suite struct HeaderImageToolTests {
    @Test func setHeaderImageAnswersWithTheDocumentAndPicture() async {
        let answer = json(await McpHandler(documents: FakeDocuments(), serverVersion: "t").handle(Data(
            #"{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"set_header_image","arguments":{"document_id":"d9"}}}"#.utf8
        )))
        let text = (((answer["result"] as? [String: Any])?["content"] as? [[String: Any]])?.first?["text"] as? String) ?? ""

        #expect(text == #"Put a header picture in "Trip" (document_id: d9): https://cdn/p.png"#)
    }
}

/// The same answers as `TagsTest.kt`, word for word.
@Suite struct TagToolTests {
    private func text(_ tool: String) async -> String {
        let answer = json(await McpHandler(documents: FakeDocuments(), serverVersion: "t").handle(Data(
            #"{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"\#(tool)","arguments":{"tags":["x"]}}}"#.utf8
        )))
        return (((answer["result"] as? [String: Any])?["content"] as? [[String: Any]])?.first?["text"] as? String) ?? ""
    }

    @Test func addTagsTellsWhatWasAddedMadeAndAlreadyThere() async {
        #expect(await text("add_tags") == #"Tagged "Lisbon" (document_id: doc) with: Travel, Portugal (new). It already had: Food."#)
    }

    @Test func listTagsShowsTheCatalogueWithDescriptions() async {
        #expect(await text("list_tags") == "Tags of the workspace (2):\n- Food\n- Travel: Trips and places")
    }
}

/// The same report cases as `CurrentPlaceTest.kt`, word for word.
@Suite struct CurrentPlaceTests {
    private let trips = LiveFolder(id: "f1", name: "Trips", parentId: "root")
    private let year = LiveFolder(id: "f2", name: "2026", parentId: "f1")

    @Test func aDocumentWithItsFolderPathTagsAndContent() {
        let text = PlaceReport.describe(
            document: OpenDocument(id: "d1", title: "Lisbon", folderPath: [trips, year], tags: ["Food", "Travel"], isLocked: false, hasHeaderPicture: true),
            currentFolder: year,
            change: .edited(added: 2, changed: 1, removed: 0),
            content: "# Lisbon\n\nDay one\n"
        )

        #expect(text == "Writeopia shows the document \"Lisbon\" (document_id: d1) in Trips / 2026 (folder_id: f2).\n" +
            "Tags: Food, Travel. Locked: no. Header picture: yes.\n\n" +
            "Since your last call, the person changed this document: 2 blocks added, 1 changed, 0 removed.\n\n" +
            "Content (Markdown):\n# Lisbon\n\nDay one")
    }

    @Test func noDocumentOpenAtTheTopLevelOrInAFolderAfterLeavingOne() {
        #expect(PlaceReport.describe(document: nil, currentFolder: nil, change: nil, content: nil)
            == "No document is open in Writeopia. The app has the top level open.")
        #expect(PlaceReport.describe(document: nil, currentFolder: trips, change: .moved(fromId: "d1", fromTitle: "Lisbon"), content: nil)
            == "No document is open in Writeopia. The app has \"Trips\" (folder_id: f1) open.\n\n" +
            "Since your last call, the person left \"Lisbon\" (document_id: d1).")
    }

    @Test func aDocumentAtTheTopLevelUnchangedEmptyAndALongOneCut() {
        let doc = OpenDocument(id: "d1", title: "Notes", folderPath: [], tags: [], isLocked: true, hasHeaderPicture: false)

        #expect(PlaceReport.describe(document: doc, currentFolder: nil, change: .unchanged, content: "  ")
            == "Writeopia shows the document \"Notes\" (document_id: d1) in the top level.\n" +
            "Tags: none. Locked: yes. Header picture: no.\n\n" +
            "Since your last call, nothing changed here.\n\nContent: empty.")
        let long = PlaceReport.describe(document: doc, currentFolder: nil, change: nil, content: String(repeating: "a", count: PlaceReport.contentLimit + 5))
        #expect(long.hasSuffix("\n…(cut: 5 more characters)"))
    }

    @Test func changesCountBlocksAddedChangedAndRemovedOrAMove() {
        let before = DocumentSnapshot(documentId: "d1", title: "Lisbon", blocks: ["a": "1", "b": "2", "c": "3"])

        #expect(before.change(to: DocumentSnapshot(documentId: "d1", title: "Lisbon", blocks: ["a": "1", "b": "2!", "d": "4"]))
            == .edited(added: 1, changed: 1, removed: 1))
        #expect(before.change(to: before) == .unchanged)
        #expect(before.change(to: DocumentSnapshot(documentId: "d2", title: "Other", blocks: [:])) == .moved(fromId: "d1", fromTitle: "Lisbon"))
        #expect(before.change(to: nil) == .moved(fromId: "d1", fromTitle: "Lisbon"))
    }

    @Test func getCurrentPlacePassesIncludeContent() async {
        let handler = McpHandler(documents: FakeDocuments(), serverVersion: "t")
        func text(_ arguments: String) async -> String {
            let answer = json(await handler.handle(Data(
                #"{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"get_current_place","arguments":\#(arguments)}}"#.utf8
            )))
            return (((answer["result"] as? [String: Any])?["content"] as? [[String: Any]])?.first?["text"] as? String) ?? ""
        }

        #expect(await text("{}") == "with content")
        #expect(await text(#"{"include_content":false}"#) == "without")
    }
}
