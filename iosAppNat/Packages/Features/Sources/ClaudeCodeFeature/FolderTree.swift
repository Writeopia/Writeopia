import Foundation

/// A folder as Claude Code sees it.
public struct LiveFolder: Equatable, Sendable {
    public let id: String
    public let name: String
    public let parentId: String

    public init(id: String, name: String, parentId: String) {
        self.id = id
        self.name = name
        self.parentId = parentId
    }
}

/// The answer of list_folders: the folders as an indented tree, siblings by name, each with its
/// folder_id, and which one the app has open. Mirrors `FolderTree.kt` word for word.
enum FolderTree {
    static let root = "root"

    static func describe(_ folders: [LiveFolder], currentFolderId: String) -> String {
        let byId = Dictionary(folders.map { ($0.id, $0) }, uniquingKeysWith: { first, _ in first })
        let open = if let current = byId[currentFolderId] {
            "The app has \"\(current.name)\" (folder_id: \(current.id)) open: new documents and folders go " +
                "there unless you pass a folder_id."
        } else {
            "The app has the top level open: new documents and folders go there unless you pass a folder_id."
        }

        if folders.isEmpty {
            return "Writeopia has no folders yet: everything is in the top level. Make one with create_folder."
        }

        // A folder whose parent isn't listed sits at the top level, so none goes missing.
        let children = Dictionary(grouping: folders) { byId[$0.parentId] != nil ? $0.parentId : root }
        var lines: [String] = []

        func walk(_ parentId: String, depth: Int, seen: Set<String>) {
            let sorted = (children[parentId] ?? []).enumerated().sorted { a, b in
                let (x, y) = (a.element.name.lowercased(), b.element.name.lowercased())
                return x == y ? a.offset < b.offset : x < y
            }.map(\.element)
            for folder in sorted where !seen.contains(folder.id) {
                lines.append(String(repeating: "  ", count: depth) + "- \(folder.name) (folder_id: \(folder.id))")
                walk(folder.id, depth: depth + 1, seen: seen.union([folder.id]))
            }
        }
        walk(root, depth: 0, seen: [])

        return "Folders in Writeopia (\(folders.count)):\n" + lines.joined(separator: "\n") + "\n\n" + open
    }
}
