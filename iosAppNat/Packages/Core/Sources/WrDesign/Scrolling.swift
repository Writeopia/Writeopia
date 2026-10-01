import SwiftUI
#if canImport(UIKit)
import UIKit
#elseif canImport(AppKit)
import AppKit
#endif

/// A scroll view a drag can move while the pointer is near an edge (the reorder drag and the
/// selection box). `UIScrollView` on iOS, `NSScrollView` on macOS.
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
