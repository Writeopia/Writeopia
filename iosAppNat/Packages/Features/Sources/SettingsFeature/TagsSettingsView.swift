import SwiftUI
import WrDesign
import WrModels
import WrSession

/// The tag catalogue of the workspace: every tag with its explanation, to edit or delete, and a
/// button for a new one. Deleting asks first. Like `SettingsTagsScreen` of the Compose app.
struct TagsSettingsView: View {
    /// What the tag form is open for.
    private enum Form: Identifiable {
        case new
        case edit(DocumentTag)

        var id: String {
            switch self {
            case .new: "new"
            case .edit(let tag): tag.id
            }
        }
    }

    @State private var model: TagsSettingsModel
    @State private var form: Form?
    @State private var toDelete: DocumentTag?

    init(session: AppSession) {
        _model = State(initialValue: TagsSettingsModel(
            repository: session.documents,
            workspaceId: session.workspace?.id ?? Workspace.localId
        ))
    }

    var body: some View {
        List {
            Section {
                ForEach(model.tags) { tag in
                    row(tag)
                }
            } header: {
                Text("The tags the documents of this workspace can carry.")
                    .textCase(nil)
            } footer: {
                WrErrorText(model.errorMessage)
            }
        }
        .overlay {
            if model.tags.isEmpty && !model.isLoading {
                ContentUnavailableView {
                    Label("No tags", systemImage: "tag")
                } description: {
                    Text("Create one to organize your documents.")
                } actions: {
                    Button("New tag") { form = .new }
                        .buttonStyle(.borderedProminent)
                        .tint(WrColors.accent)
                }
            }
        }
        .navigationTitle("Tags")
        .toolbar {
            ToolbarItem(placement: .primaryAction) {
                Button {
                    form = .new
                } label: {
                    Label("New tag", systemImage: "plus")
                }
                .accessibilityIdentifier("tags.new")
            }
        }
        .task { await model.load() }
        .refreshable { await model.load() }
        .sheet(item: $form) { form in
            TagFormView(
                tag: tagOf(form),
                isNameTaken: { name in TagsSettingsModel.isNameTaken(name, in: model.tags, except: tagOf(form)?.id) },
                onSave: { name, description in
                    Task {
                        if let tag = tagOf(form) {
                            await model.edit(tag, name: name, description: description)
                        } else {
                            await model.create(name: name, description: description)
                        }
                    }
                }
            )
        }
        .alert(
            "Delete tag?",
            isPresented: Binding(get: { toDelete != nil }, set: { if !$0 { toDelete = nil } }),
            presenting: toDelete
        ) { tag in
            Button("Delete", role: .destructive) {
                Task { await model.delete(tag) }
            }
            .accessibilityIdentifier("tags.confirmDelete")
            Button("Cancel", role: .cancel) {}
        } message: { tag in
            Text("“\(tag.name)” will be removed from every document that carries it.")
        }
    }

    private func tagOf(_ form: Form) -> DocumentTag? {
        if case .edit(let tag) = form { return tag }
        return nil
    }

    private func row(_ tag: DocumentTag) -> some View {
        HStack(spacing: 12) {
            Image(systemName: "tag")
                .foregroundStyle(.secondary)

            VStack(alignment: .leading, spacing: 2) {
                Text(tag.name)
                    .lineLimit(1)
                if !tag.description.isEmpty {
                    Text(tag.description)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                        .lineLimit(2)
                }
            }

            Spacer()

            #if os(macOS)
            // The Mac has no swipe: the actions sit on the row.
            Button("Edit tag", systemImage: "pencil") { form = .edit(tag) }
                .labelStyle(.iconOnly)
                .buttonStyle(.borderless)
                .accessibilityIdentifier("tags.edit.\(tag.id)")
            Button("Delete", systemImage: "trash") { toDelete = tag }
                .labelStyle(.iconOnly)
                .buttonStyle(.borderless)
                .accessibilityIdentifier("tags.delete.\(tag.id)")
            #endif
        }
        .contentShape(Rectangle())
        .onTapGesture { form = .edit(tag) }
        .swipeActions(edge: .trailing) {
            Button("Delete", systemImage: "trash", role: .destructive) { toDelete = tag }
            Button("Edit tag", systemImage: "pencil") { form = .edit(tag) }
                .tint(WrColors.accent)
        }
        .contextMenu {
            Button("Edit tag", systemImage: "pencil") { form = .edit(tag) }
            Button("Delete", systemImage: "trash", role: .destructive) { toDelete = tag }
        }
        .accessibilityIdentifier("tags.tag.\(tag.id)")
    }
}

/// The name and the explanation of a new tag, or of `tag` being edited.
private struct TagFormView: View {
    let tag: DocumentTag?
    let isNameTaken: (String) -> Bool
    let onSave: (String, String) -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var name: String
    @State private var description: String

    init(tag: DocumentTag?, isNameTaken: @escaping (String) -> Bool, onSave: @escaping (String, String) -> Void) {
        self.tag = tag
        self.isNameTaken = isNameTaken
        self.onSave = onSave
        _name = State(initialValue: tag?.name ?? "")
        _description = State(initialValue: tag?.description ?? "")
    }

    private var isTaken: Bool { isNameTaken(name) }

    private var canSave: Bool {
        !name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty && !isTaken
    }

    var body: some View {
        NavigationStack {
            WrForm {
                Section {
                    TextField("Name", text: $name)
                        .accessibilityIdentifier("tags.form.name")
                    TextField("What does this tag mean?", text: $description, axis: .vertical)
                        .lineLimit(2...4)
                        .accessibilityIdentifier("tags.form.description")
                } footer: {
                    if isTaken {
                        Text("A tag with this name already exists.")
                            .foregroundStyle(.red)
                    }
                }
            }
            .navigationTitle(tag == nil ? "New tag" : "Edit tag")
            #if os(iOS)
            .navigationBarTitleDisplayMode(.inline)
            #endif
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button(tag == nil ? "Create" : "Save") {
                        onSave(name, description)
                        dismiss()
                    }
                    .disabled(!canSave)
                    .accessibilityIdentifier("tags.form.confirm")
                }
            }
        }
        #if os(macOS)
        .frame(minWidth: 380, minHeight: 240)
        #endif
    }
}
