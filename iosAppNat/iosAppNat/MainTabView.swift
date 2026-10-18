#if os(iOS)
import DocumentsFeature
import SearchFeature
import SettingsFeature
import SwiftUI
import WrDesign
import WrModels
import WrSession

struct MainTabView: View {
    var body: some View {
        MainLayout()
            .readsWideLayout()
    }
}

/// A tab of the app. The favorites are in the sidebar only.
private enum MainTab: Hashable {
    case search
    case folders
    case favorites
    case settings
}

/// The tabs as the system draws them: a tab bar on the iPhone; on the iPad a tab bar at the top
/// that opens into a sidebar, like the TV app. The sidebar has the destinations of the side menu
/// of the Compose app, then the folder tree of the workspace.
private struct MainLayout: View {
    @Environment(AppSession.self) private var session
    @Environment(\.isWideLayout) private var isWideLayout
    @State private var selection: MainTab = .folders
    @State private var router = DocumentsRouter()
    @State private var openEditors = OpenEditors()
    /// Set while the folder tree moves the selection to the Folders tab: the tree has routed
    /// the documents itself, so that change mustn't reset them.
    @State private var isOpeningFromTree = false

    var body: some View {
        TabView(selection: $selection) {
            // The search role puts the tab alone at the trailing end of the tab bar, as an icon.
            Tab("Search", systemImage: "magnifyingglass", value: .search, role: .search) {
                SearchRootView()
                    .toolbar(tabBarVisibility, for: .tabBar)
            }

            Tab("Folders", systemImage: "folder", value: .folders) {
                documents
            }

            Tab("Favorites", systemImage: "star", value: .favorites) {
                documents
            }
            .defaultVisibility(.hidden, for: .tabBar)

            Tab("Settings", systemImage: "gearshape", value: .settings) {
                SettingsRootView()
                    .toolbar(tabBarVisibility, for: .tabBar)
            }
        }
        .tabViewStyle(.sidebarAdaptable)
        .tabViewSidebarHeader {
            Text(session.workspace?.name ?? String(localized: "Documents"))
                .font(.headline)
                .lineLimit(1)
        }
        .tabViewSidebarFooter {
            // The tree expands in place, which the rows of a tab section can't do.
            SidebarFolders(router: router) {
                isOpeningFromTree = selection != .folders
                selection = .folders
            }
            .padding(.top, 12)
        }
        .environment(\.openEditors, openEditors)
        .onChange(of: selection) { _, tab in route(to: tab) }
    }

    /// The documents of the workspace. The folders and the favorites are the same navigation;
    /// the tab picked decides where it goes.
    private var documents: some View {
        DocumentsRootView(router: router)
            .toolbar(tabBarVisibility, for: .tabBar)
    }

    /// The editor takes the whole screen in landscape, like the Compose app, so the tab bar and
    /// the sidebar step aside while one is open.
    private var tabBarVisibility: Visibility {
        isWideLayout && openEditors.isAnyOpen ? .hidden : .automatic
    }

    private func route(to tab: MainTab) {
        switch tab {
        case .folders:
            // A tap on the tab shows the root; the tree picked a folder or a document instead.
            if isOpeningFromTree {
                isOpeningFromTree = false
            } else {
                router.reset()
            }
        case .favorites:
            router.openFavorites()
        case .search, .settings:
            break
        }
    }
}
#endif
