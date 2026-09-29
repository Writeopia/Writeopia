#if os(macOS)
import AppKit
import SwiftUI
import Testing
import WrData
import WrDesign
import WrModels
@testable import NoteEditor

/// The editor inside the containers of the Mac app (split view, navigation stack): every step
/// wraps at the width it's drawn with, so steps never overlap.
@MainActor
@Suite(.serialized) struct MacEditorLayoutTests {
    private func settle(_ times: Int = 30) async {
        for _ in 0..<times {
            await Task.yield()
            try? await Task.sleep(for: .milliseconds(20))
        }
    }

    @Test func stepsWrapAtTheirDrawnWidthAndStack() async throws {
        let directory = URL.temporaryDirectory.appending(path: "wr layout \(UUID().uuidString)")
        let repository = LocalDocumentsRepository(directory: directory, seedsWelcome: false)
        var document = try await repository.createDocument(title: "A note about many things", parentId: Folder.rootId)
        let paragraph = "This is a long paragraph of text that should wrap across the whole width of the editor, several times over, so the height of the step depends on the width it is drawn with."
        document.content = [
            StoryStep(type: .title, text: "A note about many things", position: 0),
            StoryStep(type: .message, text: paragraph, position: 1),
            StoryStep(type: .checkItem, text: "A check item", position: 2),
            StoryStep(type: .unorderedListItem, text: "A list item", position: 3),
        ]
        try await repository.save(document)

        let root = NavigationSplitView {
            Text("Sidebar").frame(width: 200)
        } detail: {
            NavigationStack {
                NoteEditorView(documentId: document.id, title: document.title, repository: repository)
            }
        }
        .environment(\.isWideLayout, true)
        .frame(width: 1200, height: 600)
        let hosting = NSHostingView(rootView: root)
        hosting.frame = NSRect(x: 0, y: 0, width: 1200, height: 600)
        let window = NSWindow(contentRect: hosting.frame, styleMask: [.titled], backing: .buffered, defer: false)
        window.contentView = hosting
        window.orderFront(nil)
        await settle()
        hosting.layoutSubtreeIfNeeded()

        let textViews = hosting.subviewsRecursive.compactMap { $0 as? NSTextView }
        func frame(of prefix: String) -> CGRect? {
            textViews.first { $0.string.hasPrefix(prefix) }.map { $0.convert($0.bounds, to: nil) }
        }
        guard let title = frame(of: "A note"), let body = frame(of: "This is"), let check = frame(of: "A check"), let list = frame(of: "A list") else {
            Issue.record("steps not found: \(textViews.map(\.string))")
            return
        }

        // The text wraps at the frame width: several lines of body text, measured against the
        // one-line check item.
        let lineHeight = check.height
        #expect(body.width > 500)
        #expect(body.height >= lineHeight * 2 - 2)
        // Steps stack without overlapping (window coordinates grow upwards).
        #expect(title.minY >= body.maxY - 1)
        #expect(body.minY >= check.maxY - 1)
        #expect(check.minY >= list.maxY - 1)

        // The measuring container never leaks into the display: the text on screen wraps at
        // the frame, whatever width SwiftUI probed last.
        for textView in textViews {
            #expect(textView.textContainer?.containerSize.width == textView.frame.width)
        }
        _ = window
    }
}

private extension NSView {
    var subviewsRecursive: [NSView] { subviews + subviews.flatMap(\.subviewsRecursive) }
}
#endif
