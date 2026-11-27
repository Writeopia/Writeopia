import Foundation
import Testing
@testable import ClaudeCodeFeature

/// The same cases as `ReadReportTest.kt`: both apps answer read_document word for word the same.
@Suite struct ReadReportTests {
    let folders = [
        LiveFolder(id: "f-work", name: "Work", parentId: "root"),
        LiveFolder(id: "f-fin", name: "Finance", parentId: "f-work"),
    ]

    @Test func aShortDocumentComesWhole() {
        #expect(
            ReadReport.describe(
                ReadDocument(id: "d1", title: "Q3 Budget", parentId: "f-fin", markdown: "\n# Q3 Budget\n\n- Rent\n- Food\n"),
                folders: folders,
                offset: 0
            ) == "\"Q3 Budget\" (document_id: d1), in Work / Finance (folder_id: f-fin).\n\n" +
                "Content (Markdown):\n# Q3 Budget\n\n- Rent\n- Food"
        )
    }

    @Test func anEmptyDocumentAtTheTopLevel() {
        #expect(
            ReadReport.describe(ReadDocument(id: "d2", title: " ", parentId: "root", markdown: "  "), folders: folders, offset: 0)
                == "\"Untitled\" (document_id: d2), in the top level.\n\nContent: empty."
        )
    }

    @Test func aLongDocumentComesInPartsCutAtLineBreaks() {
        // 30 lines of 1,000 characters (999 + the line break).
        let markdown = (1...30).map { line in
            let start = "\(line)"
            return start + String(repeating: "x", count: 999 - start.count)
        }.joined(separator: "\n")
        let document = ReadDocument(id: "d3", title: "Long", parentId: nil, markdown: markdown)

        let first = ReadReport.describe(document, folders: folders, offset: 0)
        #expect(first.hasPrefix("\"Long\" (document_id: d3), in the top level.\n\nContent (Markdown), characters 0 to 20000 of 29999:\n1xxx"))
        #expect(first.hasSuffix("\n\nThe document goes on: call read_document with this document_id and offset 20000 to read the next part."))

        let second = ReadReport.describe(document, folders: folders, offset: 20_000)
        #expect(second.contains("Content (Markdown), characters 20000 to 29999 of 29999:\n21xxx"))
        #expect(!second.contains("goes on"))

        #expect(
            ReadReport.describe(document, folders: folders, offset: 40_000) ==
                "\"Long\" (document_id: d3), in the top level.\n\nContent: nothing from offset 40000: the document has 29999 characters."
        )
    }

    @Test func theToolReadsThroughTheApp() async {
        final class Reading: LiveDocumentsWriting {
            func create(title: String, markdown: String?, folderId: String?) async -> LiveOutcome { .finished }
            func append(documentId: String?, markdown: String) async -> LiveOutcome { .finished }
            func createFolder(name: String, parentId: String?) async -> LiveOutcome { .finished }
            func listFolders() async -> LiveOutcome { .finished }
            func finish() async -> LiveOutcome { .finished }
            func setHeaderImage(documentId: String?, description: String?) async -> LiveOutcome { .finished }
            func listTags() async -> LiveOutcome { .finished }
            func currentPlace(includeContent: Bool) async -> LiveOutcome { .finished }
            func addTags(documentId: String?, names: [String]) async -> LiveOutcome { .finished }
            func search(query: String, limit: Int) async -> LiveOutcome { .finished }
            func read(documentId: String, offset: Int) async -> LiveOutcome {
                guard documentId == "d1" else { return .failed("no document") }
                return .read(ReadReport.describe(
                    ReadDocument(id: "d1", title: "Trip", parentId: "root", markdown: "- Book the trip"),
                    folders: [],
                    offset: offset
                ))
            }
        }
        let handler = McpHandler(documents: Reading(), serverVersion: "t")

        func call(_ arguments: String) async -> [String: Any] {
            let body = #"{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"read_document","arguments":"# +
                arguments + "}}"
            let data = await handler.handle(Data(body.utf8)) ?? Data()
            let answer = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any]
            return answer?["result"] as? [String: Any] ?? [:]
        }

        let read = await call(#"{"document_id":"d1"}"#)
        #expect(read["isError"] as? Bool == false)
        #expect(
            ((read["content"] as? [[String: Any]])?.first?["text"] as? String)
                == "\"Trip\" (document_id: d1), in the top level.\n\nContent (Markdown):\n- Book the trip"
        )
        #expect(await call(#"{"document_id":"nope"}"#)["isError"] as? Bool == true)
        #expect(await call("{}")["isError"] as? Bool == true)
    }
}
