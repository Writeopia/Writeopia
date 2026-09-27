import SwiftUI
import WrData
import WrDesign
import WrModels

/// Picks the folder to move a folder to, browsing the tree from the root. The moved folder and
/// what's inside it can't be picked.
struct FolderPickerSheet: View {
    let movingFolder: Folder
    let rootTitle: String
    let repository: DocumentsRepository
    let onPick: (_ folderId: String) -> Void
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            FolderPickerLevel(
                folderId: Folder.rootId,
                title: rootTitle,
                movingFolder: movingFolder,
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
    let movingFolder: Folder
    let repository: DocumentsRepository
    let onPick: (String) -> Void
    @State private var folders: [Folder] = []
    @State private var isLoading = true
    @State private var errorMessage: String?

    private var isCurrentParent: Bool { folderId == movingFolder.parentId }

    var body: some View {
        List {
            ForEach(folders) { folder in
                NavigationLink {
                    FolderPickerLevel(
                        folderId: folder.id,
                        title: folder.displayTitle,
                        movingFolder: movingFolder,
                        repository: repository,
                        onPick: onPick
                    )
                } label: {
                    Label {
                        Text(folder.displayTitle)
                            .foregroundStyle(WrColors.textLight)
                    } icon: {
                        FolderIconImage(icon: folder.icon)
                    }
                }
                .listRowBackground(WrColors.surface)
            }
        }
        .scrollContentBackground(.hidden)
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
        .navigationTitle(title)
        .navigationBarTitleDisplayMode(.inline)
        .safeAreaInset(edge: .bottom) {
            Button {
                onPick(folderId)
            } label: {
                Text(isCurrentParent ? String(localized: "It's already here") : String(localized: "Move here"))
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(.borderedProminent)
            .tint(WrColors.accent)
            .controlSize(.large)
            .disabled(isCurrentParent)
            .padding()
            .background(.bar)
            .accessibilityIdentifier("folderPicker.moveHere")
        }
        .task { await load() }
    }

    private func load() async {
        isLoading = true
        defer { isLoading = false }
        do {
            folders = try await repository.folderContents(folderId: folderId).folders
                .filter { $0.id != movingFolder.id }
                .sorted { $0.title.localizedCaseInsensitiveCompare($1.title) == .orderedAscending }
            errorMessage = nil
        } catch {
            errorMessage = error.userMessage
        }
    }
}
