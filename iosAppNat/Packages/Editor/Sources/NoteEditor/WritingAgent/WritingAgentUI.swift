import Foundation

/// What only the screen can do for a suggestion: open the image picker, or the drawing editor.
/// The screen hands this to the controller.
public protocol WritingAgentUI: AnyObject {
    /// Opens the image picker; the picked image is added where the cursor is.
    func pickImage()
    /// Opens the drawing editor. The drawing is added to the document when it's saved.
    func openDrawing()
}

/// A UI that can't pick images or draw, for tests.
public final class NoWritingAgentUI: WritingAgentUI {
    public init() {}
    public func pickImage() {}
    public func openDrawing() {}
}
