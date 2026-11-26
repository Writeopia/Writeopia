import Foundation

/// The document open in the editor, as get_current_place reports it.
public struct OpenDocument: Equatable, Sendable {
    public let id: String
    public let title: String
    /// The folders from the top level down to the one holding the document; empty at the top level.
    public let folderPath: [LiveFolder]
    public let tags: [String]
    public let isLocked: Bool
    public let hasHeaderPicture: Bool
}

/// What changed since Claude Code's last call, as get_current_place reports it.
public enum PlaceChange: Equatable, Sendable {
    /// The person moved away from the document Claude last worked in.
    case moved(fromId: String, fromTitle: String)
    /// The person edited the document Claude last worked in.
    case edited(added: Int, changed: Int, removed: Int)
    case unchanged
}

/// What a document looked like at the end of a call of Claude Code: its id, title and blocks (id
/// to content), to tell later what the person did meanwhile. Mirrors `DocumentSnapshot` in Kotlin.
public struct DocumentSnapshot: Equatable, Sendable {
    public let documentId: String
    public let title: String
    public let blocks: [String: String]

    /// What the person did between this snapshot and `now` (the document open now, if any).
    public func change(to now: DocumentSnapshot?) -> PlaceChange {
        guard let now, now.documentId == documentId else { return .moved(fromId: documentId, fromTitle: title) }

        let added = now.blocks.keys.filter { blocks[$0] == nil }.count
        let removed = blocks.keys.filter { now.blocks[$0] == nil }.count
        let changed = now.blocks.filter { id, content in blocks[id].map { $0 != content } ?? false }.count
        return added + removed + changed == 0 ? .unchanged : .edited(added: added, changed: changed, removed: removed)
    }
}

/// The answer of get_current_place. Mirrors `PlaceReport.kt` word for word, the Markdown of the
/// content aside (each app writes its own).
enum PlaceReport {
    static let contentLimit = 20_000

    static func describe(
        document: OpenDocument?,
        currentFolder: LiveFolder?,
        change: PlaceChange?,
        content: String?
    ) -> String {
        var text = ""
        if let document {
            text += "Writeopia shows the document \"\(document.title)\" (document_id: \(document.id)) in "
            if let last = document.folderPath.last {
                text += document.folderPath.map(\.name).joined(separator: " / ") + " (folder_id: \(last.id))."
            } else {
                text += "the top level."
            }
            let tags = document.tags.isEmpty ? "none" : document.tags.joined(separator: ", ")
            text += "\nTags: \(tags). Locked: \(yesNo(document.isLocked)). Header picture: \(yesNo(document.hasHeaderPicture))."
        } else {
            text += "No document is open in Writeopia. "
            if let currentFolder {
                text += "The app has \"\(currentFolder.name)\" (folder_id: \(currentFolder.id)) open."
            } else {
                text += "The app has the top level open."
            }
        }

        switch change {
        case nil:
            break
        case let .moved(fromId, fromTitle):
            text += "\n\nSince your last call, the person \(document == nil ? "left" : "moved here from") \"\(fromTitle)\" (document_id: \(fromId))."
        case let .edited(added, changed, removed):
            text += "\n\nSince your last call, the person changed this document: \(added) blocks added, \(changed) changed, \(removed) removed."
        case .unchanged:
            text += "\n\nSince your last call, nothing changed here."
        }

        if document != nil, let content {
            let markdown = content.trimmingCharacters(in: .whitespacesAndNewlines)
            // Counted in UTF-16 units, like the Kotlin string length.
            let units = Array(markdown.utf16)
            if markdown.isEmpty {
                text += "\n\nContent: empty."
            } else if units.count <= contentLimit {
                text += "\n\nContent (Markdown):\n\(markdown)"
            } else {
                let kept = String(decoding: units[0..<contentLimit], as: UTF16.self)
                text += "\n\nContent (Markdown):\n\(kept)\n…(cut: \(units.count - contentLimit) more characters)"
            }
        }
        return text
    }

    private static func yesNo(_ value: Bool) -> String { value ? "yes" : "no" }
}
