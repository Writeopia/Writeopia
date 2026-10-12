import SwiftUI
import WrData
import WrDesign
import WrModels

/// The tags of the document as a line of names, for the side panel.
struct DocumentTagsSummary: View {
    let viewModel: NoteEditorViewModel

    var body: some View {
        if viewModel.documentTags.isEmpty {
            Text("No tags yet")
                .font(.footnote)
                .foregroundStyle(.secondary)
        } else {
            let names = viewModel.documentTags.map(\.name)
            let shown = names.prefix(3).joined(separator: ", ")
            let hidden = names.count - 3
            Text(hidden > 0 ? "\(shown), +\(hidden) more" : shown)
                .font(.footnote)
                .foregroundStyle(.secondary)
                .lineLimit(3)
                .truncationMode(.tail)
        }
    }
}

/// The tags of the document, each with its explanation, and the way to add one: from the
/// workspace catalogue, written by hand, or proposed by the cloud AI and confirmed by the user.
/// Like `DocumentTagsDialog` and `AddDocumentTagDialog` of the Kotlin app.
struct DocumentTagsSheet: View {
    @Bindable var viewModel: NoteEditorViewModel
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            WrSheetList {
                Section {
                    if viewModel.documentTags.isEmpty {
                        Text("This document has no tags. Add one from the catalogue or create a new one.")
                            .font(.footnote)
                            .foregroundStyle(.secondary)
                    } else {
                        ForEach(viewModel.documentTags) { tag in
                            HStack(alignment: .top) {
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(tag.name).font(.body.weight(.semibold)).lineLimit(1)
                                    if !tag.description.isEmpty {
                                        Text(tag.description)
                                            .font(.footnote)
                                            .foregroundStyle(.secondary)
                                            .lineLimit(3)
                                            .truncationMode(.tail)
                                    }
                                }
                                Spacer()
                                if !viewModel.isLocked {
                                    Button {
                                        viewModel.removeTag(id: tag.id)
                                    } label: {
                                        Image(systemName: "xmark.circle.fill")
                                            .foregroundStyle(.secondary)
                                    }
                                    .buttonStyle(.plain)
                                    .accessibilityLabel("Remove tag")
                                    .accessibilityIdentifier("tags.remove.\(tag.id)")
                                }
                            }
                            .padding(.vertical, 2)
                        }
                    }
                } header: {
                    Text("Tags of this document")
                }

                if !viewModel.isLocked {
                    Section {
                        NavigationLink {
                            AddDocumentTagView(viewModel: viewModel)
                        } label: {
                            Label("Add tag", systemImage: "plus")
                        }
                        .accessibilityIdentifier("tags.add")
                    }
                }
            }
            .navigationTitle("Tags")
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") { dismiss() }
                }
            }
        }
        .task { await viewModel.reloadTags() }
    }
}

/// Adds a tag: one of the catalogue, searched by name, or a new one with a name and an
/// explanation, written by the user or proposed by the AI.
private struct AddDocumentTagView: View {
    @Bindable var viewModel: NoteEditorViewModel
    @Environment(\.dismiss) private var dismiss
    @State private var query = ""
    @State private var name = ""
    @State private var explanation = ""
    @State private var isAsking = false
    @State private var error: String?

    private var available: [DocumentTag] {
        let ids = Set(viewModel.tagIds)
        let free = viewModel.workspaceTags.filter { !ids.contains($0.id) }
        guard !query.trimmingCharacters(in: .whitespaces).isEmpty else { return free }
        return free.filter { $0.name.localizedCaseInsensitiveContains(query) }
    }

    var body: some View {
        WrSheetList {
            if !viewModel.workspaceTags.isEmpty {
                Section("Pick a tag") {
                    TextField("Search tags", text: $query)
                        .textFieldStyle(.roundedBorder)
                        .accessibilityIdentifier("tags.search")
                    if available.isEmpty {
                        Text("No tag matches. Create one below.")
                            .font(.footnote)
                            .foregroundStyle(.secondary)
                    } else {
                        ForEach(available) { tag in
                            Button {
                                viewModel.addTag(id: tag.id)
                                dismiss()
                            } label: {
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(tag.name).foregroundStyle(.primary).lineLimit(1)
                                    if !tag.description.isEmpty {
                                        Text(tag.description)
                                            .font(.footnote)
                                            .foregroundStyle(.secondary)
                                            .lineLimit(3)
                                            .truncationMode(.tail)
                                    }
                                }
                            }
                            .accessibilityIdentifier("tags.pick.\(tag.id)")
                        }
                    }
                }
            }

            Section("New tag") {
                TextField("Name", text: $name)
                    .disabled(isAsking)
                    .accessibilityIdentifier("tags.name")
                TextField("What does this tag mean?", text: $explanation, axis: .vertical)
                    .lineLimit(2...4)
                    .disabled(isAsking)
                    .accessibilityIdentifier("tags.description")
                if let error {
                    Text(error)
                        .font(.footnote)
                        .foregroundStyle(.red)
                }
                if viewModel.canSuggestTagWithAi {
                    Button {
                        suggest()
                    } label: {
                        if isAsking {
                            HStack(spacing: 8) {
                                ProgressView().controlSize(.small)
                                Text("Asking the AI…")
                            }
                        } else {
                            Label("Suggest with AI", systemImage: "sparkles")
                        }
                    }
                    .disabled(isAsking)
                    .accessibilityIdentifier("tags.ai")
                }
                Button("Create and add") {
                    create()
                }
                .buttonStyle(.borderedProminent)
                .disabled(name.trimmingCharacters(in: .whitespaces).isEmpty || isAsking)
                .accessibilityIdentifier("tags.create")
            }
        }
        .navigationTitle("Add a tag")
    }

    private func suggest() {
        isAsking = true
        error = nil
        Task {
            defer { isAsking = false }
            do {
                let proposal = try await viewModel.suggestTagWithAi()
                name = proposal.name
                explanation = proposal.description
            } catch {
                self.error = String(localized: "The AI could not suggest a tag. Try again or write one.")
            }
        }
    }

    private func create() {
        Task {
            do {
                try await viewModel.createTag(name: name, description: explanation)
                dismiss()
            } catch {
                self.error = String(localized: "The tag couldn't be saved.")
            }
        }
    }
}
