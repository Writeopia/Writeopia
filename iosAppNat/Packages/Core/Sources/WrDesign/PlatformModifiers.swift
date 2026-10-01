import SwiftUI
#if canImport(UIKit)
import UIKit
#elseif canImport(AppKit)
import AppKit
#endif

public extension ToolbarItemPlacement {
    /// The trailing side of the navigation bar on iOS; the default toolbar slot on macOS.
    static var wrTrailing: ToolbarItemPlacement {
        #if os(iOS)
        .topBarTrailing
        #else
        .automatic
        #endif
    }
}

public extension View {
    /// Sheets on the Mac take the size of their content, and a list or a form has none: this
    /// gives them a sensible window. Nothing changes on iOS, where sheets fill the screen.
    @ViewBuilder
    func wrSheetSize(width: CGFloat = 440, height: CGFloat = 480) -> some View {
        #if os(macOS)
        frame(minWidth: width, idealWidth: width, minHeight: height, idealHeight: height)
        #else
        self
        #endif
    }
}

/// Deep links into the system settings.
public enum SystemSettings {
    /// Where Apple Intelligence is turned on: the app's settings on iOS, the Apple Intelligence
    /// pane of System Settings on macOS.
    public static var appleIntelligenceURL: URL? {
        #if os(iOS)
        URL(string: UIApplication.openSettingsURLString)
        #else
        URL(string: "x-apple.systempreferences:com.apple.Siri-Settings.extension")
        #endif
    }
}
