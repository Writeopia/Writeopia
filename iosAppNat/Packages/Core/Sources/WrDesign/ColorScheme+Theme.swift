import SwiftUI
import WrModels
#if os(macOS)
import AppKit
#endif

public extension ColorTheme {
    /// `nil` means "follow the system", which is what `preferredColorScheme` expects.
    var colorScheme: ColorScheme? {
        switch self {
        case .light: .light
        case .dark: .dark
        case .system: nil
        }
    }

    var systemImage: String {
        switch self {
        case .light: "sun.max"
        case .dark: "moon"
        case .system: "circle.lefthalf.filled"
        }
    }

    #if os(macOS)
    /// `nil` makes the app follow the appearance of the system.
    var appearance: NSAppearance? {
        switch self {
        case .light: NSAppearance(named: .aqua)
        case .dark: NSAppearance(named: .darkAqua)
        case .system: nil
        }
    }
    #endif
}

public extension View {
    /// Applies the color theme chosen in the settings. On the Mac it sets the appearance of the
    /// whole app, because `preferredColorScheme(nil)` leaves the window in the last light or
    /// dark choice instead of going back to the system's.
    func colorTheme(_ theme: ColorTheme) -> some View {
        #if os(macOS)
        onChange(of: theme, initial: true) { _, theme in
            NSApp.appearance = theme.appearance
        }
        #else
        preferredColorScheme(theme.colorScheme)
        #endif
    }
}
