import SwiftUI
import WrData
import WrDesign
import WrModels

/// Picks the folder to move items to, browsing the tree from the root. The moved folders and
/// what's inside them can't be picked, nor the folder the items already are in.
struct FolderPickerSheet: View {
    /// Folders being moved: not offered, nor their contents.
    let excludedFolderIds: Set<String>
    /// Where the items are now: offered, but disabled.
    let currentParentId: String
    let rootTitle: String
    let repository: DocumentsRepository
    let onPick: (_ folderId: String) -> Void
    @Environment(\.dismiss) private var dismiss

    init(excludedFolderIds: Set<String>, currentParentId: String, rootTitle: String, repository: DocumentsRepository, onPick: @escaping (_ folderId: String) -> Void) {
        self.excludedFolderIds = excludedFolderIds
        self.currentParentId = currentParentId
        self.rootTitle = rootTitle
        self.repository = repository
        self.onPick = onPick
    }

    /// Moving one folder, from its options sheet.
    init(movingFolder: Folder, rootTitle: String, repository: DocumentsRepository, onPick: @escaping (_ folderId: String) -> Void) {
        self.init(excludedFolderIds: [movingFolder.id], currentParentId: movingFolder.parentId, rootTitle: rootTitle, repository: repository, onPick: onPick)
    }

    var body: some View {
        NavigationStack {
            FolderPickerLevel(
                folderId: Folder.rootId,
                title: rootTitle,
                excludedFolderIds: excludedFolderIds,
                currentParentId: currentParentId,
                repository: repository,
                onPick: pick
            )
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
            }
        }
    }

    private func pick(_ folderId: String) {
        onPick(folderId)
        dismiss()
    }
}

private struct FolderPickerLevel: View {
    let folderId: String
    let title: String
    let excludedFolderIds: Set<String>
    let currentParentId: String
    let repository: DocumentsRepository
    let onPick: (String) -> Void
    @State private var folders: [Folder] = []
    @State private var isLoading = true
    @State private var errorMessage: String?

    private var isCurrentParent: Bool { folderId == currentParentId }
    private var isRoot: Bool { folderId == Folder.rootId }

    var body: some View {
        ScrollView {
            LazyVStack(spacing: 2) {
                ForEach(folders) { folder in
                    NavigationLink {
                        FolderPickerLevel(
                            folderId: folder.id,
                            title: folder.displayTitle,
                            excludedFolderIds: excludedFolderIds,
                            currentParentId: currentParentId,
                            repository: repository,
                            onPick: onPick
                        )
                    } label: {
                        FolderPickerRow(folder: folder, isCurrentParent: folder.id == currentParentId)
                    }
                    .buttonStyle(FolderPickerRowStyle())
                    .accessibilityIdentifier("folderPicker.folder.\(folder.id)")
                }
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 8)
        }
        .background(WrColors.background)
        .overlay {
            WrStateOverlay(
                isLoading: isLoading,
                isEmpty: folders.isEmpty,
                errorMessage: errorMessage,
                emptyTitle: "No folders here",
                emptyImage: "folder",
                retry: { Task { await load() } }
            )
        }
        // The action is the title on every level; where the user is browsing goes in the header.
        .navigationTitle("Move to…")
        .toolbarTitleDisplayMode(.inline)
        .safeAreaInset(edge: .top, spacing: 0) { locationHeader }
        .safeAreaInset(edge: .bottom, spacing: 0) {
            Button {
                onPick(folderId)
            } label: {
                Label(
                    isCurrentParent ? String(localized: "It's already here") : String(localized: "Move here"),
                    systemImage: "arrow.forward.folder"
                )
                .font(.body.weight(.semibold))
                .frame(maxWidth: .infinity)
                .frame(height: 36)
            }
            .buttonStyle(.borderedProminent)
            .buttonBorderShape(.roundedRectangle(radius: 10))
            .tint(WrColors.accent)
            .disabled(isCurrentParent)
            .padding()
            .background(.bar)
            .accessibilityIdentifier("folderPicker.moveHere")
        }
        .task { await load() }
    }

    /// The folder being browsed, so the title can stay "Move to…".
    private var locationHeader: some View {
        HStack(spacing: 12) {
            Image(systemName: isRoot ? "house.fill" : "folder.fill")
                .font(.system(size: 15, weight: .semibold))
                .foregroundStyle(WrColors.accent)
                .frame(width: 34, height: 34)
                .background(WrColors.accent.opacity(0.14), in: RoundedRectangle(cornerRadius: 9, style: .continuous))
            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(.headline)
                    .lineLimit(1)
                Text(isCurrentParent ? "The items are here. Open a folder to move them into it." : "Move here, or open a folder to move the items into it.")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .lineLimit(2)
            }
            Spacer(minLength: 0)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(.bar)
        .overlay(alignment: .bottom) { Divider() }
    }

    private func load() async {
        isLoading = true
        defer { isLoading = false }
        do {
            folders = try await repository.folderContents(folderId: folderId).folders
                .filter { !excludedFolderIds.contains($0.id) }
                .sorted { $0.title.localizedCaseInsensitiveCompare($1.title) == .orderedAscending }
            errorMessage = nil
        } catch {
            errorMessage = error.userMessage
        }
    }
}

/// A folder to open: its icon on a tinted tile, the name and a chevron. Tall enough to be an
/// easy target with the mouse.
private struct FolderPickerRow: View {
    let folder: Folder
    let isCurrentParent: Bool

    private var tint: Color { FolderIcons.color(for: folder.icon?.tint) ?? WrColors.accent }

    var body: some View {
        HStack(spacing: 12) {
            FolderIconImage(icon: folder.icon)
                .font(.system(size: 16, weight: .medium))
                .frame(width: 34, height: 34)
                .background(tint.opacity(0.14), in: RoundedRectangle(cornerRadius: 9, style: .continuous))
            VStack(alignment: .leading, spacing: 2) {
                Text(folder.displayTitle)
                    .font(.body.weight(.medium))
                    .foregroundStyle(WrColors.textLight)
                    .lineLimit(1)
                if isCurrentParent {
                    Text("The items are here")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            }
            Spacer(minLength: 8)
            Image(systemName: "chevron.right")
                .font(.system(size: 12, weight: .semibold))
                .foregroundStyle(.tertiary)
        }
        .padding(.horizontal, 12)
        .frame(minHeight: 52)
        .contentShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
    }
}

/// Rows light up under the mouse and while pressed, like the rows of the folder options sheet.
private struct FolderPickerRowStyle: ButtonStyle {
    @State private var isHovered = false

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .background(
                RoundedRectangle(cornerRadius: 10, style: .continuous)
                    .fill(configuration.isPressed ? WrColors.secondaryFill : isHovered ? WrColors.tertiaryFill : Color.clear)
            )
            .onHover { isHovered = $0 }
            .animation(.easeOut(duration: 0.12), value: isHovered)
    }
}
