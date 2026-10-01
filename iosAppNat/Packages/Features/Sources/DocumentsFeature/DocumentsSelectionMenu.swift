import SwiftUI
import WrDesign

/// Menu shown while documents are selected, like `NotesSelectionMenu` of the Compose app:
/// copy, favorite, AI summary, delete and close.
struct DocumentsSelectionMenu: View {
    let count: Int
    let isFavorite: Bool
    let showsCopy: Bool
    let showsSummary: Bool
    let onCopy: () -> Void
    let onFavorite: () -> Void
    let onSummary: () -> Void
    /// Opens the folder picker to move the selection.
    let onMove: () -> Void
    /// Called once the deletion is confirmed.
    let onDelete: () -> Void
    let onClose: () -> Void
    @State private var confirmsDelete = false

    var body: some View {
        HStack(spacing: 2) {
            if showsCopy {
                button("doc.on.doc", label: "Copy", id: "copy", action: onCopy)
            }
            button(isFavorite ? "star.slash" : "star", label: isFavorite ? "Remove from favorites" : "Favorite", id: "favorite", action: onFavorite)
            if showsSummary {
                button("sparkles", label: "AI Summary", id: "summary", tint: WrColors.accent, action: onSummary)
            }
            button("arrow.forward.folder", label: "Move to…", id: "move", action: onMove)
            button("trash", label: "Delete", id: "delete", tint: .red) { confirmsDelete = true }
                // Attached to the button so the confirmation shows right above it.
                .confirmationDialog(
                    count == 1 ? String(localized: "Delete this item?") : String(localized: "Delete \(count) items?"),
                    isPresented: $confirmsDelete,
                    titleVisibility: .visible
                ) {
                    Button("Delete", role: .destructive, action: onDelete)
                } message: {
                    Text("Folders are deleted with everything inside them. This can't be undone.")
                }

            Divider()
                .frame(height: 22)
                .padding(.horizontal, 6)

            Button(action: onClose) {
                HStack(spacing: 6) {
                    Text("\(count) selected")
                        .font(.subheadline.weight(.semibold))
                        .monospacedDigit()
                        .contentTransition(.numericText())
                    Image(systemName: "xmark")
                        .font(.caption.weight(.bold))
                }
                .foregroundStyle(WrColors.accent)
                .padding(.horizontal, 12)
                .frame(height: 32)
                .background(WrColors.accent.opacity(0.15), in: Capsule())
                .animation(.snappy, value: count)
            }
            .buttonStyle(.plain)
            .accessibilityLabel(Text("Unselect \(count) items"))
            .accessibilityIdentifier("documents.selection.close")
        }
        .padding(.horizontal, 10)
        .frame(height: 48)
        .selectionGlass()
        .padding(.horizontal, 16)
        .padding(.bottom, 8)
    }

    private func button(
        _ systemImage: String,
        label: LocalizedStringKey,
        id: String,
        tint: Color = .primary,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            Image(systemName: systemImage)
                .font(.system(size: 17, weight: .medium))
                .foregroundStyle(tint)
                .frame(width: 44, height: 40)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(label)
        .accessibilityIdentifier("documents.selection.\(id)")
    }
}

private extension View {
    @ViewBuilder
    func selectionGlass() -> some View {
        if #available(iOS 26.0, *) {
            glassEffect(.regular.interactive(), in: .capsule)
        } else {
            background(.regularMaterial, in: Capsule())
                .overlay(Capsule().strokeBorder(Color.primary.opacity(0.08)))
                .shadow(color: .black.opacity(0.12), radius: 12, y: 4)
        }
    }
}
