import SwiftUI
import WrDesign

/// The commands offered while a "/" command is typed, floating under the cursor, like
/// `SlashCommandPopup` of the Compose SDK. A tap runs a command; on the Mac the arrows move the
/// highlight and Return runs the highlighted one.
struct SlashCommandPopup: View {
    let manager: WriteopiaStateManager

    static let width: CGFloat = 280
    private static let shape = RoundedRectangle(cornerRadius: 10, style: .continuous)

    var body: some View {
        let commands = manager.filteredSlashCommands
        VStack(alignment: .leading, spacing: 0) {
            Text("Commands")
                .font(.system(size: 11, weight: .medium))
                .foregroundStyle(.secondary)
                .padding(.horizontal, 12)
                .padding(.vertical, 8)

            ForEach(Array(commands.enumerated()), id: \.element.id) { index, command in
                Button {
                    manager.selectSlashCommand(command)
                } label: {
                    row(command, highlighted: isHighlighted(index))
                }
                .buttonStyle(.plain)
                .onHover { hovering in
                    if hovering { manager.highlightSlashCommand(at: index) }
                }
                .accessibilityIdentifier("editor.slashCommand.\(command.commandText.dropFirst())")
            }
        }
        .padding(.vertical, 4)
        .frame(width: Self.width)
        .background(WrColors.surface, in: Self.shape)
        .overlay(Self.shape.strokeBorder(Color.primary.opacity(0.1)))
        .shadow(color: .black.opacity(0.18), radius: 10, y: 4)
        .accessibilityIdentifier("editor.slashCommands")
    }

    /// Only the Mac draws the highlight: it is moved with the keyboard and the pointer, which
    /// touch screens don't have.
    private func isHighlighted(_ index: Int) -> Bool {
        #if os(macOS)
        manager.slashCommandHighlight == index
        #else
        false
        #endif
    }

    private func row(_ command: SlashCommand, highlighted: Bool) -> some View {
        HStack(spacing: 12) {
            Image(systemName: command.systemImage)
                .font(.system(size: 15, weight: .medium))
                .foregroundStyle(Color.primary)
                .frame(width: 20)

            VStack(alignment: .leading, spacing: 1) {
                Text(command.name)
                    .font(.system(size: 14, weight: .medium))
                    .foregroundStyle(Color.primary)
                Text(command.description)
                    .font(.system(size: 12))
                    .foregroundStyle(.secondary)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(8)
        .background(highlighted ? WrColors.accent.opacity(0.15) : Color.clear, in: RoundedRectangle(cornerRadius: 6))
        .padding(.horizontal, 8)
        .padding(.vertical, 2)
        .contentShape(Rectangle())
    }
}

extension View {
    /// Floats the popup of commands under `rect`, the cursor in the coordinates of this view,
    /// kept inside `width` so it never leaves the text column sideways. Nothing while `rect`
    /// is nil.
    func slashCommandPopup(at rect: CGRect?, width: CGFloat, manager: WriteopiaStateManager) -> some View {
        overlay(alignment: .topLeading) {
            if let rect {
                let maxX = max(0, width - SlashCommandPopup.width)
                SlashCommandPopup(manager: manager)
                    .fixedSize()
                    .offset(x: min(max(0, rect.minX), maxX), y: rect.maxY + 6)
                    .transition(.opacity.combined(with: .scale(scale: 0.95, anchor: .top)))
                    .zIndex(2)
            }
        }
        .animation(.easeOut(duration: 0.12), value: rect == nil)
    }
}
