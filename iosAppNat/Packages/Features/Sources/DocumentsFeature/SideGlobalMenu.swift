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
    @Environment(AppSession.self) private var session
    @State private var newFolderTitle = ""
    @State private var asksNewFolderTitle = false

    public init(selection: Binding<MainDestination>, router: DocumentsRouter) {
        _selection = selection
        self.router = router
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
                destinationRow(.settings, title: "Settings", systemImage: "gearshape")

                HStack {
                    Text("Folders")
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(.secondary)
                        .textCase(.uppercase)
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
                .padding(.top, 20)

                FolderTreeLevel(parentId: Folder.rootId, depth: 0, router: router, open: open)
            }
            .padding(.vertical, 12)
            .padding(.horizontal, 8)
        }
        .frame(width: 234)
        .background(WrColors.surface)
        .alert("New folder", isPresented: $asksNewFolderTitle) {
            TextField("Title", text: $newFolderTitle)
            Button("Cancel", role: .cancel) {}
            Button("Create") { createFolder() }
        }
    }

    private func destinationRow(_ destination: MainDestination, title: LocalizedStringKey, systemImage: String) -> some View {
        let isSelected = selection == destination
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

    private func open(_ item: FolderItem) {
        selection = .documents
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
            router.treeChanged()
        }
    }
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
                row(title: document.displayTitle, icon: { Image(systemName: "doc.text") }) {
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
        HStack(spacing: 4) {
            // Expands a folder; documents keep the space so titles line up.
            Button(action: toggle) {
                Image(systemName: "chevron.right")
                    .font(.caption2.weight(.bold))
                    .foregroundStyle(.secondary)
                    .rotationEffect(.degrees(isExpanded == true ? 90 : 0))
                    .frame(width: 20, height: 32)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .opacity(isExpanded == nil ? 0 : 1)
            .disabled(isExpanded == nil)
            .accessibilityLabel(isExpanded == true ? "Collapse" : "Expand")

            Button(action: action) {
                Label {
                    Text(title.isEmpty ? String(localized: "Untitled") : title)
                        .lineLimit(1)
                        .foregroundStyle(WrColors.textLight)
                } icon: {
                    icon()
                        .foregroundStyle(.secondary)
                }
                .font(.subheadline)
                .frame(maxWidth: .infinity, minHeight: 32, alignment: .leading)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
        }
        .padding(.leading, CGFloat(depth) * 14 + 4)
    }

    private func load() async {
        guard let loaded = try? await session.documents.folderContents(folderId: parentId) else { return }
        contents = loaded
    }
}
