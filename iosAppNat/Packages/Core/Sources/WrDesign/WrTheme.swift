import SwiftUI
#if canImport(UIKit)
import UIKit
#elseif canImport(AppKit)
import AppKit
#endif

/// Design tokens of the v4 redesign, mirrored from the Figma file (page "01 Foundations · v4",
/// collections `Color · v4` and `Dimension · v4`). Names follow the Figma variables, so
/// `bg/raised` is `WrTheme.Colors.bgRaised` and `space/300` is `WrTheme.Space.s300`.
///
/// Every color has a light and a dark value and follows the appearance of the view it's drawn
/// in, so a screen can switch part of itself to dark with `.environment(\.colorScheme, .dark)`.
///
/// `WrColors` stays as it is for the screens that aren't on v4 yet.
nonisolated public enum WrTheme {

    // MARK: Colors

    nonisolated public enum Colors {
        // Backgrounds
        public static let bgApp = dynamic(light: 0xF2F2F2, dark: 0x141414)
        public static let bgCanvas = dynamic(light: 0xFFFFFF, dark: 0x1C1C1C)
        public static let bgSubtle = dynamic(light: 0xFAFAFA, dark: 0x242424)
        public static let bgSunken = dynamic(light: 0xF2F2F2, dark: 0x141414)
        public static let bgRaised = dynamic(light: 0xFFFFFF, dark: 0x242424)
        public static let bgHover = dynamic(light: 0xF2F2F2, dark: 0x2E2E2E)
        public static let bgPressed = dynamic(light: 0xE5E5E5, dark: 0x383838)
        public static let bgSelected = dynamic(light: 0xF9F4FF, dark: 0x391255)
        public static let bgInverse = dynamic(light: 0x242424, dark: 0xFAFAFA)

        // Text
        public static let textPrimary = dynamic(light: 0x242424, dark: 0xFAFAFA)
        public static let textSecondary = dynamic(light: 0x4D4D4D, dark: 0xD1D1D1)
        public static let textTertiary = dynamic(light: 0x676767, dark: 0xACACAC)
        public static let textDisabled = dynamic(light: 0x858585, dark: 0x676767)
        public static let textOnAccent = dynamic(light: 0xFFFFFF, dark: 0x290441)
        public static let textLink = dynamic(light: 0x662D91, dark: 0xD4AAFC)
        public static let textInverse = dynamic(light: 0xFFFFFF, dark: 0x242424)

        // Icons
        public static let iconPrimary = dynamic(light: 0x383838, dark: 0xF2F2F2)
        public static let iconSecondary = dynamic(light: 0x676767, dark: 0xACACAC)
        public static let iconTertiary = dynamic(light: 0x858585, dark: 0x858585)

        // Borders
        public static let borderSubtle = dynamic(light: 0xE5E5E5, dark: 0x2E2E2E)
        public static let borderDefault = dynamic(light: 0xD1D1D1, dark: 0x383838)
        public static let borderStrong = dynamic(light: 0x858585, dark: 0x676767)
        public static let borderFocus = dynamic(light: 0x9C6BC8, dark: 0xB88BE3)

        // Accent (brand violet)
        public static let accent = dynamic(light: 0x662D91, dark: 0xD4AAFC)
        public static let accentHover = dynamic(light: 0x501777, dark: 0xE5CDFF)
        public static let accentPressed = dynamic(light: 0x391255, dark: 0xF1E5FF)
        public static let accentSubtle = dynamic(light: 0xF9F4FF, dark: 0x391255)
        public static let accentSubtleHover = dynamic(light: 0xF1E5FF, dark: 0x501777)
        public static let accentOnSubtle = dynamic(light: 0x662D91, dark: 0xE5CDFF)
        public static let accentBorder = dynamic(light: 0xD4AAFC, dark: 0x814CAD)

        // AI (brand magenta, the "spark")
        public static let ai = dynamic(light: 0xB818AF, dark: 0xFF99F4)
        public static let aiSubtle = dynamic(light: 0xFFF3FD, dark: 0x370034)
        public static let aiOnSubtle = dynamic(light: 0x92008B, dark: 0xFFC5F7)

        // Brand
        public static let brandViolet = dynamic(light: 0x662D91, dark: 0xB88BE3)
        public static let brandMagenta = dynamic(light: 0xFF55F3, dark: 0xFF55F3)
        public static let brandInk = dynamic(light: 0x383838, dark: 0xFAFAFA)

        // Status
        public static let danger = dynamic(light: 0xD92D20, dark: 0xFF8170)
        public static let dangerSubtle = dynamic(light: 0xFFF4F2, dark: 0x430000)
        public static let dangerBorder = dynamic(light: 0xFFCEC5, dark: 0x880200)
        public static let success = dynamic(light: 0x007B3E, dark: 0x68CC88)
        public static let successSubtle = dynamic(light: 0xE6FEEC, dark: 0x002A11)
        public static let warning = dynamic(light: 0x895400, dark: 0xF4A33D)
        public static let warningSubtle = dynamic(light: 0xFFF5EB, dark: 0x2E1900)
        public static let info = dynamic(light: 0x2F6FEB, dark: 0x7CAAFF)
        public static let infoSubtle = dynamic(light: 0xF2F7FF, dark: 0x001852)

        // Overlays and shadows
        public static let scrim = dynamic(light: 0x000000, dark: 0x000000, lightAlpha: 0.32, darkAlpha: 0.56)
        public static let shadowKey = dynamic(light: 0x140A1F, dark: 0x000000, lightAlpha: 0.10, darkAlpha: 0.48)
        public static let shadowAmbient = dynamic(light: 0x140A1F, dark: 0x000000, lightAlpha: 0.06, darkAlpha: 0.32)

        static func dynamic(light: UInt32, dark: UInt32, lightAlpha: CGFloat = 1, darkAlpha: CGFloat = 1) -> Color {
            #if canImport(UIKit)
            Color(UIColor { traits in
                traits.userInterfaceStyle == .dark
                    ? UIColor(hex: dark).withAlphaComponent(darkAlpha)
                    : UIColor(hex: light).withAlphaComponent(lightAlpha)
            })
            #else
            Color(NSColor(name: nil) { appearance in
                appearance.bestMatch(from: [.aqua, .darkAqua]) == .darkAqua
                    ? NSColor(hex: dark).withAlphaComponent(darkAlpha)
                    : NSColor(hex: light).withAlphaComponent(lightAlpha)
            })
            #endif
        }
    }

    // MARK: Dimensions (8 pt grid, with 4 for tight spots)

    nonisolated public enum Space {
        public static let s0: CGFloat = 0
        public static let s50: CGFloat = 4
        public static let s100: CGFloat = 8
        public static let s150: CGFloat = 12
        public static let s200: CGFloat = 16
        public static let s300: CGFloat = 24
        public static let s400: CGFloat = 32
        public static let s500: CGFloat = 40
        public static let s600: CGFloat = 48
        public static let s800: CGFloat = 64
        public static let s1000: CGFloat = 80
        public static let s1200: CGFloat = 96
    }

    nonisolated public enum Radius {
        public static let xs: CGFloat = 2
        public static let sm: CGFloat = 4
        public static let md: CGFloat = 8
        public static let lg: CGFloat = 12
        public static let xl: CGFloat = 16
        public static let xxl: CGFloat = 24
        public static let full: CGFloat = 999
    }

    nonisolated public enum Size {
        public static let controlSm: CGFloat = 24
        public static let controlMd: CGFloat = 32
        public static let controlLg: CGFloat = 40
        public static let controlXl: CGFloat = 48
        public static let iconSm: CGFloat = 16
        public static let iconMd: CGFloat = 20
        public static let iconLg: CGFloat = 24
        public static let header: CGFloat = 48
        public static let sidebar: CGFloat = 256
        public static let inspector: CGFloat = 288
        public static let editorColumn: CGFloat = 680
        public static let dialogSm: CGFloat = 400
        public static let dialogMd: CGFloat = 560
        public static let dialogLg: CGFloat = 768
    }
}

// MARK: - Typography

/// The `v4/UI/*` text styles. Figma uses Inter; the app uses the system font with the same
/// sizes, weights, line heights and tracking, so it reads like the rest of the platform.
nonisolated public enum WrTextStyle: CaseIterable, Sendable {
    case displayLarge
    case displayMedium
    case headingXL
    case headingLarge
    case headingMedium
    case headingSmall
    case bodyLarge
    case bodyMedium
    case bodySmall
    case bodyXSmall
    case labelLarge
    case labelMedium
    case labelSmall
    /// Uppercase eyebrow above titles. Apply `.textCase(.uppercase)` too.
    case overline
    case code

    public var size: CGFloat {
        switch self {
        case .displayLarge: 40
        case .displayMedium: 32
        case .headingXL: 28
        case .headingLarge: 24
        case .headingMedium: 20
        case .headingSmall, .bodyLarge: 16
        case .bodyMedium, .labelLarge: 14
        case .bodySmall, .labelMedium, .code: 13
        case .bodyXSmall, .labelSmall: 12
        case .overline: 11
        }
    }

    public var lineHeight: CGFloat {
        switch self {
        case .displayLarge: 48
        case .displayMedium: 40
        case .headingXL: 36
        case .headingLarge: 32
        case .headingMedium: 28
        case .headingSmall, .bodyLarge: 24
        case .bodyMedium, .bodySmall, .labelLarge, .code: 20
        case .bodyXSmall, .labelMedium, .labelSmall, .overline: 16
        }
    }

    public var weight: Font.Weight {
        switch self {
        case .displayLarge, .displayMedium: .bold
        case .headingXL, .headingLarge, .headingMedium, .headingSmall, .overline: .semibold
        case .labelLarge, .labelMedium, .labelSmall: .medium
        case .bodyLarge, .bodyMedium, .bodySmall, .bodyXSmall, .code: .regular
        }
    }

    /// Letter spacing in points (Figma gives it as a percentage of the size).
    public var tracking: CGFloat {
        let percent: CGFloat = switch self {
        case .displayLarge: -2
        case .displayMedium: -1.5
        case .headingXL: -1
        case .headingLarge, .headingMedium: -0.5
        case .overline: 6
        default: 0
        }
        return size * percent / 100
    }

    public var font: Font {
        self == .code
            ? .system(size: size, weight: weight, design: .monospaced)
            : .system(size: size, weight: weight)
    }
}

public extension View {
    /// Font, tracking and line height of a v4 text style.
    func wrTextStyle(_ style: WrTextStyle) -> some View {
        font(style.font)
            .tracking(style.tracking)
            // The system font's own line height is about 1.2 × the size.
            .lineSpacing(max(0, style.lineHeight - style.size * 1.2))
    }
}

// MARK: - Elevation

nonisolated public enum WrElevation: Sendable {
    /// Cards on the page.
    case raised
    /// Menus and popovers.
    case overlay
    /// Dialogs and sheets.
    case dialog
    /// Floating bars and hover cards.
    case floating

    var key: (y: CGFloat, radius: CGFloat) {
        switch self {
        case .raised: (1, 2)
        case .overlay: (4, 12)
        case .dialog: (12, 32)
        case .floating: (24, 56)
        }
    }

    var ambient: (y: CGFloat, radius: CGFloat) {
        switch self {
        case .raised: (0, 1)
        case .overlay: (1, 3)
        case .dialog: (2, 8)
        case .floating: (4, 16)
        }
    }
}

public extension View {
    /// The two shadows of a v4 elevation level (Figma's blur is twice SwiftUI's radius).
    func wrElevation(_ level: WrElevation) -> some View {
        shadow(color: WrTheme.Colors.shadowAmbient, radius: level.ambient.radius / 2, y: level.ambient.y)
            .shadow(color: WrTheme.Colors.shadowKey, radius: level.key.radius / 2, y: level.key.y)
    }
}
