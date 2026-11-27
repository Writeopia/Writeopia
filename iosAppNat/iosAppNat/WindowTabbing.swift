#if os(macOS)
import AppKit
import DocumentsFeature
import SwiftUI

/// What a window of the documents opens on: a new tab, from Cmd+T (no place), "Open in New
/// Tab" or Claude Code. Each one has its own id, so SwiftUI opens a new window every time instead
/// of bringing back a window opened on the same place earlier (which may have moved on since).
struct TabLaunch: Codable, Hashable {
    var id = UUID()
    var place: TabPlace?
    /// Opened by Claude Code, maybe while the app is in the background: the tab takes Claude's
    /// side at once instead of waiting to be the active window.
    var byClaude = false
}

extension EnvironmentValues {
    /// The launch of the window's tab; nil for the first window of the app.
    @Entry var tabLaunch: TabLaunch?
}

/// Makes the windows of the documents tabs of one another, like the tabs of a browser or of
/// Notion: every window of the main group joins the tab group of the window it was opened from.
@MainActor
enum WindowTabbing {
    static let identifier = "io.writeopia.main"

    /// The window a tab is being opened from: the new window joins its tab group.
    private static var pendingParent: NSWindow?

    /// Call right before `openWindow`: the window opened next becomes a tab of `parent`.
    static func prepareNewTab(from parent: NSWindow?) {
        pendingParent = parent
    }

    /// A window of the documents is ready: it prefers tabs, and joins the group it was opened from.
    static func configure(_ window: NSWindow) {
        window.tabbingIdentifier = identifier
        window.tabbingMode = .preferred

        guard let parent = pendingParent else {
            showTabBar(of: window)
            return
        }
        pendingParent = nil
        guard parent !== window, parent.tabbingIdentifier == identifier else { return }
        if !(parent.tabbedWindows?.contains(window) ?? false) {
            parent.addTabbedWindow(window, ordered: .above)
        }
        window.makeKeyAndOrderFront(nil)
    }

    /// AppKit shows the tab bar (and its +) only from the second tab; the documents always show
    /// it, like a browser. On the next turn, once the window is on screen with its tab group.
    private static func showTabBar(of window: NSWindow) {
        DispatchQueue.main.async {
            guard let group = window.tabGroup, !group.isTabBarVisible else { return }
            window.toggleTabBar(nil)
        }
    }
}

/// Gives the window a view is in, as soon as the view is put in it (before it shows), so the
/// tabbing applies to the first frame.
struct WindowAccessor: NSViewRepresentable {
    let onWindow: (NSWindow) -> Void

    func makeNSView(context: Context) -> NSView {
        WindowReportingView(onWindow: onWindow)
    }

    func updateNSView(_ nsView: NSView, context: Context) {}

    private final class WindowReportingView: NSView {
        let onWindow: (NSWindow) -> Void

        init(onWindow: @escaping (NSWindow) -> Void) {
            self.onWindow = onWindow
            super.init(frame: .zero)
        }

        @available(*, unavailable)
        required init?(coder: NSCoder) { fatalError() }

        override func viewDidMoveToWindow() {
            super.viewDidMoveToWindow()
            if let window { onWindow(window) }
        }
    }
}

/// The tabs open now and the document each one shows, so Claude Code goes to the tab that has
/// a document instead of opening it a second time.
@MainActor
final class OpenTabs {
    static let shared = OpenTabs()

    private struct Entry {
        weak var window: NSWindow?
        var documentId: String?
        /// Gives Claude Code this tab's side (its folder, its editor), as when it becomes active.
        var takeFront: () -> Void
    }

    private var entries: [ObjectIdentifier: Entry] = [:]

    func update(window: NSWindow, documentId: String?, takeFront: @escaping () -> Void) {
        entries = entries.filter { $0.value.window != nil }
        entries[ObjectIdentifier(window)] = Entry(window: window, documentId: documentId, takeFront: takeFront)
    }

    func remove(_ window: NSWindow) {
        entries[ObjectIdentifier(window)] = nil
    }

    /// Shows the tab that has `documentId` on screen, if one does (other than `except`).
    /// Returns whether there was one.
    func bringToFront(showing documentId: String, except: NSWindow?) -> Bool {
        guard let entry = entries.values.first(where: {
            $0.documentId == documentId && $0.window != nil && $0.window !== except
        }), let window = entry.window else { return false }

        window.makeKeyAndOrderFront(nil)
        entry.takeFront()
        return true
    }
}

/// File > New Tab (Cmd+T): a new tab of the window in front, on the root of the documents.
struct NewTabCommand: View {
    @Environment(\.openWindow) private var openWindow

    var body: some View {
        Button("New Tab") {
            WindowTabbing.prepareNewTab(from: NSApp.keyWindow)
            openWindow(id: MainWindow.id, value: TabLaunch())
        }
        .keyboardShortcut("t", modifiers: .command)
    }
}

enum MainWindow {
    static let id = "main"
}
#endif
