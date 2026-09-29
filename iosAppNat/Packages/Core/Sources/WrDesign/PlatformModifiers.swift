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
