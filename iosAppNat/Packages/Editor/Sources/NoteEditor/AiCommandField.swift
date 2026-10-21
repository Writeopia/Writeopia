import SwiftUI
import WrDesign

/// The field at the bottom of the Mac editor where the user types a free command to the AI,
/// like `AiCommandBar` of the Compose desktop app. It shows with the AI panel of the side
/// options and takes the focus then. Return sends the command; Option+Return breaks the line.
struct AiCommandField: View {
    let onRun: (String) -> Void
    /// Tells when the field takes or loses the keyboard focus, so the editor's own shortcuts
    /// (Backspace on selected lines) stay out of the way while the command is typed.
    var onFocusChange: (Bool) -> Void = { _ in }
    @State private var command = ""
    @FocusState private var isFocused: Bool

    private var trimmed: String { command.trimmingCharacters(in: .whitespacesAndNewlines) }

    var body: some View {
        HStack(spacing: 8) {
            TextField("Tell the AI what to do with the text", text: $command, axis: .vertical)
                .textFieldStyle(.plain)
                .lineLimit(1...4)
                .focused($isFocused)
                .onSubmit(run)
                .accessibilityIdentifier("ai.command")

            Button(action: run) {
                Image(systemName: "sparkles")
                    .font(.body.weight(.semibold))
                    .padding(6)
            }
            .buttonStyle(.plain)
            .foregroundStyle(trimmed.isEmpty ? Color.secondary : WrColors.accent)
            .disabled(trimmed.isEmpty)
            .accessibilityLabel("Run")
            .accessibilityIdentifier("ai.run")
        }
        .padding(.leading, 16)
        .padding(.trailing, 8)
        .padding(.vertical, 8)
        .glassCapsule()
        .onAppear { isFocused = true }
        .onChange(of: isFocused) { _, focused in onFocusChange(focused) }
        .onDisappear { onFocusChange(false) }
    }

    private func run() {
        guard !trimmed.isEmpty else { return }
        onRun(trimmed)
        command = ""
    }
}
