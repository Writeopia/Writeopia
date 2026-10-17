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

/// A tab of the app. The favorites, the graph and the items of the tree are in the sidebar only.
private enum MainTab: Hashable {
    case home
    case favorites
    case graph
    case settings
    case search
    /// A folder or a document at the root of the workspace, by id.
    case item(String)
}

/// The tabs as the system draws them: a tab bar on the iPhone; on the iPad a tab bar at the top
/// that opens into a sidebar, like the TV app. The sidebar has the destinations of the side menu
/// of the Compose app, then the folders of the workspace.
private struct MainLayout: View {
    @Environment(AppSession.self) private var session
    @Environment(\.isWideLayout) private var isWideLayout
    @State private var selection: MainTab = .home
    @State private var router = DocumentsRouter()
    @State private var tree = SidebarTree()
    @State private var openEditors = OpenEditors()
    @State private var newFolderTitle = ""
    @State private var asksNewFolderTitle = false

    var body: some View {
        TabView(selection: $selection) {
            Tab("Home", systemImage: "house", value: .home) {
                documents
            }

            Tab("Favorites", systemImage: "star", value: .favorites) {
                documents
            }
            .defaultVisibility(.hidden, for: .tabBar)

            Tab("Documents graph", systemImage: "point.3.connected.trianglepath.dotted", value: .graph) {
                documents
            }
            .defaultVisibility(.hidden, for: .tabBar)

            Tab("Settings", systemImage: "gearshape", value: .settings) {
                SettingsRootView()
                    .toolbar(tabBarVisibility, for: .tabBar)
            }

            Tab("Search", systemImage: "magnifyingglass", value: .search, role: .search) {
                SearchRootView()
                    .toolbar(tabBarVisibility, for: .tabBar)
            }

            TabSection("Folders") {
                ForEach(tree.items, id: \.id) { item in
                    Tab(value: MainTab.item(item.id)) {
                        documents
                    } label: {
                        Label {
                            Text(item.title)
                        } icon: {
                            SidebarItemIcon(item)
                        }
                    }
                }
            }
            .sectionActions {
                Button("New folder", systemImage: "plus") {
                    newFolderTitle = ""
                    asksNewFolderTitle = true
                }
                .accessibilityIdentifier("sideMenu.newFolder")
            }
            .defaultVisibility(.hidden, for: .tabBar)
        }
        .tabViewStyle(.sidebarAdaptable)
        .tabViewSidebarHeader {
            Text(session.workspace?.name ?? String(localized: "Documents"))
                .font(.headline)
                .lineLimit(1)
        }
        .environment(\.openEditors, openEditors)
        // A different workspace means a different tree; the router counts the changes to it.
        .task(id: "\(session.workspace?.id ?? "")-\(router.treeVersion)") {
            await tree.load(session: session)
        }
        .onChange(of: selection) { _, tab in route(to: tab) }
        .alert("New folder", isPresented: $asksNewFolderTitle) {
            TextField("Title", text: $newFolderTitle)
            Button("Cancel", role: .cancel) {}
            Button("Create") {
                Task { await tree.createFolder(title: newFolderTitle, session: session, router: router) }
            }
        }
    }

    /// The documents of the workspace. The home, the favorites, the graph and the items of the
    /// tree are all the same navigation; the tab picked decides where it goes.
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
        case .home:
            router.reset()
        case .favorites:
            router.openFavorites()
        case .graph:
            router.openGraph()
        case .settings, .search:
            break
        case .item(let id):
            guard let item = tree.items.first(where: { $0.id == id }) else { return }
            Task { await router.open(item, session: session) }
        }
    }
}
#endif
