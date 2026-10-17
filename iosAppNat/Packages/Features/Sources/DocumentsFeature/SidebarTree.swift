import Observation
import SwiftUI
import WrDesign
import WrModels
import WrSession

/// A folder or a document at the root of the workspace, as the "Folders" section of the sidebar
/// lists it.
public struct SidebarItem: Identifiable {
    public let id: String
    public let title: String
    let item: FolderItem

    init(_ item: FolderItem) {
        self.item = item
        switch item {
        case .folder(let folder):
            id = folder.id
            title = folder.displayTitle
        case .document(let document):
            id = document.id
            title = document.displayTitle
        }
    }
}

/// The icon of a sidebar item: the one of its folder or document, like the rows of the tree.
public struct SidebarItemIcon: View {
    private let item: SidebarItem

    public init(_ item: SidebarItem) {
        self.item = item
    }

    public var body: some View {
        switch item.item {
        case .folder(let folder):
            FolderIconImage(icon: folder.icon)
        case .document(let document):
            DocumentIconImage(icon: document.icon, defaultColor: .secondary)
        }
    }
}

/// The root of the tree of the workspace, for the "Folders" section of the sidebar: its folders
/// first, then its documents, both by title.
@MainActor
@Observable
public final class SidebarTree {
    public private(set) var items: [SidebarItem] = []

    public init() {}

    public func load(session: AppSession) async {
        guard let contents = try? await session.documents.folderContents(folderId: Folder.rootId) else { return }
        let folders = contents.folders
            .sorted { $0.title.localizedCaseInsensitiveCompare($1.title) == .orderedAscending }
            .map { SidebarItem(.folder($0)) }
        let documents = contents.documents
            .sorted { $0.displayTitle.localizedCaseInsensitiveCompare($1.displayTitle) == .orderedAscending }
            .map { SidebarItem(.document($0)) }
        items = folders + documents
    }

    /// Adds a folder at the root and tells `router`, so the list and this tree load it.
    public func createFolder(title: String, session: AppSession, router: DocumentsRouter) async {
        let title = title.trimmingCharacters(in: .whitespacesAndNewlines)
        _ = try? await session.documents.createFolder(
            title: title.isEmpty ? String(localized: "Untitled") : title,
            parentId: Folder.rootId
        )
        router.contentsChanged()
    }
}
