import SwiftUI
import WrDesign
import WrModels

/// Icons a folder can have. The names are the ones of `WrIcons.allIcons` of the Compose app, so
/// an icon chosen on one app shows on the other; each is drawn with the closest SF Symbol.
enum FolderIcons {
    static let all: [(name: String, symbol: String)] = [
        ("folder", "folder.fill"),
        ("file", "doc"),
        ("favorites", "star"),
        ("home", "house"),
        ("settings", "gearshape"),
        ("search", "magnifyingglass"),
        ("notifications", "bell"),
        ("person", "person"),
        ("image", "photo"),
        ("drawing", "scribble.variable"),
        ("code", "chevron.left.forwardslash.chevron.right"),
        ("link", "link"),
        ("chart", "chart.bar"),
        ("ai", "sparkles"),
        ("zap", "bolt"),
        ("target", "scope"),
        ("play", "play"),
        ("command", "command"),
        ("highlight", "highlighter"),
        ("textStyle", "textformat"),
        ("pageStyle", "doc.richtext"),
        ("bold", "bold"),
        ("italic", "italic"),
        ("underline", "underline"),
        ("save", "square.and.arrow.down"),
        ("download", "arrow.down.circle"),
        ("fileDownload", "arrow.down.doc"),
        ("exportFile", "square.and.arrow.up"),
        ("sync", "arrow.triangle.2.circlepath"),
        ("move", "arrow.up.and.down.and.arrow.left.and.right"),
        ("sort", "arrow.up.arrow.down"),
        ("sortByName", "textformat.abc"),
        ("sortByCreated", "calendar.badge.plus"),
        ("sortByUpdate", "clock.arrow.circlepath"),
        ("colorModeLight", "sun.max"),
        ("colorModeDark", "moon"),
        ("colorModeSystem", "circle.lefthalf.filled"),
        ("visibilityOn", "eye"),
        ("visibilityOff", "eye.slash"),
        ("layoutGrid", "square.grid.2x2"),
        ("layoutStaggeredGrid", "rectangle.3.group"),
        ("layoutList", "list.bullet"),
        ("add", "plus"),
        ("addCircle", "plus.circle"),
        ("close", "xmark"),
        ("delete", "trash"),
        ("undo", "arrow.uturn.backward"),
        ("redo", "arrow.uturn.forward"),
        ("circularArrowLeft", "arrow.counterclockwise"),
        ("circularArrowRight", "arrow.clockwise"),
        ("backArrowDesktop", "arrow.left"),
        ("backArrowAndroid", "arrow.backward"),
        ("smallArrowRight", "chevron.right"),
        ("smallArrowDown", "chevron.down"),
        ("moreVert", "ellipsis.circle"),
        ("moreHoriz", "ellipsis"),
        ("transparent", "circle.dashed"),
    ]

    private static let symbols = Dictionary(all.map { ($0.name, $0.symbol) }, uniquingKeysWith: { first, _ in first })

    static func symbol(for name: String?) -> String? {
        name.flatMap { symbols[$0] }
    }

    /// Tints of `ColorUtils.tintColors()` of the Compose app, as the ARGB `Int` it stores.
    static let tints: [Int] = [
        0xFFCC_CCCC, // LightGray
        0xFF44_4444, // DarkGray
        0xFF00_00FF, // Blue
        0xFF88_8888, // Gray
        0xFFFF_FF00, // Yellow
        0xFFFF_0000, // Red
        0xFFFF_00FF, // Magenta
    ].map { Int(Int32(bitPattern: UInt32($0))) }

    static func color(for tint: Int?) -> Color? {
        guard let tint else { return nil }
        let argb = UInt32(truncatingIfNeeded: tint)
        return Color(
            red: Double((argb >> 16) & 0xFF) / 255,
            green: Double((argb >> 8) & 0xFF) / 255,
            blue: Double(argb & 0xFF) / 255
        )
    }
}

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
