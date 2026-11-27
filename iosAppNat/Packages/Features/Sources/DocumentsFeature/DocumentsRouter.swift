import Observation
import SwiftUI
import WrData
import WrModels

/// Navigation of the Documents tab, shared with the side menu of landscape so it can open a
/// folder or a document from anywhere in the tree.
@Observable
public final class DocumentsRouter {
    /// The screens over the root, last on top. An array, not a `NavigationPath`, so the place a
    /// tab is in can be read and saved (`place`).
    public var path: [DocumentsRoute] = []
    /// The folder shown last (the root when none, or for favorites and tags): where Claude Code
    /// puts new folders and documents unless told otherwise.
    public private(set) var shownFolderId = Folder.rootId
    /// Increases whenever the side menu should load its tree again.
    public private(set) var treeVersion = 0
    /// Increases whenever the side menu changed the folders, so the list loads them again.
    public private(set) var contentsVersion = 0

    public init() {}

    /// Shows `folder` with its ancestors behind it, so back goes up the tree.
    @MainActor
    public func open(folder: Folder, repository: DocumentsRepository) async {
        favoritesOpened = false
        let ancestors = (try? await repository.folderPath(to: folder.id)) ?? []
        let folders = ancestors.last?.id == folder.id ? ancestors : [folder]
        path = folders.map(DocumentsRoute.folder)
    }

    /// Opens `document` over the root of the documents.
    public func open(document: WrDocument) {
        path.append(DocumentsRoute.document(id: document.id, title: document.displayTitle))
    }

    /// Shows the documents carrying `tag`, like a folder.
    func open(tag: DocumentTag) {
        path.append(DocumentsRoute.tag(tag))
    }

    /// Shows the documents graph, right under the root.
    public func openGraph() {
        favoritesOpened = false
        path = [.graph]
    }

    /// Shows every favorite of the workspace, right under the root.
    public func openFavorites() {
        favoritesOpened = true
        path = [.favorites]
    }

    /// Set by `openFavorites`; cleared when the tree is opened again.
    private var favoritesOpened = false

    /// Whether the favorites screen is the one shown (or a document opened from it), for the
    /// side menu's highlight. Going back to the root with the back button leaves it too.
    public var showsFavorites: Bool { favoritesOpened && !path.isEmpty }

    public func reset() {
        favoritesOpened = false
        shownFolderId = Folder.rootId
        path = []
    }

    /// A folder view appeared: `id` is a folder, or the favorites or a tag (then the root counts).
    /// `folder` is about to be shown: it is the folder open from now on, before the navigation lands.
    public func willShow(_ folder: Folder) {
        shownFolderId = folder.id
    }

    func folderShown(_ id: String, isFolder: Bool) {
        shownFolderId = isFolder ? id : Folder.rootId
    }

    // MARK: - Tabs

    /// Where this router is, to save with the tab and restore at the next launch: the screen on
    /// top. Nil at the root, and for a tag (restored at the root).
    public var place: TabPlace? {
        switch path.last {
        case .document(let id, let title, _): .document(id: id, title: title)
        case .folder(let folder): .folder(id: folder.id)
        case .favorites: .favorites
        case .graph: .graph
        case .tag, nil: nil
        }
    }

    /// The document on screen, if the top screen is one.
    public var shownDocumentId: String? {
        if case .document(let id, _, _) = path.last { id } else { nil }
    }

    /// Goes to a saved place, or one a new tab was opened on. A folder comes with its ancestors
    /// behind it, like `open(folder:)`; a folder that no longer exists leaves the root.
    @MainActor
    public func open(place: TabPlace, repository: DocumentsRepository) async {
        switch place {
        case .document(let id, let title):
            favoritesOpened = false
            path = [.document(id: id, title: title)]
        case .folder(let id):
            favoritesOpened = false
            let ancestors = (try? await repository.folderPath(to: id)) ?? []
            if ancestors.last?.id == id {
                shownFolderId = id
                path = ancestors.map(DocumentsRoute.folder)
            }
        case .favorites:
            openFavorites()
        case .graph:
            openGraph()
        }
    }

    /// Folders or documents were added, moved or removed.
    public func treeChanged() {
        treeVersion += 1
    }

    /// The side menu added a folder: the list shows it too.
    func contentsChanged() {
        contentsVersion += 1
        treeVersion += 1
    }
}

/// Where a tab of the Mac app is: what a new tab opens on (the value of its window) and what it
/// saves to come back to at the next launch. Mirrors `TabPlace` of the Compose desktop app.
public enum TabPlace: Codable, Hashable, Sendable {
    case document(id: String, title: String)
    case folder(id: String)
    case favorites
    case graph
}

public extension EnvironmentValues {
    /// Opens a document or folder in a new tab of the window; nil where there are no tabs (iOS).
    @Entry var openInNewTab: ((TabPlace) -> Void)?
}
