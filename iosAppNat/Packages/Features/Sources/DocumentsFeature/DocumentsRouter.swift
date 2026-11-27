import Observation
import SwiftUI
import WrData
import WrModels

/// Navigation of the Documents tab, shared with the side menu of landscape so it can open a
/// folder or a document from anywhere in the tree.
@Observable
public final class DocumentsRouter {
    public var path = NavigationPath()
    /// Increases whenever the side menu should load its tree again.
    public private(set) var treeVersion = 0
    /// Increases whenever the side menu changed the folders, so the list loads them again.
    public private(set) var contentsVersion = 0

    public init() {}

    /// Shows `folder` with its ancestors behind it, so back goes up the tree.
    @MainActor
    func open(folder: Folder, repository: DocumentsRepository) async {
        favoritesOpened = false
        graphOpened = false
        let ancestors = (try? await repository.folderPath(to: folder.id)) ?? []
        let folders = ancestors.last?.id == folder.id ? ancestors : [folder]
        path = NavigationPath(folders.map(DocumentsRoute.folder))
    }

    /// Opens `document` over the root of the documents.
    public func open(document: WrDocument) {
        path.append(DocumentsRoute.document(id: document.id, title: document.displayTitle))
    }

    /// Shows the documents carrying `tag`, like a folder.
    func open(tag: DocumentTag) {
        path.append(DocumentsRoute.tag(tag))
    }

    /// Shows the documents carrying `tag` right under the root, from the sidebar.
    func openFromSidebar(tag: DocumentTag) {
        favoritesOpened = false
        graphOpened = false
        path = NavigationPath([DocumentsRoute.tag(tag)])
    }

    /// Shows the documents graph, right under the root.
    public func openGraph() {
        favoritesOpened = false
        graphOpened = true
        path = NavigationPath([DocumentsRoute.graph])
    }

    /// Shows every favorite of the workspace, right under the root.
    public func openFavorites() {
        favoritesOpened = true
        graphOpened = false
        path = NavigationPath([DocumentsRoute.favorites])
    }

    /// Set by `openFavorites`; cleared when the tree is opened again.
    private var favoritesOpened = false

    /// Whether the favorites screen is the one shown (or a document opened from it), for the
    /// side menu's highlight. Going back to the root with the back button leaves it too.
    public var showsFavorites: Bool { favoritesOpened && !path.isEmpty }

    /// Set by `openGraph`; cleared when anything else is opened from the side menu.
    private var graphOpened = false

    /// Whether the graph is the screen shown (or a document opened from it), for the
    /// side menu's highlight.
    public var showsGraph: Bool { graphOpened && !path.isEmpty }

    public func reset() {
        favoritesOpened = false
        graphOpened = false
        path = NavigationPath()
    }

    /// Folders or documents were added, moved or removed.
    func treeChanged() {
        treeVersion += 1
    }

    /// The side menu added a folder: the list shows it too.
    func contentsChanged() {
        contentsVersion += 1
        treeVersion += 1
    }
}

public extension EnvironmentValues {
    /// Shows the Mac sidebar again; set only while it's hidden, so the canvas offers the button.
    @Entry var showSidebar: (() -> Void)?
}
