import Foundation
import Testing
@testable import ClaudeCodeFeature

/// The same cases as `SearchReportTest.kt`: both apps answer search_documents word for word the same.
@Suite struct SearchReportTests {
    let work = LiveFolder(id: "f-work", name: "Work", parentId: "root")
    let finance = LiveFolder(id: "f-fin", name: "Finance", parentId: "f-work")
    let budgets = LiveFolder(id: "f-bud", name: "Budgets 2026", parentId: "root")
    var folders: [LiveFolder] { [work, finance, budgets] }

    @Test func tokensAreTheLowercasedWordsOnce() {
        #expect(SearchReport.tokens("  Budget, 2026 — plan budget!") == ["budget", "2026", "plan"])
        #expect(SearchReport.tokens(" ?! ").isEmpty)
    }

    @Test func shortTextIsKeptWholeWithTheWordsInBold() {
        #expect(
            SearchReport.excerpt("The budget  for the next\nplanning cycle.", tokens: ["budget", "plan"])
                == "The **budget** for the next **planning** cycle."
        )
    }

    @Test func onlyTheStartOfAWordMatches() {
        #expect(SearchReport.excerpt("Unplanned plans", tokens: ["plan"]) == "Unplanned **plans**")
    }

    @Test func aMatchLongerThanTheExcerptShowsItsStart() {
        #expect(
            SearchReport.excerpt("a intro budget" + String(repeating: "x", count: 200) + " end", tokens: ["budget"])
                == "a intro **budget" + String(repeating: "x", count: 146) + "**…"
        )
    }

    @Test func longTextIsCutAroundTheFirstMatchAtWordBoundaries() {
        let text = (1...40).map { "word\($0)" }.joined(separator: " ") + " budget " +
            (41...80).map { "word\($0)" }.joined(separator: " ")
        #expect(
            SearchReport.excerpt(text, tokens: ["budget"]) ==
                "…word33 word34 word35 word36 word37 word38 word39 word40 **budget** word41 word42 word43 word44 " +
                "word45 word46 word47 word48 word49 word50 word51 word52 word53 word54…"
        )
    }

    @Test func describesDocumentsWithTheirFolderAndPassage() {
        let matches = [
            SearchMatch(documentId: "d1", title: "Q3 Budget", parentId: "f-fin", text: nil),
            SearchMatch(documentId: "d2", title: "Ideas", parentId: "root", text: "We should budget the trip."),
            SearchMatch(documentId: "d1", title: "Q3 Budget", parentId: "f-fin", text: "duplicate"),
            SearchMatch(documentId: "d3", title: "", parentId: nil, text: "Budget review"),
        ]

        #expect(SearchReport.describe(query: " budget ", matches: matches, folders: folders, limit: 10) == """
            Documents matching "budget", best first (3):

            1. Q3 Budget (document_id: d1)
               In: Work / Finance (folder_id: f-fin)
               Found by its title.

            2. Ideas (document_id: d2)
               In: the top level
               "We should **budget** the trip."

            3. Untitled (document_id: d3)
               In: the top level
               "**Budget** review"

            Folders matching "budget":
            - Budgets 2026 (folder_id: f-bud), in the top level

            Read a whole document with read_document. Pass a document_id to append_to_document, add_tags or set_header_image, and a folder_id to create_document.
            """)
    }

    @Test func tellsWhenTheListIsCut() {
        let matches = (1...4).map { SearchMatch(documentId: "d\($0)", title: "Plan \($0)", parentId: "f-work", text: nil) }

        let text = SearchReport.describe(query: "plan", matches: matches, folders: folders, limit: 2)

        #expect(text.hasPrefix("Documents matching \"plan\", best first (4):\n\n1. Plan 1 (document_id: d1)"))
        #expect(text.contains("\n\n2. Plan 2 (document_id: d2)\n   In: Work (folder_id: f-work)\n   Found by its title."))
        #expect(!text.contains("Plan 3"))
        #expect(text.contains("\n\nShowing the first 2 of 4: add words to narrow the search, or raise limit (up to 25)."))
    }

    @Test func onlyFoldersMatch() {
        #expect(SearchReport.describe(query: "finance", matches: [], folders: folders, limit: 10) == """
            No document matches "finance".

            Folders matching "finance":
            - Finance (folder_id: f-fin), in Work (folder_id: f-work)

            Read a whole document with read_document. Pass a document_id to append_to_document, add_tags or set_header_image, and a folder_id to create_document.
            """)
    }

    @Test func nothingMatches() {
        #expect(
            SearchReport.describe(query: "zebra", matches: [], folders: folders, limit: 10) ==
                "Nothing in the workspace matches \"zebra\". The search looks for every word at the start of a word " +
                "(\"plan\" finds \"planning\"), in the titles and the text of the documents. Try fewer or other words."
        )
    }

    @Test func theToolSearchesThroughTheApp() async {
        final class Searching: LiveDocumentsWriting {
            var asked: [(String, Int)] = []
            func create(title: String, markdown: String?, folderId: String?) async -> LiveOutcome { .finished }
            func append(documentId: String?, markdown: String) async -> LiveOutcome { .finished }
            func createFolder(name: String, parentId: String?) async -> LiveOutcome { .finished }
            func listFolders() async -> LiveOutcome { .finished }
            func finish() async -> LiveOutcome { .finished }
            func setHeaderImage(documentId: String?, description: String?) async -> LiveOutcome { .finished }
            func listTags() async -> LiveOutcome { .finished }
            func currentPlace(includeContent: Bool) async -> LiveOutcome { .finished }
            func addTags(documentId: String?, names: [String]) async -> LiveOutcome { .finished }
            func read(documentId: String, offset: Int) async -> LiveOutcome { .finished }
            func search(query: String, limit: Int) async -> LiveOutcome {
                asked.append((query, limit))
                return .found(SearchReport.describe(
                    query: query,
                    matches: [SearchMatch(documentId: "d1", title: "Trip", parentId: "root", text: "Book the trip")],
                    folders: [],
                    limit: limit
                ))
            }
        }
        let documents = Searching()
        let handler = McpHandler(documents: documents, serverVersion: "t")

        func call(_ arguments: String) async -> [String: Any] {
            let body = #"{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"search_documents","arguments":"# +
                arguments + "}}"
            let data = await handler.handle(Data(body.utf8)) ?? Data()
            let answer = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any]
            return answer?["result"] as? [String: Any] ?? [:]
        }

        let found = await call(#"{"query":"trip","limit":3}"#)
        let text = ((found["content"] as? [[String: Any]])?.first?["text"] as? String) ?? ""
        #expect(documents.asked.first?.0 == "trip")
        #expect(documents.asked.first?.1 == 3)
        #expect(found["isError"] as? Bool == false)
        #expect(text.contains("1. Trip (document_id: d1)\n   In: the top level\n   \"Book the **trip**\""))

        #expect(await call("{}")["isError"] as? Bool == true)
    }
}
