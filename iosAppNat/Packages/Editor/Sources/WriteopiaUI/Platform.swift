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

/// The scroll view around the editor, so the reorder drag can scroll while the pointer is
/// near an edge. `UIScrollView` on iOS, `NSScrollView` on macOS.
@MainActor
public protocol EditorScrolling: AnyObject {
    var scrollOffset: CGFloat { get set }
    var visibleHeight: CGFloat { get }
    var topInset: CGFloat { get }
    var bottomInset: CGFloat { get }
    var contentHeight: CGFloat { get }
}

#if canImport(UIKit)
extension UIScrollView: EditorScrolling {
    public var scrollOffset: CGFloat {
        get { contentOffset.y }
        set { contentOffset.y = newValue }
    }

    public var visibleHeight: CGFloat { bounds.height }
    public var topInset: CGFloat { adjustedContentInset.top }
    public var bottomInset: CGFloat { adjustedContentInset.bottom }
    public var contentHeight: CGFloat { contentSize.height }
}
#elseif canImport(AppKit)
extension NSScrollView: EditorScrolling {
    public var scrollOffset: CGFloat {
        get { contentView.bounds.origin.y }
        set {
            contentView.scroll(to: NSPoint(x: contentView.bounds.origin.x, y: newValue))
            reflectScrolledClipView(contentView)
        }
    }

    public var visibleHeight: CGFloat { contentView.bounds.height }
    public var topInset: CGFloat { contentInsets.top }
    public var bottomInset: CGFloat { contentInsets.bottom }
    public var contentHeight: CGFloat { documentView?.frame.height ?? 0 }
}
#endif
