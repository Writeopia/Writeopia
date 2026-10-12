import SwiftUI
import WrDesign
import WrModels

/// The box at the bottom right with what the writing agent suggests, like `WritingSuggestionsBox`
/// of the Kotlin app. Each suggestion has a button that applies it. The box is hidden while there
/// is nothing to suggest. The user can fold it down to a small "AI suggestions" badge with the
/// count, to focus on the text, and open it again from the badge.
struct WritingSuggestionsBox: View {
    let controller: WritingAgentController

    var body: some View {
        let state = controller.state
        if state.hasSuggestions {
            Group {
                if controller.collapsed {
                    CollapsedBadge(count: state.count, isLoading: state.isLoading) {
                        withAnimation(.snappy) { controller.toggleCollapsed() }
                    }
                } else {
                    ExpandedBox(controller: controller, state: state)
                }
            }
            .transition(.move(edge: .bottom).combined(with: .opacity))
        }
    }
}

/// The folded box: the AI icon, "AI suggestions" and how many there are. A click unfolds it.
private struct CollapsedBadge: View {
    let count: Int
    let isLoading: Bool
    let onExpand: () -> Void

    var body: some View {
        Button(action: onExpand) {
            HStack(spacing: 8) {
                if isLoading {
                    ProgressView().controlSize(.small)
                } else {
                    Image(systemName: "sparkles")
                        .foregroundStyle(WrColors.accent)
                }
                Text("AI suggestions")
                    .font(.footnote.weight(.medium))
                    .foregroundStyle(.primary)
                Text("\(count)")
                    .font(.caption2.weight(.semibold))
                    .foregroundStyle(.white)
                    .padding(.horizontal, 7)
                    .padding(.vertical, 2)
                    .background(WrColors.accent, in: RoundedRectangle(cornerRadius: 6))
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 8)
            .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 12))
            .shadow(color: .black.opacity(0.1), radius: 8, y: 2)
        }
        .buttonStyle(.plain)
        .accessibilityLabel("Show the suggestions")
        .accessibilityIdentifier("writingAgent.badge")
    }
}

private struct ExpandedBox: View {
    let controller: WritingAgentController
    let state: WritingSuggestionsState

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 8) {
                if state.isLoading {
                    ProgressView().controlSize(.small)
                } else {
                    Image(systemName: "sparkles")
                        .foregroundStyle(WrColors.accent)
                }
                Text("Suggestions")
                    .font(.footnote.weight(.medium))
                Spacer(minLength: 12)
                Button {
                    withAnimation(.snappy) { controller.toggleCollapsed() }
                } label: {
                    Image(systemName: "chevron.down")
                }
                .buttonStyle(.borderless)
                .accessibilityLabel("Hide the suggestions")
                .accessibilityIdentifier("writingAgent.collapse")
                Button {
                    withAnimation(.snappy) { controller.dismiss() }
                } label: {
                    Image(systemName: "xmark")
                }
                .buttonStyle(.borderless)
                .accessibilityLabel("Dismiss")
                .accessibilityIdentifier("writingAgent.dismiss")
            }
            .foregroundStyle(.secondary)

            ForEach(state.suggestions) { suggestion in
                SuggestionRow(label: suggestion.action.label, identifier: "writingAgent.\(suggestion.action.rawValue)") {
                    controller.execute(suggestion.action)
                }
            }
            if state.offerFolder {
                SuggestionRow(label: String(localized: "Move to a folder"), identifier: "writingAgent.folder") {
                    controller.openFolderDialog()
                }
            }
            if state.offerTags {
                SuggestionRow(label: String(localized: "Add tags"), identifier: "writingAgent.tags") {
                    controller.openTagDialog()
                }
            }
        }
        .padding(12)
        .frame(minWidth: 220, maxWidth: 320, alignment: .leading)
        .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 12))
        .shadow(color: .black.opacity(0.1), radius: 8, y: 2)
        .accessibilityIdentifier("writingAgent.box")
    }
}

private struct SuggestionRow: View {
    let label: String
    let identifier: String
    let onApply: () -> Void

    var body: some View {
        HStack(spacing: 8) {
            Text(label)
                .font(.footnote)
                .lineLimit(2)
                .frame(maxWidth: .infinity, alignment: .leading)
            Button("Apply", action: onApply)
                .font(.footnote.weight(.semibold))
                .buttonStyle(.borderless)
                .accessibilityIdentifier(identifier)
        }
        .padding(.vertical, 6)
        .contentShape(Rectangle())
        .onTapGesture(perform: onApply)
    }
}

extension WritingSuggestionAction {
    var label: String {
        switch self {
        case .codeBlock: String(localized: "Add a code block")
        case .list: String(localized: "Add a bullet list")
        case .checkList: String(localized: "Add a checklist")
        case .newDocumentLink: String(localized: "Create a linked document")
        case .image: String(localized: "Draw an image")
        case .drawing: String(localized: "Add a drawing")
        case .spreadsheet: String(localized: "Turn into a table")
        case .sectionHeading: String(localized: "Add a section heading")
        case .documentTitle: String(localized: "Give the document a title")
        case .callout: String(localized: "Highlight as a callout")
        case .tldr: String(localized: "Add a TL;DR at the top")
        case .conclusion: String(localized: "Write a conclusion")
        }
    }
}

/// The dialog that files a document sitting in the root folder, like `FolderSuggestionDialog` of
/// the Kotlin app: a spinner while the backend judges the folders, the ones that fit to pick, and
/// a new folder named by the AI.
struct FolderSuggestionDialog: View {
    let controller: WritingAgentController
    let dialog: FolderDialogState

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            HStack(spacing: 10) {
                Image(systemName: "folder.badge.plus")
                    .foregroundStyle(WrColors.accent)
                Text("Where should this document live?")
                    .font(.headline)
            }
            Text("This document is in the root folder. Pick a folder for it, or let the AI create one.")
                .font(.subheadline)
                .foregroundStyle(.secondary)

            Group {
                if dialog.isCreating {
                    progress("Creating a folder…")
                } else if dialog.isLoading {
                    progress("Looking at your folders…")
                } else if dialog.options.isEmpty {
                    Text("None of your folders seems to fit this document.")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                } else {
                    VStack(spacing: 4) {
                        ForEach(dialog.options) { folder in
                            Button {
                                controller.moveToFolder(folder.folderId)
                            } label: {
                                HStack {
                                    Image(systemName: "folder")
                                        .foregroundStyle(.secondary)
                                    Text(folder.name)
                                        .lineLimit(1)
                                    Spacer()
                                    Text("\(Int(folder.probability * 100))%")
                                        .font(.caption)
                                        .foregroundStyle(.secondary)
                                }
                                .padding(.horizontal, 8)
                                .padding(.vertical, 10)
                                .contentShape(Rectangle())
                            }
                            .buttonStyle(.plain)
                            .background(WrColors.secondaryFill, in: RoundedRectangle(cornerRadius: 8))
                            .accessibilityIdentifier("writingAgent.folder.\(folder.folderId)")
                        }
                    }
                }
            }

            Spacer(minLength: 0)

            HStack {
                Button("Not now") { controller.dismissFolderDialog() }
                    .disabled(dialog.isCreating)
                    .accessibilityIdentifier("writingAgent.folder.notNow")
                Spacer()
                Button("Create a new folder") { controller.createFolder() }
                    .buttonStyle(.borderedProminent)
                    .disabled(dialog.isLoading || dialog.isCreating)
                    .accessibilityIdentifier("writingAgent.folder.new")
            }
        }
        .padding(20)
        .interactiveDismissDisabled(dialog.isCreating)
    }

    private func progress(_ label: LocalizedStringKey) -> some View {
        HStack(spacing: 10) {
            ProgressView().controlSize(.small)
            Text(label)
                .font(.footnote)
                .foregroundStyle(.secondary)
        }
    }
}

/// The dialog that tags a document without tags, like `TagSuggestionDialog` of the Kotlin app: a
/// spinner while the backend judges the catalogue, the tags that fit to keep or drop, and a new
/// tag made up by the AI.
struct TagSuggestionDialog: View {
    let controller: WritingAgentController
    let dialog: TagDialogState

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            HStack(spacing: 10) {
                Image(systemName: "tag.fill")
                    .foregroundStyle(WrColors.accent)
                Text("Which tags fit this document?")
                    .font(.headline)
            }
            Text("This document has no tags. Pick the ones that fit, or let the AI create one.")
                .font(.subheadline)
                .foregroundStyle(.secondary)

            Group {
                if dialog.isCreating {
                    progress("Creating a tag…")
                } else if dialog.isLoading {
                    progress("Looking at your tags…")
                } else if dialog.catalogueIsEmpty {
                    Text("This workspace has no tags yet.")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                } else if dialog.options.isEmpty {
                    Text("None of your tags seems to fit this document.")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                } else {
                    ScrollView {
                        VStack(spacing: 4) {
                            ForEach(dialog.options) { tag in
                                let selected = dialog.selected.contains(tag.tagId)
                                Button {
                                    controller.toggleTag(tag.tagId)
                                } label: {
                                    HStack(alignment: .top) {
                                        Image(systemName: selected ? "checkmark.circle.fill" : "circle")
                                            .foregroundStyle(selected ? WrColors.accent : .secondary)
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
                                        Text("\(Int(tag.probability * 100))%")
                                            .font(.caption)
                                            .foregroundStyle(.secondary)
                                    }
                                    .padding(.horizontal, 8)
                                    .padding(.vertical, 10)
                                    .contentShape(Rectangle())
                                }
                                .buttonStyle(.plain)
                                .background(WrColors.secondaryFill, in: RoundedRectangle(cornerRadius: 8))
                                .accessibilityIdentifier("writingAgent.tag.\(tag.tagId)")
                            }
                        }
                    }
                }
            }

            Spacer(minLength: 0)

            HStack {
                Button("Not now") { controller.dismissTagDialog() }
                    .disabled(dialog.isCreating)
                    .accessibilityIdentifier("writingAgent.tags.notNow")
                Spacer()
                Button("Create with AI") { controller.createTagWithAi() }
                    .disabled(dialog.isLoading || dialog.isCreating)
                    .accessibilityIdentifier("writingAgent.tags.new")
                Button("Add selected") { controller.applyTags() }
                    .buttonStyle(.borderedProminent)
                    .disabled(dialog.isLoading || dialog.isCreating || dialog.selected.isEmpty)
                    .accessibilityIdentifier("writingAgent.tags.add")
            }
        }
        .padding(20)
        .interactiveDismissDisabled(dialog.isCreating)
    }

    private func progress(_ label: LocalizedStringKey) -> some View {
        HStack(spacing: 10) {
            ProgressView().controlSize(.small)
            Text(label)
                .font(.footnote)
                .foregroundStyle(.secondary)
        }
    }
}
