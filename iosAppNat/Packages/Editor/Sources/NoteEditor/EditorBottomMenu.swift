#if canImport(UIKit)
import SwiftUI
import Writeopia
import WriteopiaUI
import WrData
import WrDesign

/// Menu under the editor, like `MobileInputScreen` of the Compose app when no text is
/// selected. AI and the text formats work; drawing, image, spreadsheet, undo and redo are
/// placeholders for now.
///
/// Drawn as a floating Liquid Glass capsule on iOS 26+, and as a material capsule before that.
struct EditorBottomMenu: View {
    let manager: WriteopiaStateManager
    let showsAi: Bool
    let onAiClick: () -> Void
    let onLinkClick: () -> Void
    @State private var showsHighlightColors = false

    var body: some View {
        VStack(spacing: 8) {
            if showsHighlightColors {
                HighlightColors(manager: manager)
                    .transition(.move(edge: .bottom).combined(with: .opacity))
            }

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 2) {
                    if manager.hasSelectedLines {
                        SelectedLinesChip(count: manager.selectedPositions.count) {
                            withAnimation(.snappy) { manager.clearLineSelection() }
                        }
                        Divider()
                            .frame(height: 22)
                            .padding(.horizontal, 4)
                    }
                    if showsAi {
                        MenuButton(systemImage: "sparkles", label: "AI", tint: WrColors.accent, action: onAiClick)
                            .accessibilityIdentifier("editor.menu.ai")
                        Divider()
                            .frame(height: 22)
                            .padding(.horizontal, 4)
                    }
                    spanButton(.bold, systemImage: "bold", label: "Bold")
                    spanButton(.italic, systemImage: "italic", label: "Italic")
                    spanButton(.underline, systemImage: "underline", label: "Underline")
                    MenuButton(
                        systemImage: "highlighter",
                        label: "Highlight",
                        isActive: showsHighlightColors || manager.isHighlightActive
                    ) {
                        withAnimation(.snappy) { showsHighlightColors.toggle() }
                    }
                    .accessibilityIdentifier("editor.menu.highlight")
                    MenuButton(systemImage: "link", label: "Link", isActive: manager.isSpanActive(.link), action: onLinkClick)
                        .accessibilityIdentifier("editor.menu.link")
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
        }
        .padding(.horizontal, 16)
        .padding(.bottom, 8)
    }

    private func spanButton(_ span: Span, systemImage: String, label: String) -> some View {
        MenuButton(systemImage: systemImage, label: label, isActive: manager.isSpanActive(span)) {
            manager.toggleSpan(span)
        }
        .accessibilityIdentifier("editor.menu.\(span.rawValue.lowercased())")
    }
}

/// Shown while lines are selected by sliding them: how many, and a tap to unselect them all.
private struct SelectedLinesChip: View {
    let count: Int
    let clear: () -> Void

    var body: some View {
        Button(action: clear) {
            HStack(spacing: 6) {
                Text("\(count) selected")
                    .font(.subheadline.weight(.semibold))
                    .monospacedDigit()
                Image(systemName: "xmark")
                    .font(.caption.weight(.bold))
            }
            .foregroundStyle(WrColors.accent)
            .padding(.horizontal, 12)
            .frame(height: 32)
            .background(WrColors.accent.opacity(0.15), in: Capsule())
        }
        .buttonStyle(.plain)
        .accessibilityLabel("Unselect \(count) lines")
        .accessibilityIdentifier("editor.menu.clearSelection")
    }
}

/// The highlight colors, shown above the menu like the color row of the Compose app.
private struct HighlightColors: View {
    let manager: WriteopiaStateManager

    var body: some View {
        HStack(spacing: 14) {
            ForEach(Span.highlights, id: \.self) { span in
                let isActive = manager.isSpanActive(span)
                Button {
                    manager.toggleSpan(span)
                } label: {
                    Circle()
                        .fill(span.color)
                        .frame(width: 26, height: 26)
                        .overlay {
                            Circle().strokeBorder(Color.primary.opacity(isActive ? 0.8 : 0.15), lineWidth: isActive ? 2 : 1)
                        }
                        .padding(4)
                        .contentShape(Circle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel(span.colorName)
                .accessibilityAddTraits(isActive ? .isSelected : [])
            }
        }
        .padding(.horizontal, 12)
        .frame(height: 44)
        .glassCapsule()
    }
}

private extension Span {
    var color: Color {
        switch self {
        case .highlightGreen: .green.opacity(0.55)
        case .highlightRed: .red.opacity(0.5)
        default: .yellow.opacity(0.6)
        }
    }

    var colorName: String {
        switch self {
        case .highlightGreen: "Green highlight"
        case .highlightRed: "Red highlight"
        default: "Yellow highlight"
        }
    }
}

private struct MenuButton: View {
    let systemImage: String
    let label: String
    var tint: Color = .primary
    var isEnabled = true
    var isActive = false
    var action: () -> Void = {}

    var body: some View {
        Button(action: action) {
            Image(systemName: systemImage)
                .font(.system(size: 17, weight: isActive ? .bold : .medium))
                .foregroundStyle(isActive ? WrColors.accent : isEnabled ? tint : Color.secondary.opacity(0.5))
                .frame(width: 40, height: 40)
                .background(Circle().fill(isActive ? WrColors.accent.opacity(0.15) : Color.clear))
                .contentShape(Circle())
        }
        .buttonStyle(.plain)
        .disabled(!isEnabled)
        .accessibilityLabel(label)
        .accessibilityAddTraits(isActive ? .isSelected : [])
        .animation(.easeInOut(duration: 0.15), value: isActive)
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
