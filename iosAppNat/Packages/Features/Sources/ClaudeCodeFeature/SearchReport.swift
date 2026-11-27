import Foundation

/// A document the search of the app found. `text` is the step the words were found in, when it
/// was found by them; nil when it was found by its title. `parentId` is its folder, nil when
/// unknown.
public struct SearchMatch: Equatable, Sendable {
    public let documentId: String
    public let title: String
    public let parentId: String?
    public let text: String?

    public init(documentId: String, title: String, parentId: String?, text: String?) {
        self.documentId = documentId
        self.title = title
        self.parentId = parentId
        self.text = text
    }
}

/// The answer of search_documents. Mirrors `SearchReport.kt` word for word: the same words of the
/// query, the same excerpt around them, the same text. `SearchReportTests.swift` and
/// `SearchReportTest.kt` hold the same cases.
enum SearchReport {
    static let defaultLimit = 10
    static let maxLimit = 25
    static let excerptLength = 160
    private static let excerptLead = 60
    private static let maxTokens = 8
    private static let root = "root"

    /// The words of `query`, lowercased, each looked for at the start of a word.
    static func tokens(_ query: String) -> [String] {
        let characters = Array(query)
        var seen = Set<String>()
        var tokens: [String] = []
        for (start, end) in words(characters) {
            let token = String(characters[start..<end]).lowercased()
            if seen.insert(token).inserted { tokens.append(token) }
        }
        return Array(tokens.prefix(maxTokens))
    }

    static func describe(query: String, matches: [SearchMatch], folders: [LiveFolder], limit: Int) -> String {
        let tokens = tokens(query)
        let byId = Dictionary(folders.map { ($0.id, $0) }, uniquingKeysWith: { first, _ in first })
        let shownLimit = min(max(limit, 1), maxLimit)
        var known = Set<String>()
        let documents = matches.filter { known.insert($0.documentId).inserted }
        let matchingFolders = folders
            .filter { matchesAll($0.name, tokens) }
            .sorted { $0.name.lowercased() < $1.name.lowercased() }
        let quoted = "\"\(query.trimmingCharacters(in: .whitespacesAndNewlines))\""

        if documents.isEmpty && matchingFolders.isEmpty {
            return "Nothing in the workspace matches \(quoted). The search looks for every word at the start " +
                "of a word (\"plan\" finds \"planning\"), in the titles and the text of the documents. Try " +
                "fewer or other words."
        }

        var text = ""
        if documents.isEmpty {
            text += "No document matches \(quoted)."
        } else {
            let shown = Array(documents.prefix(shownLimit))
            text += "Documents matching \(quoted), best first (\(documents.count)):"
            for (index, match) in shown.enumerated() {
                let title = match.title.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? "Untitled" : match.title
                text += "\n\n\(index + 1). \(title) (document_id: \(match.documentId))"
                text += "\n   In: \(place(match.parentId, byId))"
                if let found = match.text, !found.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                    text += "\n   \"\(excerpt(found, tokens: tokens))\""
                } else {
                    text += "\n   Found by its title."
                }
            }
            if documents.count > shown.count {
                text += "\n\nShowing the first \(shown.count) of \(documents.count): add words to narrow the " +
                    "search, or raise limit (up to \(maxLimit))."
            }
        }

        if !matchingFolders.isEmpty {
            text += "\n\nFolders matching \(quoted):"
            for folder in matchingFolders {
                text += "\n- \(folder.name) (folder_id: \(folder.id)), in \(place(folder.parentId, byId))"
            }
        }

        text += "\n\nRead a whole document with \(McpHandler.read). Pass a document_id to " +
            "\(McpHandler.append), \(McpHandler.addTags) or \(McpHandler.setHeaderImage), and a " +
            "folder_id to \(McpHandler.create)."
        return text
    }

    /// `text` on one line, cut to about `excerptLength` characters around the first word that
    /// matched (at word boundaries, "…" where cut), with the matching words in **bold**.
    static func excerpt(_ text: String, tokens: [String]) -> String {
        let clean = oneLine(text)
        let words = words(clean)
        let hits = words.filter { start, end in isHit(String(clean[start..<end]), tokens) }

        var from = 0
        var to = clean.count
        if clean.count > excerptLength {
            let first = hits.first?.0 ?? 0
            from = max(first - excerptLead, 0)
            if from > 0 { from = words.first { start, _ in start >= from }?.0 ?? from }
            to = min(from + excerptLength, clean.count)
            if to < clean.count {
                to = words.last { start, end in end <= to && start > from }?.1 ?? to
            }
        }

        var result = from > 0 ? "…" : ""
        var cursor = from
        for (start, end) in hits where start >= from && end <= to {
            result += String(clean[cursor..<start])
            result += "**" + String(clean[start..<end]) + "**"
            cursor = end
        }
        result += String(clean[cursor..<to])
        if to < clean.count { result += "…" }
        return result
    }

    /// Where a document or folder is: its folders from the top level down, or the top level.
    static func place(_ parentId: String?, _ byId: [String: LiveFolder]) -> String {
        var path: [LiveFolder] = []
        var id = parentId
        while let current = id, current != root, path.count < byId.count {
            guard let folder = byId[current] else { break }
            path.insert(folder, at: 0)
            id = folder.parentId
        }
        guard let last = path.last else { return "the top level" }
        return path.map(\.name).joined(separator: " / ") + " (folder_id: \(last.id))"
    }

    /// Every token starts a word of `text`.
    private static func matchesAll(_ text: String, _ tokens: [String]) -> Bool {
        guard !tokens.isEmpty else { return false }
        let characters = Array(text)
        let textWords = words(characters).map { start, end in String(characters[start..<end]).lowercased() }
        return tokens.allSatisfy { token in textWords.contains { $0.hasPrefix(token) } }
    }

    private static func isHit(_ word: String, _ tokens: [String]) -> Bool {
        let lower = word.lowercased()
        return tokens.contains { lower.hasPrefix($0) }
    }

    /// `text` with every run of whitespace (line breaks too) made one space, trimmed.
    private static func oneLine(_ text: String) -> [Character] {
        var result: [Character] = []
        var space = false
        for character in text {
            if character.isWhitespace {
                space = !result.isEmpty
            } else {
                if space { result.append(" ") }
                space = false
                result.append(character)
            }
        }
        return result
    }

    /// The start and end of each run of letters and digits.
    private static func words(_ text: [Character]) -> [(Int, Int)] {
        var ranges: [(Int, Int)] = []
        var start = -1
        for (index, character) in text.enumerated() {
            if character.isLetter || character.isNumber {
                if start < 0 { start = index }
            } else if start >= 0 {
                ranges.append((start, index))
                start = -1
            }
        }
        if start >= 0 { ranges.append((start, text.count)) }
        return ranges
    }
}
