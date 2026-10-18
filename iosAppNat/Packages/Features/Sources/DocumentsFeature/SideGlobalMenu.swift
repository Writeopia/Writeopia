import SwiftUI
import WrData
import WrDesign
import WrModels
import WrSession

/// Where the side menu of landscape can go: the tabs of the tab bar it replaces.
public enum MainDestination: Hashable {
    case documents
    case search
    case settings
}

/// Menu on the left in landscape, in place of the tab bar, like `SideGlobalMenu` of the Compose
/// app: the main destinations, then the folder tree of the workspace.
public struct SideGlobalMenu: View {
    @Binding private var selection: MainDestination
    private let router: DocumentsRouter
    private let fixedWidth: Bool
    @Environment(AppSession.self) private var session

    /// `fixedWidth` keeps the menu at the width of the landscape layout; the Mac sidebar column
    /// sizes it instead and paints its own material behind it.
    public init(selection: Binding<MainDestination>, router: DocumentsRouter, fixedWidth: Bool = true) {
        _selection = selection
        self.router = router
        self.fixedWidth = fixedWidth
    }

    public var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 2) {
                Text(session.workspace?.name ?? String(localized: "Documents"))
                    .font(.headline)
                    .lineLimit(1)
                    .padding(.horizontal, 12)
                    .padding(.bottom, 8)

                destinationRow(.search, title: "Search", systemImage: "magnifyingglass")
                destinationRow(.documents, title: "Home", systemImage: "house")
                favoritesRow
                graphRow
                destinationRow(.settings, title: "Settings", systemImage: "gearshape")

                SidebarFolders(router: router) { selection = .documents }
                    .padding(.top, 20)
            }
            .padding(.vertical, 12)
            .padding(.horizontal, 8)
        }
        .frame(width: fixedWidth ? 234 : nil)
        .frame(maxWidth: fixedWidth ? nil : .infinity)
        .background(fixedWidth ? WrColors.surface : .clear)
    }

    private func destinationRow(_ destination: MainDestination, title: LocalizedStringKey, systemImage: String) -> some View {
        // Home and Favorites share the Documents tab; only one of them is highlighted.
        let isSelected = selection == destination && !(destination == .documents && showsFavorites)
        return Button {
            if destination == .documents, selection == .documents {
                router.reset()
            }
            selection = destination
        } label: {
            Label(title, systemImage: systemImage)
                .font(.body.weight(isSelected ? .semibold : .regular))
                .foregroundStyle(isSelected ? WrColors.accent : .primary)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal, 12)
                .frame(height: 40)
                .background(
                    RoundedRectangle(cornerRadius: 10)
                        .fill(isSelected ? WrColors.accent.opacity(0.12) : Color.clear)
                )
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(isSelected ? .isSelected : [])
        .accessibilityIdentifier("sideMenu.\(destination)")
    }

    /// Every favorite of the workspace, like "Favorites" of the Compose side menu.
    private var favoritesRow: some View {
        let isSelected = selection == .documents && showsFavorites
        return Button {
            selection = .documents
            router.openFavorites()
        } label: {
            Label("Favorites", systemImage: "star")
                .font(.body.weight(isSelected ? .semibold : .regular))
                .foregroundStyle(isSelected ? WrColors.accent : .primary)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal, 12)
                .frame(height: 40)
                .background(
                    RoundedRectangle(cornerRadius: 10)
                        .fill(isSelected ? WrColors.accent.opacity(0.12) : Color.clear)
                )
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(isSelected ? .isSelected : [])
        .accessibilityIdentifier("sideMenu.favorites")
    }

    /// The force graph of the workspace, like the graph of the Compose desktop app.
    private var graphRow: some View {
        Button {
            selection = .documents
            router.openGraph()
        } label: {
            Label("Documents graph", systemImage: "point.3.connected.trianglepath.dotted")
                .font(.body)
                .foregroundStyle(.primary)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal, 12)
                .frame(height: 40)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier("sideMenu.graph")
    }

    private var showsFavorites: Bool { router.showsFavorites }
}

/// The folder tree of the workspace for a side menu: a "Folders" header with a button for a new
/// folder, then the tree from the root, with the folders expanding in place. `onOpen` runs when
/// a folder or a document is picked, so the menu can show the documents.
public struct SidebarFolders: View {
    private let router: DocumentsRouter
    private let onOpen: () -> Void
    @Environment(AppSession.self) private var session
    @State private var newFolderTitle = ""
    @State private var asksNewFolderTitle = false

    public init(router: DocumentsRouter, onOpen: @escaping () -> Void) {
        self.router = router
        self.onOpen = onOpen
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            HStack {
                Text("Folders")
                    .font(SideMenuStyle.sectionFont)
                    .foregroundStyle(.secondary)
                    .textCase(SideMenuStyle.sectionCase)
                // Shows while an editor opens a document, from the device and then the backend.
                if session.documentLoad.isLoading {
                    ProgressView()
                        .controlSize(.mini)
                        .padding(.leading, 6)
                        .accessibilityLabel("Loading document")
                        .accessibilityIdentifier("sideMenu.loadingDocument")
                }
                Spacer()
                Button {
                    newFolderTitle = ""
                    asksNewFolderTitle = true
                } label: {
                    Image(systemName: "plus")
                        .font(.caption.weight(.bold))
                        .frame(width: 28, height: 28)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel("New folder")
                .accessibilityIdentifier("sideMenu.newFolder")
            }
            .padding(.leading, 12)
            .padding(.trailing, 4)

            FolderTreeLevel(parentId: Folder.rootId, depth: 0, router: router, open: open)
                .padding(.leading, SideMenuStyle.treeInset)
        }
        .alert("New folder", isPresented: $asksNewFolderTitle) {
            TextField("Title", text: $newFolderTitle)
            Button("Cancel", role: .cancel) {}
            Button("Create") { createFolder() }
        }
    }

    private func open(_ item: FolderItem) {
        onOpen()
        switch item {
        case .folder(let folder):
            Task { await router.open(folder: folder, repository: session.documents) }
        case .document(let document):
            router.open(document: document)
        }
    }

    private func createFolder() {
        let title = newFolderTitle.trimmingCharacters(in: .whitespacesAndNewlines)
        Task {
            _ = try? await session.documents.createFolder(
                title: title.isEmpty ? String(localized: "Untitled") : title,
                parentId: Folder.rootId
            )
            router.contentsChanged()
        }
    }
}

/// The look of the rows of the tree. On the iPad the tree sits under the rows of the system
/// sidebar, so its rows match them; the Mac sidebar and the side menu are denser.
private enum SideMenuStyle {
    #if os(macOS)
    static let rowFont: Font = .subheadline
    static let rowHeight: CGFloat = 32
    static let sectionFont: Font = .caption.weight(.semibold)
    static let sectionCase: Text.Case? = .uppercase
    static let treeInset: CGFloat = 0
    #else
    static let rowFont: Font = .body
    static let rowHeight: CGFloat = 44
    static let sectionFont: Font = .body
    static let sectionCase: Text.Case? = nil
    /// The column of the chevrons goes in the margin of the sidebar, so the icons of the tree
    /// line up with the icons of the rows above it.
    static let treeInset: CGFloat = -24
    #endif
}

/// The folders and documents inside `parentId`; folders expand in place.
private struct FolderTreeLevel: View {
    let parentId: String
    let depth: Int
    let router: DocumentsRouter
    let open: (FolderItem) -> Void
    @Environment(AppSession.self) private var session
    @State private var contents = FolderContents()
    @State private var expanded: Set<String> = []

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            ForEach(sortedFolders) { folder in
                row(
                    title: folder.displayTitle,
                    icon: { FolderIconImage(icon: folder.icon) },
                    isExpanded: expanded.contains(folder.id),
                    toggle: { toggle(folder.id) }
                ) { open(.folder(folder)) }

                if expanded.contains(folder.id) {
                    FolderTreeLevel(parentId: folder.id, depth: depth + 1, router: router, open: open)
                }
            }
            ForEach(sortedDocuments) { document in
                row(
                    title: document.displayTitle,
                    icon: { DocumentIconImage(icon: document.icon, defaultColor: .secondary) },
                    iconButton: {
                        DocumentIconButton(icon: document.icon) { icon in changeIcon(of: document.id, to: icon) }
                    }
                ) {
                    open(.document(document))
                }
            }
        }
        .task(id: router.treeVersion) { await load() }
    }

    private var sortedFolders: [Folder] {
        contents.folders.sorted { $0.title.localizedCaseInsensitiveCompare($1.title) == .orderedAscending }
    }

    private var sortedDocuments: [WrDocument] {
        contents.documents.sorted { $0.displayTitle.localizedCaseInsensitiveCompare($1.displayTitle) == .orderedAscending }
    }

    private func toggle(_ id: String) {
        withAnimation(.snappy) {
            if expanded.contains(id) {
                expanded.remove(id)
            } else {
                expanded.insert(id)
            }
        }
    }

    private func row<Icon: View>(
        title: String,
        @ViewBuilder icon: () -> Icon,
        isExpanded: Bool? = nil,
        toggle: @escaping () -> Void = {},
        action: @escaping () -> Void
    ) -> some View {
        row(title: title, icon: icon, iconButton: { EmptyView() }, hasIconButton: false, isExpanded: isExpanded, toggle: toggle, action: action)
    }

    /// A row whose icon is a button of its own (the icon picker of a document): it sits before
    /// the title, outside the button that opens the row, so a click on it doesn't open the row.
    private func row<Icon: View, IconButton: View>(
        title: String,
        @ViewBuilder icon: () -> Icon,
        @ViewBuilder iconButton: () -> IconButton,
        hasIconButton: Bool = true,
        isExpanded: Bool? = nil,
        toggle: @escaping () -> Void = {},
        action: @escaping () -> Void
    ) -> some View {
        HStack(spacing: 4) {
            // Expands a folder; documents keep the space so titles line up.
            Button(action: toggle) {
                Image(systemName: "chevron.right")
                    .font(.caption2.weight(.bold))
                    .foregroundStyle(.secondary)
                    .rotationEffect(.degrees(isExpanded == true ? 90 : 0))
                    .frame(width: 20, height: SideMenuStyle.rowHeight)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .opacity(isExpanded == nil ? 0 : 1)
            .disabled(isExpanded == nil)
            .accessibilityLabel(isExpanded == true ? "Collapse" : "Expand")

            if hasIconButton {
                iconButton()
                    .font(SideMenuStyle.rowFont)

                Button(action: action) {
                    Text(title.isEmpty ? String(localized: "Untitled") : title)
                        .lineLimit(1)
                        .foregroundStyle(WrColors.textLight)
                        .font(SideMenuStyle.rowFont)
                        .frame(maxWidth: .infinity, minHeight: SideMenuStyle.rowHeight, alignment: .leading)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
            } else {
                Button(action: action) {
                    Label {
                        Text(title.isEmpty ? String(localized: "Untitled") : title)
                            .lineLimit(1)
                            .foregroundStyle(WrColors.textLight)
                    } icon: {
                        icon()
                            .foregroundStyle(.secondary)
                    }
                    .font(SideMenuStyle.rowFont)
                    .frame(maxWidth: .infinity, minHeight: SideMenuStyle.rowHeight, alignment: .leading)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
            }
        }
        .padding(.leading, CGFloat(depth) * 14 + 4)
    }

    private func load() async {
        guard let loaded = try? await session.documents.folderContents(folderId: parentId) else { return }
        contents = loaded
    }

    /// Shown here right away; the list of the folder reloads to show it on the card too.
    private func changeIcon(of documentId: String, to icon: IconInfo?) {
        if let index = contents.documents.firstIndex(where: { $0.id == documentId }) {
            contents.documents[index].icon = icon
        }
        Task {
            try? await session.documents.updateDocumentIcon(id: documentId, icon: icon)
            router.contentsChanged()
        }
    }
}
