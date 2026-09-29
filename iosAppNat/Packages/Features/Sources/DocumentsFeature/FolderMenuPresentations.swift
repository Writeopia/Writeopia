import SwiftUI
import WrData
import WrDesign
import WrModels

/// Sheets opened from the folder options button.
enum FolderSheet: String, Identifiable {
    case options
    case edit
    case move

    var id: String { rawValue }
}

/// What was picked in the options sheet, done once that sheet is gone.
enum FolderAction {
    case edit
    case move
    case delete
}

/// The options bottom sheet, and the sheets and confirmation it leads to.
struct FolderMenuPresentations: ViewModifier {
    /// Inside a folder (not the root), where the folder actions are offered.
    let isFolder: Bool
    let folderTitle: String
    /// Needed by the edit and move sheets only: the current name, icon and parent.
    let folder: Folder?
    let rootTitle: String
    let settings: FolderDisplaySettings
    let repository: DocumentsRepository
    @Binding var sheet: FolderSheet?
    @Binding var confirmsDeletion: Bool
    let onEdit: (_ title: String, _ icon: IconInfo?) -> Void
    let onMove: (_ folderId: String) -> Void
    let onDelete: () -> Void
    /// Only one sheet shows at a time: the next one opens when the options sheet is dismissed.
    @State private var pendingAction: FolderAction?

    func body(content: Content) -> some View {
        content
            .sheet(item: $sheet, onDismiss: runPendingAction) { sheet in
                switch sheet {
                case .options:
                    FolderOptionsSheet(settings: settings, isFolder: isFolder, folderTitle: folderTitle) { action in
                        pendingAction = action
                        self.sheet = nil
                    }
                case .edit:
                    if let folder {
                        FolderEditSheet(folder: folder, onSave: onEdit)
                            .presentationDetents([.medium, .large])
                    }
                case .move:
                    if let folder {
                        FolderPickerSheet(movingFolder: folder, rootTitle: rootTitle, repository: repository, onPick: onMove)
                    }
                }
            }
            .confirmationDialog(
                String(localized: "Delete \(folderTitle)?"),
                isPresented: $confirmsDeletion,
                titleVisibility: .visible
            ) {
                Button("Delete folder", role: .destructive, action: onDelete)
            } message: {
                Text("Folders are deleted with everything inside them. This can't be undone.")
            }
    }

    private func runPendingAction() {
        guard let action = pendingAction else { return }
        pendingAction = nil
        switch action {
        case .edit: sheet = .edit
        case .move: sheet = .move
        case .delete: confirmsDeletion = true
        }
    }
}

/// Bottom sheet with every option of the folder: how it's shown, how it's sorted and, inside a
/// folder, editing, moving and deleting it. Like the edition menu of the Compose notes list.
struct FolderOptionsSheet: View {
    @Bindable var settings: FolderDisplaySettings
    let isFolder: Bool
    let folderTitle: String
    let onAction: (FolderAction) -> Void

    var body: some View {
        NavigationStack {
            List {
                Section {
                    ArrangementPicker(selection: $settings.arrangement)
                        .listRowInsets(EdgeInsets())
                        .accessibilityIdentifier("folderOptions.arrangement")
                } header: {
                    Text("View as")
                }

                Section {
                    ForEach(DocumentsOrder.allCases, id: \.self) { order in
                        Button {
                            settings.order = order
                        } label: {
                            HStack {
                                Label(order.title, systemImage: order.systemImage)
                                Spacer()
                                if settings.order == order {
                                    Image(systemName: "checkmark")
                                        .fontWeight(.semibold)
                                        .foregroundStyle(WrColors.accent)
                                }
                            }
                            .contentShape(Rectangle())
                        }
                        .foregroundStyle(WrColors.textLight)
                        .accessibilityAddTraits(settings.order == order ? .isSelected : [])
                    }
                } header: {
                    Text("Sort by")
                }

                if isFolder {
                    Section {
                        action("Edit folder", systemImage: "pencil", id: "edit") { onAction(.edit) }
                        action("Move to…", systemImage: "folder", id: "move") { onAction(.move) }
                        Button(role: .destructive) {
                            onAction(.delete)
                        } label: {
                            Label("Delete folder", systemImage: "trash")
                        }
                        .accessibilityIdentifier("folderOptions.delete")
                    } header: {
                        Text(folderTitle)
                    }
                }
            }
            .scrollContentBackground(.hidden)
            .background(WrColors.background)
            .navigationTitle("Folder options")
            .toolbarTitleDisplayMode(.inline)
        }
        .presentationDetents(isFolder ? [.medium, .large] : [.medium])
        .presentationDragIndicator(.visible)
    }

    private func action(_ title: LocalizedStringKey, systemImage: String, id: String, perform: @escaping () -> Void) -> some View {
        Button(action: perform) {
            Label(title, systemImage: systemImage)
                .contentShape(Rectangle())
        }
        .foregroundStyle(WrColors.textLight)
        .accessibilityIdentifier("folderOptions.\(id)")
    }
}

/// "View as" choice that fills the whole row: each option is a tall segment with its icon above
/// its name, and the selected one is highlighted edge to edge.
struct ArrangementPicker: View {
    @Binding var selection: DocumentsArrangement

    var body: some View {
        HStack(spacing: 4) {
            ForEach(DocumentsArrangement.allCases, id: \.self) { arrangement in
                let isSelected = selection == arrangement
                Button {
                    withAnimation(.snappy) { selection = arrangement }
                } label: {
                    VStack(spacing: 6) {
                        Image(systemName: arrangement.systemImage)
                            .font(.title3)
                        Text(arrangement.title)
                            .font(.footnote.weight(isSelected ? .semibold : .regular))
                            .lineLimit(1)
                            .minimumScaleFactor(0.8)
                    }
                    .foregroundStyle(isSelected ? WrColors.accent : WrColors.textLight)
                    .frame(maxWidth: .infinity, minHeight: 64)
                    .background {
                        if isSelected {
                            RoundedRectangle(cornerRadius: 10, style: .continuous)
                                .fill(WrColors.accent.opacity(0.15))
                        }
                    }
                    .contentShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
                }
                .buttonStyle(.plain)
                .accessibilityLabel(Text(arrangement.title))
                .accessibilityAddTraits(isSelected ? .isSelected : [])
                .accessibilityIdentifier("folderOptions.arrangement.\(arrangement.rawValue)")
            }
        }
        .padding(4)
        .accessibilityElement(children: .contain)
    }
}
