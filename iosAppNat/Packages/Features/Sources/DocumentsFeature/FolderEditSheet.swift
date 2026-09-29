import SwiftUI
import WrDesign
import WrModels

/// Edits the name and the icon of a folder, like `EditFileDialog` and the icon picker of the
/// Compose app. Without a folder it creates one, with the same name and icon choices.
struct FolderEditSheet: View {
    /// Nil to create a new folder.
    let folder: Folder?
    let onSave: (_ title: String, _ icon: IconInfo?) -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var title: String
    @State private var iconName: String?
    @State private var tint: Int?
    @FocusState private var titleFocused: Bool

    private let columns = Array(repeating: GridItem(.flexible(), spacing: 8), count: 6)

    init(folder: Folder?, onSave: @escaping (_ title: String, _ icon: IconInfo?) -> Void) {
        self.folder = folder
        self.onSave = onSave
        _title = State(initialValue: folder?.title ?? "")
        _iconName = State(initialValue: folder?.icon?.label)
        _tint = State(initialValue: folder?.icon?.tint)
    }

    private var isNew: Bool { folder == nil }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    HStack(spacing: 12) {
                        FolderIconImage(icon: icon)
                            .font(.title2)
                            .frame(width: 32)
                        TextField("Folder name", text: $title)
                            .focused($titleFocused)
                            .submitLabel(.done)
                            .onSubmit(save)
                            .accessibilityIdentifier("folderEdit.name")
                    }
                } header: {
                    Text("Name")
                }

                Section {
                    tintPicker
                    LazyVGrid(columns: columns, spacing: 8) {
                        ForEach(FolderIcons.all, id: \.name) { entry in
                            iconButton(entry.name, symbol: entry.symbol)
                        }
                    }
                    .padding(.vertical, 4)
                } header: {
                    Text("Icon")
                }
            }
            .scrollContentBackground(.hidden)
            .background(WrColors.background)
            .navigationTitle(isNew ? "New folder" : "Edit folder")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button(isNew ? "Create" : "Save", action: save)
                        .accessibilityIdentifier("folderEdit.save")
                }
            }
            .onAppear {
                if isNew { titleFocused = true }
            }
        }
    }

    private var icon: IconInfo? {
        guard iconName != nil || tint != nil else { return nil }
        return IconInfo(label: iconName ?? "folder", tint: tint)
    }

    private var tintPicker: some View {
        HStack {
            tintButton(nil)
            ForEach(FolderIcons.tints, id: \.self) { tintButton($0) }
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 4)
    }

    private func tintButton(_ value: Int?) -> some View {
        let isSelected = tint == value
        return Button {
            tint = value
        } label: {
            Circle()
                .fill(FolderIcons.color(for: value) ?? WrColors.accent)
                .frame(width: 26, height: 26)
                .overlay {
                    Circle().strokeBorder(WrColors.divider, lineWidth: 1)
                }
                .padding(3)
                .overlay {
                    if isSelected {
                        Circle().strokeBorder(WrColors.textLight, lineWidth: 2)
                    }
                }
                .frame(maxWidth: .infinity)
        }
        .buttonStyle(.plain)
        .accessibilityLabel(value == nil ? Text("Default color") : Text("Color"))
        .accessibilityAddTraits(isSelected ? .isSelected : [])
    }

    private func iconButton(_ name: String, symbol: String) -> some View {
        let isSelected = (iconName ?? "folder") == name
        return Button {
            iconName = name
        } label: {
            Image(systemName: symbol)
                .font(.title3)
                .foregroundStyle(FolderIcons.color(for: tint) ?? WrColors.accent)
                .frame(maxWidth: .infinity, minHeight: 40)
                .background(
                    isSelected ? WrColors.accent.opacity(0.15) : .clear,
                    in: RoundedRectangle(cornerRadius: 10)
                )
                .contentShape(RoundedRectangle(cornerRadius: 10))
        }
        .buttonStyle(.plain)
        .accessibilityLabel(Text(name))
        .accessibilityAddTraits(isSelected ? .isSelected : [])
    }

    private func save() {
        onSave(title, icon)
        dismiss()
    }
}
