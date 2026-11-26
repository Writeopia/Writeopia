#if os(macOS)
import ClaudeCodeFeature
import DocumentsFeature
import SearchFeature
import SettingsFeature
import SwiftUI
import WrDesign
import WrSession

/// The Mac window: the side menu of the landscape layout as the sidebar, and the documents,
/// search or settings in the detail column, like the desktop app in Compose.
struct MacMainView: View {
    @State private var selection: MainDestination = .documents
    @State private var router = DocumentsRouter()
    @State private var openEditors = OpenEditors()
    /// Grows with every Cmd+F, so the search field takes the cursor (`searchFocusRequest`).
    @State private var searchFocusRequests = 0
    @Environment(AppSession.self) private var session

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
        .background { searchShortcut }
        // Claude Code at work: the banner with the button to stop it, over everything.
        .overlay(alignment: .top) { ClaudeCodeAiModeBanner() }
        .task { connectClaudeCode() }
    }

    /// Claude Code writes through the session, and shows the documents it writes into here, so
    /// the person watches the text being typed.
    private func connectClaudeCode() {
        ClaudeCodeServer.shared.configure(session: session, appVersion: Self.appVersion)
        ClaudeCode.openDocument = { id, title in
            selection = .documents
            router.treeChanged()
            router.path.append(DocumentsRoute.document(id: id, title: title))
        }
        ClaudeCode.openFolder = { folder in
            selection = .documents
            router.treeChanged()
            // The default parent at once, before the navigation below lands.
            router.willShow(folder)
            Task { await router.open(folder: folder, repository: session.documents) }
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
#endif
