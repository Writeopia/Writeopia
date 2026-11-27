#if os(macOS)
import DocumentsFeature
import SearchFeature
import SettingsFeature
import SwiftUI
import WrDesign
import WrSession

/// The Mac window: the v4 sidebar, and the documents, search or settings in the detail column,
/// like the desktop app in Compose.
struct MacMainView: View {
    @State private var selection: MainDestination = .documents
    @State private var columnVisibility: NavigationSplitViewVisibility = .all
    @State private var router = DocumentsRouter()
    @State private var openEditors = OpenEditors()
    /// Grows with every Cmd+F, so the search field takes the cursor (`searchFocusRequest`).
    @State private var searchFocusRequests = 0

    var body: some View {
        NavigationSplitView(columnVisibility: $columnVisibility) {
            MacSidebar(selection: $selection, columnVisibility: $columnVisibility, router: router)
                .navigationSplitViewColumnWidth(min: 220, ideal: WrTheme.Size.sidebar, max: 320)
                // The sidebar has its own button to hide it, in its title bar.
                .toolbar(removing: .sidebarToggle)
                .ignoresSafeArea(edges: .top)
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
        .environment(\.showSidebar, columnVisibility == .detailOnly ? showSidebar : nil)
        .environment(\.openEditors, openEditors)
        .environment(\.isWideLayout, true)
        .environment(\.searchFocusRequest, searchFocusRequests)
        .background { searchShortcut }
    }

    private var showSidebar: () -> Void {
        { withAnimation(.snappy) { columnVisibility = .all } }
    }

    /// Cmd+F and Cmd+K (shown next to Search in the sidebar) open the search from the documents menu only, like the Compose desktop app: in
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
            Button("Search") {
                selection = .search
                searchFocusRequests += 1
            }
            .keyboardShortcut("k", modifiers: .command)
            .opacity(0)
            .allowsHitTesting(false)
            .accessibilityHidden(true)
        }
    }
}
#endif
