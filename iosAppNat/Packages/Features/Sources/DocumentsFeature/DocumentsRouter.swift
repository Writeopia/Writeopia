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

    public init() {}

    /// Shows `folder` with its ancestors behind it, so back goes up the tree.
    @MainActor
    func open(folder: Folder, repository: DocumentsRepository) async {
        let ancestors = (try? await repository.folderPath(to: folder.id)) ?? []
        let folders = ancestors.last?.id == folder.id ? ancestors : [folder]
        path = NavigationPath(folders.map(DocumentsRoute.folder))
    }

    func open(document: WrDocument) {
        path.append(DocumentsRoute.document(id: document.id, title: document.displayTitle))
    }

    func reset() {
        path = NavigationPath()
    }

    /// Folders or documents were added, moved or removed.
    func treeChanged() {
        treeVersion += 1
    }
}
