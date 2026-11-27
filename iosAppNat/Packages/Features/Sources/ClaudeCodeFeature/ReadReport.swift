import Foundation

/// A document to read whole: `markdown` is its content, written by the app.
public struct ReadDocument: Equatable, Sendable {
    public let id: String
    public let title: String
    public let parentId: String?
    public let markdown: String

    public init(id: String, title: String, parentId: String?, markdown: String) {
        self.id = id
        self.title = title
        self.parentId = parentId
        self.markdown = markdown
    }
}

/// The answer of read_document. Mirrors `ReadReport.kt` word for word, the Markdown of the
/// content aside (each app writes its own). `ReadReportTests.swift` and `ReadReportTest.kt` hold
/// the same cases.
enum ReadReport {
    static let pageLength = 20_000

    static func describe(_ document: ReadDocument, folders: [LiveFolder], offset: Int) -> String {
        let title = document.title.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? "Untitled" : document.title
        let byId = Dictionary(folders.map { ($0.id, $0) }, uniquingKeysWith: { first, _ in first })
        var result = "\"\(title)\" (document_id: \(document.id)), in \(SearchReport.place(document.parentId, byId))."

        let text = Array(document.markdown.trimmingCharacters(in: .whitespacesAndNewlines))
        let from = max(offset, 0)
        if text.isEmpty {
            result += "\n\nContent: empty."
        } else if from >= text.count {
            result += "\n\nContent: nothing from offset \(from): the document has \(text.count) characters."
        } else if from == 0 && text.count <= pageLength {
            result += "\n\nContent (Markdown):\n" + String(text)
        } else {
            let to = pageEnd(text, from: from)
            result += "\n\nContent (Markdown), characters \(from) to \(to) of \(text.count):\n"
            result += trimmedEnd(text[from..<to])
            if to < text.count {
                result += "\n\nThe document goes on: call \(McpHandler.read) with this document_id and offset \(to) " +
                    "to read the next part."
            }
        }
        return result
    }

    /// Where the part from `from` ends: `pageLength` characters on, back at a line break when one is near.
    private static func pageEnd(_ text: [Character], from: Int) -> Int {
        let end = min(from + pageLength, text.count)
        if end == text.count { return end }
        var index = end - 1
        while index > from + pageLength / 2 {
            if text[index] == "\n" { return index + 1 }
            index -= 1
        }
        return end
    }

    private static func trimmedEnd(_ part: ArraySlice<Character>) -> String {
        var end = part.endIndex
        while end > part.startIndex, part[end - 1].isWhitespace { end -= 1 }
        return String(part[part.startIndex..<end])
    }
}
