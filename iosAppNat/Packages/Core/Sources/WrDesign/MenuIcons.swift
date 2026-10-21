import SwiftUI
import WrModels

/// Icons a folder or a document can have. The names are the ones of `WrIcons.allIcons` of the
/// Compose app, so an icon chosen on one app shows on the other; each is drawn with the closest
/// SF Symbol.
public enum MenuIcons {
    public static let all: [(name: String, symbol: String)] = [
        ("folder", "folder.fill"),
        ("file", "doc"),
        ("favorites", "heart"),
        ("home", "house"),
        ("settings", "gearshape"),
        ("search", "magnifyingglass"),
        ("notifications", "bell"),
        ("person", "person"),
        ("image", "photo"),
        ("drawing", "pencil.line"),
        ("code", "chevron.left.forwardslash.chevron.right"),
        ("link", "link"),
        ("chart", "chart.dots.scatter"),
        ("ai", "wand.and.stars"),
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
        ("download", "icloud.and.arrow.down"),
        ("fileDownload", "arrow.down.doc"),
        ("exportFile", "square.and.arrow.up"),
        ("sync", "arrow.triangle.2.circlepath"),
        ("move", "doc.on.clipboard"),
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
        ("circularArrowLeft", "arrow.left.circle"),
        ("circularArrowRight", "arrow.right.circle"),
        ("backArrowDesktop", "arrow.left"),
        ("backArrowAndroid", "arrow.backward"),
        ("smallArrowRight", "chevron.right"),
        ("smallArrowDown", "chevron.down"),
        ("moreVert", "ellipsis.circle"),
        ("moreHoriz", "ellipsis"),
        ("transparent", "drop"),
    ]

    private static let symbols = Dictionary(all.map { ($0.name, $0.symbol) }, uniquingKeysWith: { first, _ in first })

    public static func symbol(for name: String?) -> String? {
        name.flatMap { symbols[$0] }
    }

    /// Tints of `ColorUtils.tintColors()` of the Compose app, as the ARGB `Int` it stores.
    public static let tints: [Int] = [
        0xFFCC_CCCC, // LightGray
        0xFF44_4444, // DarkGray
        0xFF00_00FF, // Blue
        0xFF88_8888, // Gray
        0xFFFF_FF00, // Yellow
        0xFFFF_0000, // Red
        0xFFFF_00FF, // Magenta
    ].map { Int(Int32(bitPattern: UInt32($0))) }

    public static func color(for tint: Int?) -> Color? {
        guard let tint else { return nil }
        let argb = UInt32(truncatingIfNeeded: tint)
        return Color(
            red: Double((argb >> 16) & 0xFF) / 255,
            green: Double((argb >> 8) & 0xFF) / 255,
            blue: Double(argb & 0xFF) / 255
        )
    }
}

/// The icon of a document: the one chosen for it, in its color, or the plain page in
/// `defaultColor`. The same in the sidebar, the cards and by the title in the editor, like
/// `WrIcons.fromName` and the icon tint of the Compose app.
public struct DocumentIconImage: View {
    let icon: IconInfo?
    let defaultColor: Color

    public init(icon: IconInfo?, defaultColor: Color = WrColors.textLighter) {
        self.icon = icon
        self.defaultColor = defaultColor
    }

    public var body: some View {
        Image(systemName: MenuIcons.symbol(for: icon?.label) ?? "doc.text")
            .foregroundStyle(MenuIcons.color(for: icon?.tint) ?? defaultColor)
            .accessibilityLabel("Document icon")
    }
}
