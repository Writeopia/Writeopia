#if os(macOS)
import AppKit
import SwiftUI
import Testing
import WrModels
@testable import WriteopiaUI

/// The `NSTextView` editor hosted in a real window: sizing, Return, Backspace and focus.
@MainActor
@Suite(.serialized) struct MacEditorTests {
    private func host() -> (WriteopiaStateManager, NSWindow, NSHostingView<WriteopiaEditor>) {
        let manager = WriteopiaStateManager()
        manager.loadDocument(WrDocument(id: "d", title: "Doc", workspaceId: "w", content: [
            StoryStep(id: "t", type: .title, text: "Doc", position: 0),
            StoryStep(id: "a", type: .text, text: "Hello world", position: 1),
            StoryStep(id: "b", type: .text, text: "Second", position: 2),
        ]))
        let hosting = NSHostingView(rootView: WriteopiaEditor(manager: manager))
        hosting.frame = NSRect(x: 0, y: 0, width: 600, height: 400)
        let window = NSWindow(contentRect: hosting.frame, styleMask: [.titled], backing: .buffered, defer: false)
        window.contentView = hosting
        window.orderFront(nil)
        hosting.layoutSubtreeIfNeeded()
        return (manager, window, hosting)
    }

    private func textViews(in view: NSView) -> [StepNSTextView] {
        view.subviews.flatMap { subview -> [StepNSTextView] in
            if let textView = subview as? StepNSTextView { return [textView] }
            return textViews(in: subview)
        }
    }

    private func settle() async {
        for _ in 0..<5 {
            await Task.yield()
            try? await Task.sleep(for: .milliseconds(10))
        }
    }

    @Test func stepsAreSizedToTheirText() async {
        let (_, window, hosting) = host()
        await settle()
        hosting.layoutSubtreeIfNeeded()

        let views = textViews(in: hosting)
        #expect(views.count == 3)
        for view in views {
            #expect(view.frame.height >= 15, "each step shows at least one line")
            #expect(view.frame.width > 300, "steps take the width of the editor")
        }
        let title = views.first { $0.string == "Doc" }
        let body = views.first { $0.string == "Hello world" }
        #expect((title?.frame.height ?? 0) > (body?.frame.height ?? 0), "the title uses a larger font")
        _ = window
    }

    @Test func returnSplitsAndBackspaceMergesTheStep() async {
        let (manager, window, hosting) = host()
        await settle()

        guard let textView = textViews(in: hosting).first(where: { $0.string == "Hello world" }) else {
            Issue.record("text view of the step not found")
            return
        }
        window.makeFirstResponder(textView)
        #expect(manager.currentStory.focus == 1)

        textView.setSelectedRange(NSRange(location: 5, length: 0))
        textView.insertNewline(nil)
        await settle()

        let texts = manager.currentStory.sortedStories.map { $0.text ?? "" }
        #expect(texts == ["Doc", "Hello", " world", "Second"])

        // The new line took the keyboard, with the cursor at its start, and the request is spent.
        hosting.layoutSubtreeIfNeeded()
        await settle()
        guard let secondHalf = textViews(in: hosting).first(where: { $0.string == " world" }) else {
            Issue.record("text view of the new step not found")
            return
        }
        #expect(window.firstResponder === secondHalf)
        #expect(secondHalf.selectedRange().location == 0)
        #expect(manager.focusRequest == nil)

        // Backspace at the start merges back, through the key binding path a keystroke takes.
        secondHalf.doCommand(by: #selector(NSResponder.deleteBackward(_:)))
        await settle()

        #expect(manager.currentStory.sortedStories.map { $0.text ?? "" } == ["Doc", "Hello world", "Second"])
    }

    @Test func typingUpdatesTheModelAndArrowsMoveBetweenSteps() async {
        let (manager, window, hosting) = host()
        await settle()

        guard let textView = textViews(in: hosting).first(where: { $0.string == "Second" }) else {
            Issue.record("text view of the step not found")
            return
        }
        window.makeFirstResponder(textView)
        textView.setSelectedRange(NSRange(location: 6, length: 0))
        textView.insertText("!", replacementRange: NSRange(location: 6, length: 0))
        await settle()
        #expect(manager.step(withId: "b")?.text == "Second!")

        textView.doCommand(by: #selector(NSResponder.moveUp(_:)))
        await settle()
        guard let above = textViews(in: hosting).first(where: { $0.string == "Hello world" }) else {
            Issue.record("text view of the step above not found")
            return
        }
        #expect(window.firstResponder === above)
        #expect(above.selectedRange().location == 7)

        // Down at the last line goes back; Backspace in the middle of the text stays local.
        above.doCommand(by: #selector(NSResponder.moveDown(_:)))
        await settle()
        #expect(window.firstResponder === textView)
        textView.doCommand(by: #selector(NSResponder.deleteBackward(_:)))
        await settle()
        #expect(manager.step(withId: "b")?.text == "Second")
        #expect(manager.currentStory.sortedStories.count == 3)
    }
}
#endif
