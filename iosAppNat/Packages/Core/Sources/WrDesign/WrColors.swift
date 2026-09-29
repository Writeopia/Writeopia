import SwiftUI
#if canImport(UIKit)
import UIKit
#elseif canImport(AppKit)
import AppKit
#endif

/// Writeopia palette, mirrored from the Compose `WriteopiaTheme` so both apps feel the same.
///
/// `nonisolated` because SwiftUI resolves dynamic colors on its render thread, not the main actor.
nonisolated public enum WrColors {
    public static let accent = dynamic(light: 0xB7409A, dark: 0xE987D0)
    public static let background = dynamic(light: 0xF8F0F9, dark: 0x252525)
    public static let surface = dynamic(light: 0xFFFFFF, dark: 0x2F2F2F)
    public static let textLight = dynamic(light: 0x444444, dark: 0xDFDFDF)
    public static let textLighter = dynamic(light: 0x666666, dark: 0xAAAAAA)
    public static let divider = dynamic(light: 0xE0E0E0, dark: 0x616161)

    /// The background of the system (`systemBackground` on iOS, the window background on macOS).
    public static var systemBackground: Color {
        #if canImport(UIKit)
        Color(uiColor: .systemBackground)
        #else
        Color(nsColor: .windowBackgroundColor)
        #endif
    }

    /// A raised surface inside the content (`secondarySystemBackground` on iOS).
    public static var secondaryBackground: Color {
        #if canImport(UIKit)
        Color(uiColor: .secondarySystemBackground)
        #else
        Color(nsColor: .controlBackgroundColor)
        #endif
    }

    /// Thin lines between content (`separator` on iOS).
    public static var separator: Color {
        #if canImport(UIKit)
        Color(uiColor: .separator)
        #else
        Color(nsColor: .separatorColor)
        #endif
    }

    /// A faint fill behind unselected chips (`tertiarySystemFill` on iOS).
    public static var tertiaryFill: Color {
        #if canImport(UIKit)
        Color(uiColor: .tertiarySystemFill)
        #else
        Color(nsColor: .quaternaryLabelColor).opacity(0.5)
        #endif
    }

    /// A subtle fill for placeholders (`secondarySystemFill` on iOS).
    public static var secondaryFill: Color {
        #if canImport(UIKit)
        Color(uiColor: .secondarySystemFill)
        #else
        Color(nsColor: .quaternaryLabelColor)
        #endif
    }

    private static func dynamic(light: UInt32, dark: UInt32) -> Color {
        #if canImport(UIKit)
        Color(UIColor { traits in
            traits.userInterfaceStyle == .dark ? UIColor(hex: dark) : UIColor(hex: light)
        })
        #else
        Color(NSColor(name: nil) { appearance in
            appearance.bestMatch(from: [.aqua, .darkAqua]) == .darkAqua ? NSColor(hex: dark) : NSColor(hex: light)
        })
        #endif
    }
}

#if canImport(UIKit)
nonisolated extension UIColor {
    convenience init(hex: UInt32) {
        self.init(
            red: CGFloat((hex >> 16) & 0xFF) / 255,
            green: CGFloat((hex >> 8) & 0xFF) / 255,
            blue: CGFloat(hex & 0xFF) / 255,
            alpha: 1
        )
    }
}
#elseif canImport(AppKit)
nonisolated extension NSColor {
    convenience init(hex: UInt32) {
        self.init(
            srgbRed: CGFloat((hex >> 16) & 0xFF) / 255,
            green: CGFloat((hex >> 8) & 0xFF) / 255,
            blue: CGFloat(hex & 0xFF) / 255,
            alpha: 1
        )
    }
}
#endif

nonisolated extension Color {
    init(hex: UInt32) {
        self.init(
            red: Double((hex >> 16) & 0xFF) / 255,
            green: Double((hex >> 8) & 0xFF) / 255,
            blue: Double(hex & 0xFF) / 255
        )
    }
}
