#if os(macOS)
import AppKit
import ClaudeCodeFeature
import DocumentsFeature
import SearchFeature
import SettingsFeature
import SwiftUI
import WrDesign
import WriteopiaUI
import WrModels
import WrSession

/// The Mac window: the side menu of the landscape layout as the sidebar, and the documents,
/// search or settings in the detail column, like the desktop app in Compose. Each window is a
/// tab (`WindowTabbing`) with a navigation of its own.
struct MacMainView: View {
    @State private var selection: MainDestination = .documents
    @State private var router = DocumentsRouter()
    @State private var openEditors = OpenEditors()
    /// Grows with every Cmd+F, so the search field takes the cursor (`searchFocusRequest`).
    @State private var searchFocusRequests = 0
    /// The window of this tab, once the view is in it.
    @State private var window: NSWindow?
    @State private var placeApplied = false
    @Environment(AppSession.self) private var session
    @Environment(\.openWindow) private var openWindow
    @Environment(\.appearsActive) private var appearsActive
    @Environment(\.tabLaunch) private var launch
    /// Where this tab was, kept by the system with the window to restore it at the next launch.
    @SceneStorage("tab.place") private var savedPlace = ""

    var body: some View {
        NavigationSplitView {
            SideGlobalMenu(selection: $selection, router: router, fixedWidth: false)
                .navigationSplitViewColumnWidth(min: 220, ideal: 234, max: 320)
        } detail: {
            switch selection {
            case .documents:
                DocumentsRootView(router: router)
            case .search:
                SearchRootView()
            case .settings:
                SettingsRootView { document in
                    router.reset()
                    selection = .documents
                    router.open(document: document)
                }
            }
        }
        .environment(\.openEditors, openEditors)
        .environment(\.isWideLayout, true)
        .environment(\.searchFocusRequest, searchFocusRequests)
        .environment(\.openInNewTab) { place in openTab(place) }
        .background { searchShortcut }
        .background {
            WindowAccessor { window in
                WindowTabbing.configure(window)
                self.window = window
                registerTab()
            }
        }
        // Claude Code at work: the banner with the button to stop it, over everything.
        .overlay(alignment: .top) { ClaudeCodeAiModeBanner() }
        .task { await applyPlace() }
        // The tab the person moved to (or the window they came back to) is where Claude Code
        // looks: its folder, its editor, its navigation.
        .onChange(of: appearsActive, initial: true) { _, active in
            if active { takeFront() }
        }
        .onChange(of: router.place) { _, place in
            savePlace(place)
            registerTab()
        }
        // Only the documents screen shows the router's document: search and settings show none.
        .onChange(of: selection) { _, _ in registerTab() }
        .onDisappear {
            if let window { OpenTabs.shared.remove(window) }
        }
    }

    // MARK: - Tabs

    /// The place of a restored tab, or the one a new tab was opened on; once. A tab that saved
    /// anything is a restored one: its saved place wins (the root included), and the launch place
    /// is never replayed. A place saved in another workspace means nothing here: the root.
    private func applyPlace() async {
        guard !placeApplied else { return }
        placeApplied = true

        if let saved = StoredPlace(json: savedPlace) {
            if saved.workspaceId == session.workspace?.id, let place = saved.place {
                await router.open(place: place, repository: session.documents)
            }
        } else if let place = launch?.place {
            await router.open(place: place, repository: session.documents)
        }
        // Saved at once, even at the root: from now on this tab restores from what it saved.
        savePlace(router.place)
        if launch?.byClaude == true { takeFront() }
    }

    /// Keeps where the tab is, with its workspace; nil is the root. Without a workspace (signed
    /// out) the last place stays: it is checked against the workspace when restored.
    private func savePlace(_ place: TabPlace?) {
        guard let workspaceId = session.workspace?.id else { return }
        savedPlace = StoredPlace(workspaceId: workspaceId, place: place).json ?? ""
    }

    /// The document this tab shows, for Claude Code to come to it instead of opening it again:
    /// only while the documents screen is selected, since search and settings hide it.
    private func registerTab() {
        guard let window else { return }
        let documentId = selection == .documents ? router.shownDocumentId : nil
        OpenTabs.shared.update(window: window, documentId: documentId) { takeFront() }
    }

    /// A new tab of this window's group, on `place`.
    private func openTab(_ place: TabPlace, byClaude: Bool = false) {
        WindowTabbing.prepareNewTab(from: window ?? NSApp.keyWindow)
        openWindow(id: MainWindow.id, value: TabLaunch(place: place, byClaude: byClaude))
    }

    /// This tab is the one in front: Claude Code works with it.
    private func takeFront() {
        connectClaudeCode()
        LiveEditors.shared.bringToFront(owner: openEditors)
    }

    /// Claude Code writes through the session, and shows the documents it writes into, so the
    /// person watches the text being typed. It works in a tab of its own, unless it writes into
    /// the document this tab shows; a document open in another tab is shown there.
    private func connectClaudeCode() {
        ClaudeCodeServer.shared.configure(session: session, appVersion: Self.appVersion)
        ClaudeCode.openDocument = { id, title in
            router.treeChanged()
            if selection == .documents, router.shownDocumentId == id { return }
            if OpenTabs.shared.bringToFront(showing: id, except: window) { return }
            openTab(.document(id: id, title: title), byClaude: true)
        }
        ClaudeCode.openFolder = { folder in
            router.treeChanged()
            // The default parent at once, before the new tab is up and takes over.
            ClaudeCode.currentFolderId = { folder.id }
            openTab(.folder(id: folder.id), byClaude: true)
        }
        ClaudeCode.currentFolderId = { router.shownFolderId }
    }

    private static var appVersion: String {
        Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "mac"
    }

    /// Cmd+F opens the search from the documents menu only, like the Compose desktop app: in
    /// the editor the shortcut finds inside the document, and the other screens have no search.
    @ViewBuilder
    private var searchShortcut: some View {
        if selection == .documents, !openEditors.isAnyOpen {
            Button("Search") {
                selection = .search
                searchFocusRequests += 1
            }
            .keyboardShortcut("f", modifiers: .command)
            .opacity(0)
            .allowsHitTesting(false)
            .accessibilityHidden(true)
        }
    }
}

/// The place of a tab as the scene keeps it: with its workspace, since the ids mean nothing in
/// another one. `place` is nil at the root.
struct StoredPlace: Codable, Equatable {
    let workspaceId: String
    let place: TabPlace?

    init(workspaceId: String, place: TabPlace?) {
        self.workspaceId = workspaceId
        self.place = place
    }

    init?(json: String) {
        guard let data = json.data(using: .utf8),
              let stored = try? JSONDecoder().decode(StoredPlace.self, from: data) else { return nil }
        self = stored
    }

    var json: String? {
        (try? JSONEncoder().encode(self)).flatMap { String(data: $0, encoding: .utf8) }
    }
}
#endif
