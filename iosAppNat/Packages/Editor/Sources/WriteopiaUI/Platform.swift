import SwiftUI
#if canImport(UIKit)
import UIKit

public typealias PlatformFont = UIFont
public typealias PlatformColor = UIColor
typealias PlatformFontDescriptor = UIFontDescriptor
#elseif canImport(AppKit)
import AppKit

public typealias PlatformFont = NSFont
public typealias PlatformColor = NSColor
typealias PlatformFontDescriptor = NSFontDescriptor
#endif

/// The system pasteboard, for the few places the editor writes plain text to it.
enum SystemPasteboard {
    static func copy(_ string: String) {
        #if canImport(UIKit)
        UIPasteboard.general.string = string
        #else
        NSPasteboard.general.clearContents()
        NSPasteboard.general.setString(string, forType: .string)
        #endif
    }
}
