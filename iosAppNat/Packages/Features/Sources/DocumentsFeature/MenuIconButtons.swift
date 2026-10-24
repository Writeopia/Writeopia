import SwiftUI
import WrDesign
import WrModels

/// The icon of a document in a list, opening the icon picker when clicked, like the icon of a
/// document in the side menu of the Compose app.
struct DocumentIconButton: View {
    let icon: IconInfo?
    let onPick: (IconInfo?) -> Void
    @State private var showsPicker = false

    var body: some View {
        Button {
            showsPicker = true
        } label: {
            DocumentIconImage(icon: icon, defaultColor: .secondary)
                .frame(width: 20, height: 32)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .help("Change icon")
        .accessibilityLabel("Change icon")
        .accessibilityIdentifier("documentIcon.button")
        .trailingPopover(isPresented: $showsPicker) {
            MenuIconPicker(initial: icon, onPick: onPick)
        }
    }
}

/// The icon of a folder, opening the icon and color picker when clicked, like the icon of a
/// folder card and of the side menu tree of the Compose app. The folder keeps its name; the
/// edition sheet changes both.
struct FolderIconButton: View {
    let icon: IconInfo?
    var isDropTarget = false
    let onPick: (IconInfo?) -> Void
    @State private var showsPicker = false

    var body: some View {
        Button {
            showsPicker = true
        } label: {
            FolderIconImage(icon: icon, isDropTarget: isDropTarget)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .help("Change icon")
        .accessibilityLabel("Change icon")
        .accessibilityIdentifier("folderIcon.button")
        .trailingPopover(isPresented: $showsPicker) {
            MenuIconPicker(initial: icon, removeTitle: "Default icon", removeIdentifier: "folderIcon.remove", onPick: onPick)
        }
    }
}

private extension View {
    /// A popover opening to the right of the view. The icons sit near the left edge of the
    /// screen, where a popover opening to the left is crushed. On iOS the arrow edge is the
    /// side of the popover holding the arrow, so `.leading` puts the popover on the right; iOS
    /// ignores it before iOS 18 and picks the side itself. macOS reads it as the side of the view.
    @ViewBuilder
    func trailingPopover<Content: View>(
        isPresented: Binding<Bool>,
        @ViewBuilder content: @escaping () -> Content
    ) -> some View {
        #if os(macOS)
        popover(isPresented: isPresented, arrowEdge: .trailing) {
            content()
        }
        #else
        if #available(iOS 18, *) {
            popover(isPresented: isPresented, attachmentAnchor: .rect(.bounds), arrowEdge: Edge?.some(.leading)) {
                content().presentationCompactAdaptation(.popover)
            }
        } else {
            popover(isPresented: isPresented, attachmentAnchor: .point(.trailing)) {
                content().presentationCompactAdaptation(.popover)
            }
        }
        #endif
    }
}
