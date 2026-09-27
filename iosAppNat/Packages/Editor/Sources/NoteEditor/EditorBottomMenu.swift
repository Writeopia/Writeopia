#if canImport(UIKit)
import SwiftUI
import WrData
import WrDesign

/// Menu under the editor, like `MobileInputScreen` of the Compose app when no text is
/// selected. Only the AI button works for now; the others are placeholders.
///
/// Drawn as a floating Liquid Glass capsule on iOS 26+, and as a material capsule before that.
struct EditorBottomMenu: View {
    let showsAi: Bool
    let onAiClick: () -> Void

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 2) {
                if showsAi {
                    MenuButton(systemImage: "sparkles", label: "AI", tint: WrColors.accent, action: onAiClick)
                        .accessibilityIdentifier("editor.menu.ai")
                    Divider()
                        .frame(height: 22)
                        .padding(.horizontal, 4)
                }
                MenuButton(systemImage: "bold", label: "Bold")
                MenuButton(systemImage: "italic", label: "Italic")
                MenuButton(systemImage: "underline", label: "Underline")
                MenuButton(systemImage: "highlighter", label: "Highlight")
                MenuButton(systemImage: "pencil.and.scribble", label: "Drawing")
                MenuButton(systemImage: "photo", label: "Image")
                MenuButton(systemImage: "tablecells", label: "Spreadsheet")
                // No undo history yet, so they look disabled like in the Compose app.
                MenuButton(systemImage: "arrow.uturn.backward", label: "Undo", isEnabled: false)
                MenuButton(systemImage: "arrow.uturn.forward", label: "Redo", isEnabled: false)
            }
            .padding(.horizontal, 10)
        }
        .frame(height: 48)
        .glassCapsule()
        .padding(.horizontal, 16)
        .padding(.bottom, 8)
    }
}

private struct MenuButton: View {
    let systemImage: String
    let label: String
    var tint: Color = .primary
    var isEnabled = true
    var action: () -> Void = {}

    var body: some View {
        Button(action: action) {
            Image(systemName: systemImage)
                .font(.system(size: 17, weight: .medium))
                .foregroundStyle(isEnabled ? tint : Color.secondary.opacity(0.5))
                .frame(width: 40, height: 40)
                .contentShape(Circle())
        }
        .buttonStyle(.plain)
        .disabled(!isEnabled)
        .accessibilityLabel(label)
    }
}

private extension View {
    @ViewBuilder
    func glassCapsule() -> some View {
        if #available(iOS 26.0, *) {
            glassEffect(.regular.interactive(), in: .capsule)
        } else {
            background(.regularMaterial, in: Capsule())
                .overlay(Capsule().strokeBorder(Color.primary.opacity(0.08)))
                .shadow(color: .black.opacity(0.12), radius: 12, y: 4)
        }
    }
}

/// Picks what the AI should do, like `MobileAiDialog` of the Compose app. Picking a command
/// closes the dialog so the answer can be seen streaming into the document.
struct AiDialog: View {
    let onCommand: (AiCommand, AiTargetMode) -> Void
    @State private var mode: AiTargetMode = .document
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            List {
                Section("Apply to:") {
                    Picker("Apply to", selection: $mode) {
                        ForEach(AiTargetMode.allCases) { mode in
                            Text(mode.title).tag(mode)
                        }
                    }
                    .pickerStyle(.segmented)
                    .labelsHidden()
                    .listRowBackground(Color.clear)
                    .listRowInsets(EdgeInsets())
                }

                Section {
                    ForEach(NoteEditorViewModel.commands(for: mode)) { command in
                        Button {
                            dismiss()
                            onCommand(command, mode)
                        } label: {
                            Label(command.title, systemImage: command.systemImage)
                        }
                        .accessibilityIdentifier("ai.\(command.rawValue)")
                    }
                }
            }
            .navigationTitle("Ask AI")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
            }
        }
        .presentationDetents([.medium])
        .tint(WrColors.accent)
    }
}

extension AiCommand {
    var title: String {
        switch self {
        case .prompt: "Prompt"
        case .summary: "Summary"
        case .actionPoints: "Action Points"
        case .faq: "FAQ"
        case .tags: "Tags"
        }
    }

    var systemImage: String {
        switch self {
        case .prompt: "text.bubble"
        case .summary: "text.alignleft"
        case .actionPoints: "checklist"
        case .faq: "questionmark.bubble"
        case .tags: "tag"
        }
    }
}
#endif
