import SwiftUI
import WrDesign
import WrModels

/// The icon catalogue lives in `WrDesign`, shared with the editor, which shows the icon of a
/// document by its title.
typealias FolderIcons = MenuIcons

/// The icon of a folder: the one chosen in the edition menu, or the default folder.
struct FolderIconImage: View {
    let icon: IconInfo?
    var isDropTarget = false

    var body: some View {
        Image(systemName: symbol)
            .foregroundStyle(FolderIcons.color(for: icon?.tint) ?? WrColors.accent)
    }

    private var symbol: String {
        if isDropTarget { return "folder.fill.badge.plus" }
        return FolderIcons.symbol(for: icon?.label) ?? "folder.fill"
    }
}
