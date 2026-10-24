import SwiftUI

/// Tells what a drop into a folder moved, with a button to undo it, like the snackbar of the
/// Compose app. It goes away by itself after a while; a newer drop replaces it.
struct DropUndoBanner: View {
    let drop: FolderContentsViewModel.DroppedMove
    let onUndo: () -> Void
    let onTimeout: () -> Void

    var body: some View {
        HStack(spacing: 12) {
            Text("“\(drop.itemTitle)” moved to “\(drop.folderTitle)”")
                .font(.subheadline.weight(.semibold))
                .lineLimit(1)
                .truncationMode(.middle)
            Button("Undo", action: onUndo)
                .font(.subheadline.weight(.bold))
                .buttonStyle(.plain)
                .foregroundStyle(.tint)
                .accessibilityIdentifier("documents.drop.undo")
        }
        .padding(.horizontal, 16)
        .frame(height: 44)
        .background(.regularMaterial, in: Capsule())
        .padding(.horizontal)
        .padding(.bottom, 8)
        .task(id: drop.id) {
            try? await Task.sleep(for: .seconds(8))
            if !Task.isCancelled { onTimeout() }
        }
    }
}
