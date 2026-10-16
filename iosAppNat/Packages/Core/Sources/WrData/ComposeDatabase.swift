import Foundation
import WrModels

/// Reads the SQLite database of the Compose desktop app (`~/.writeopia/writeopia_1.db`), so the
/// Mac app can bring its offline space over. The database is only read, the Compose app keeps
/// working with it.
///
/// The Compose schema keeps a step in columns (`storyStepEntity`), with tags as `H1,H2` and
/// spans as `start:end:SPAN[:extra]` joined by commas, and nests the steps of a group through
/// `parent_id`. Columns added by later versions of the Compose app may be missing in an old
/// database; they are read as NULL.
struct ComposeDatabase {
    struct Contents {
        var folders: [Folder] = []
        var documents: [WrDocument] = []
        var tags: [DocumentTag] = []
        var presentations: [Presentation] = []
        var presentationSteps: [PresentationStepRow] = []

        var isEmpty: Bool { folders.isEmpty && documents.isEmpty && tags.isEmpty && presentations.isEmpty }
    }

    /// A slide step as the Compose app stores it: the step is JSON of `StoryStepApi`, which the
    /// Swift `StoryStep` decodes, so it's copied as it is.
    struct PresentationStepRow: Equatable {
        let id: String
        let presentationId: String
        let slideIndex: Int64
        let position: Double
        let content: String
    }

    #if os(macOS)
    /// Where the Compose desktop app keeps its database.
    static var defaultLocation: URL {
        FileManager.default.homeDirectoryForCurrentUser.appending(path: ".writeopia/writeopia_1.db")
    }
    #endif

    private let db: SQLiteDatabase

    /// Nil when there's no database at `url`.
    init?(url: URL) throws {
        guard FileManager.default.fileExists(atPath: url.path(percentEncoded: false)) else { return nil }
        db = try SQLiteDatabase(readingOnly: url)
    }

    /// Everything in the database, moved to `workspaceId`: the Compose database only holds the
    /// offline space. Nothing is marked as synced, like the rest of the private space.
    func contents(workspaceId: String) throws -> Contents {
        let tables = Set(try db.query("SELECT name FROM sqlite_master WHERE type = 'table'") { $0.text(0) })
        var contents = Contents()

        if tables.contains("folderEntity") {
            contents.folders = try folders(workspaceId: workspaceId)
        }
        if tables.contains("documentEntity") {
            let steps = tables.contains("storyStepEntity") ? try stepsByDocument() : [:]
            let tagLinks = tables.contains("documentTagLinkEntity") ? try tagLinksByDocument() : [:]
            contents.documents = try documents(workspaceId: workspaceId, steps: steps, tagLinks: tagLinks)
        }
        if tables.contains("documentTagEntity") {
            contents.tags = try tags(workspaceId: workspaceId)
        }
        if tables.contains("presentationEntity") {
            contents.presentations = try presentations()
            if tables.contains("presentationStepEntity") {
                contents.presentationSteps = try presentationSteps()
            }
        }
        return contents
    }

    // MARK: - Tables

    private func folders(workspaceId: String) throws -> [Folder] {
        let c = try columns(of: "folderEntity")
        return try db.query(
            "SELECT id, \(c("parent_id")), title, \(c("created_at")), \(c("last_updated_at")), \(c("favorite")), \(c("icon")), \(c("icon_tint")), \(c("deleted")) FROM folderEntity"
        ) { row in
            guard let id = row.text(0) else { return nil }
            return Folder(
                id: id,
                parentId: row.text(1).flatMap { $0.isEmpty ? nil : $0 } ?? Folder.rootId,
                title: row.text(2) ?? "",
                workspaceId: workspaceId,
                favorite: row.bool(5),
                icon: row.text(6).map { IconInfo(label: $0, tint: row.integer(7).map(Int.init)) },
                createdAt: row.integer(3).map(Date.init(millis:)),
                lastUpdatedAt: row.integer(4).map(Date.init(millis:)),
                deleted: row.bool(8)
            )
        }
    }

    private func documents(
        workspaceId: String,
        steps: [String: [StoryStep]],
        tagLinks: [String: [String]]
    ) throws -> [WrDocument] {
        let c = try columns(of: "documentEntity")
        let rows = try db.query(
            """
            SELECT id, title, \(c("created_at")), \(c("last_updated_at")), \(c("favorite")), \(c("parent_document_id")),
            \(c("icon")), \(c("icon_tint")), \(c("is_locked")), \(c("deleted")) FROM documentEntity
            """
        ) { row -> WrDocument? in
            guard let id = row.text(0) else { return nil }
            let lastUpdatedAt = row.integer(3) ?? row.integer(2) ?? Date.nowMillis
            return WrDocument(
                id: id,
                title: row.text(1) ?? "",
                workspaceId: workspaceId,
                content: steps[id] ?? [],
                createdAt: row.integer(2) ?? lastUpdatedAt,
                lastUpdatedAt: lastUpdatedAt,
                isFavorite: row.bool(4),
                parentId: row.text(5).flatMap { $0.isEmpty ? nil : $0 } ?? Folder.rootId,
                isLocked: row.bool(8),
                deleted: row.bool(9),
                icon: row.text(6).map { IconInfo(label: $0, tint: row.integer(7).map(Int.init)) },
                tagIds: tagLinks[id] ?? []
            )
        }

        // A link to another document shows its title, like the Compose app loads it.
        let titles = Dictionary(rows.map { ($0.id, $0.title) }, uniquingKeysWith: { first, _ in first })
        return rows.map { document in
            var document = document
            document.content = document.content.map { Self.withLinkTitles($0, titles: titles) }
            return document
        }
    }

    /// The steps of each document, top-level ones in order with the steps of their group nested.
    private func stepsByDocument() throws -> [String: [StoryStep]] {
        let c = try columns(of: "storyStepEntity")
        let rows = try db.query(
            """
            SELECT id, document_id, \(c("type")), \(c("parent_id")), \(c("url")), \(c("path")), \(c("text")), \(c("checked")),
            \(c("position")), \(c("background_color")), \(c("tags")), \(c("spans")), \(c("link_to_document")), \(c("last_updated_at"))
            FROM storyStepEntity ORDER BY position
            """
        ) { row -> (documentId: String, step: StoryStep)? in
            guard let id = row.text(0), let documentId = row.text(1) else { return nil }
            let type = Self.storyType(number: Int(row.integer(2) ?? 0))
            let checked = row.integer(7).map { $0 != 0 }
            let step = StoryStep(
                id: id,
                type: type,
                text: row.text(6),
                checked: type.number == StoryType.checkItem.number ? (checked ?? false) : (checked == true ? true : nil),
                url: row.text(4),
                path: row.text(5),
                tags: Self.tags(row.text(10)),
                spans: Self.spans(row.text(11)),
                position: row.real(8) ?? 0,
                documentLink: row.text(12).map { DocumentLink(id: $0) },
                parentId: row.text(3),
                decoration: row.integer(9).map { Decoration(backgroundColor: Int($0)) },
                lastUpdatedAt: row.integer(13)
            )
            return (documentId, step)
        }

        var result: [String: [StoryStep]] = [:]
        for (documentId, steps) in Dictionary(grouping: rows, by: \.documentId) {
            let all = steps.map(\.step)
            let children = Dictionary(grouping: all.filter { $0.parentId != nil }, by: { $0.parentId! })
            let ids = Set(all.map(\.id))
            // A child whose group is gone is kept at the top level instead of being lost.
            result[documentId] = all
                .filter { $0.parentId == nil || !ids.contains($0.parentId!) }
                .map { Self.nesting($0, children: children, depth: 0) }
        }
        return result
    }

    private func tagLinksByDocument() throws -> [String: [String]] {
        let rows = try db.query("SELECT document_id, tag_id FROM documentTagLinkEntity ORDER BY tag_id") { row -> (String, String)? in
            guard let documentId = row.text(0), let tagId = row.text(1) else { return nil }
            return (documentId, tagId)
        }
        return Dictionary(grouping: rows, by: \.0).mapValues { $0.map(\.1) }
    }

    private func tags(workspaceId: String) throws -> [DocumentTag] {
        let c = try columns(of: "documentTagEntity")
        return try db.query(
            "SELECT id, name, \(c("description")), \(c("created_at")), \(c("last_updated_at")), \(c("deleted")) FROM documentTagEntity"
        ) { row in
            guard let id = row.text(0) else { return nil }
            let createdAt = row.integer(3) ?? Date.nowMillis
            return DocumentTag(
                id: id,
                workspaceId: workspaceId,
                name: row.text(1) ?? "",
                description: row.text(2) ?? "",
                createdAt: createdAt,
                lastUpdatedAt: row.integer(4) ?? createdAt,
                deleted: row.bool(5)
            )
        }
    }

    private func presentations() throws -> [Presentation] {
        try db.query("SELECT id, document_id, title, created_at FROM presentationEntity") { row in
            guard let id = row.text(0) else { return nil }
            return Presentation(id: id, documentId: row.text(1) ?? "", title: row.text(2) ?? "", createdAt: row.integer(3) ?? 0, slides: [])
        }
    }

    private func presentationSteps() throws -> [PresentationStepRow] {
        try db.query("SELECT id, presentation_id, slide_index, position, content FROM presentationStepEntity") { row in
            guard let id = row.text(0), let presentationId = row.text(1), let content = row.text(4) else { return nil }
            return PresentationStepRow(
                id: id,
                presentationId: presentationId,
                slideIndex: row.integer(2) ?? 0,
                position: row.real(3) ?? 0,
                content: content
            )
        }
    }

    // MARK: - Helpers

    /// A function giving the column name, or NULL when the table doesn't have it.
    private func columns(of table: String) throws -> (String) -> String {
        let names = Set(try db.query("PRAGMA table_info(\(table))") { $0.text(1) })
        return { names.contains($0) ? $0 : "NULL" }
    }

    /// Groups are one level deep in the Compose app; the depth only guards against a cycle.
    private static func nesting(_ step: StoryStep, children: [String: [StoryStep]], depth: Int) -> StoryStep {
        guard depth < 8, let inner = children[step.id], !inner.isEmpty else { return step }
        var step = step
        step.steps = inner.map { nesting($0, children: children, depth: depth + 1) }
        return step
    }

    private static func withLinkTitles(_ step: StoryStep, titles: [String: String]) -> StoryStep {
        var step = step
        if let link = step.documentLink {
            step.documentLink = DocumentLink(id: link.id, title: titles[link.id])
        }
        step.steps = step.steps.map { withLinkTitles($0, titles: titles) }
        return step
    }

    /// `H1,CODE_BLOCK`, as `DocumentSqlDao` writes them.
    static func tags(_ text: String?) -> [TagInfo] {
        (text ?? "").split(separator: ",").map { TagInfo(tag: String($0)) }
    }

    /// `start:end:SPAN` or `start:end:SPAN:extra`, joined by commas. The extra of a link is an
    /// address that may have commas itself, so a piece that doesn't start like a span belongs
    /// to the one before.
    static func spans(_ text: String?) -> [SpanInfo] {
        var pieces: [String] = []
        for piece in (text ?? "").split(separator: ",", omittingEmptySubsequences: true).map(String.init) {
            if piece.wholeMatch(of: /\d+:\d+:.*/) == nil, let last = pieces.popLast() {
                pieces.append(last + "," + piece)
            } else {
                pieces.append(piece)
            }
        }

        return pieces.compactMap { piece in
            let parts = piece.split(separator: ":", maxSplits: 3, omittingEmptySubsequences: false).map(String.init)
            guard parts.count >= 3, let start = Int(parts[0]), let end = Int(parts[1]), !parts[2].isEmpty else { return nil }
            let extra = parts.count == 4 && parts[3] != "null" ? parts[3] : nil
            return SpanInfo(start: min(start, end), end: max(start, end), span: parts[2].uppercased(), extra: extra)
        }
    }

    /// The names of `StoryTypes` in the SDK, by number.
    static func storyType(number: Int) -> StoryType {
        let names = [
            0: "message", 2: "image", 4: "audio", 6: "video", 7: "space", 8: "last_space", 9: "add_button",
            10: "check_item", 11: "title", 16: "unordered_list_item", 17: "on_drag_space", 18: "ai_answer",
            19: "loading", 20: "document_link", 21: "divider", 22: "equation", 23: "code_block", 24: "link_card",
            100: "drawing", 101: "spreadsheet", 102: "spreadsheet_row", 103: "spreadsheet_cell",
        ]
        return StoryType(name: names[number] ?? "message", number: names[number] == nil ? 0 : number)
    }
}
