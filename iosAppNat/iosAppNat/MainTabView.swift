import DocumentsFeature
import SearchFeature
import SettingsFeature
import SwiftUI
import WrDesign

struct MainTabView: View {
    var body: some View {
        MainLayout()
            .readsWideLayout()
    }
}

/// The tab bar in portrait. In landscape the tab bar gives way to a side menu, like the Compose
/// app. The tab view stays in place in both, so each tab keeps where it was on rotation.
private struct MainLayout: View {
    @Environment(\.isWideLayout) private var isWideLayout
    @State private var selection: MainDestination = .documents
    @State private var documentsRouter = DocumentsRouter()
    @State private var openEditors = OpenEditors()

    /// The editor takes the whole screen in landscape, so the side menu shows only without one.
    private var showsSideMenu: Bool { isWideLayout && !openEditors.isAnyOpen }

    var body: some View {
        HStack(spacing: 0) {
            if showsSideMenu {
                SideGlobalMenu(selection: $selection, router: documentsRouter)
                    .transition(.move(edge: .leading))
                Divider()
                    .ignoresSafeArea()
            }

            TabView(selection: $selection) {
                DocumentsRootView(router: documentsRouter)
                    .toolbar(tabBarVisibility, for: .tabBar)
                    .tabItem { Label("Documents", systemImage: "doc.text") }
                    .tag(MainDestination.documents)

                SearchRootView()
                    .toolbar(tabBarVisibility, for: .tabBar)
                    .tabItem { Label("Search", systemImage: "magnifyingglass") }
                    .tag(MainDestination.search)

                SettingsRootView()
                    .toolbar(tabBarVisibility, for: .tabBar)
                    .tabItem { Label("Settings", systemImage: "gearshape") }
                    .tag(MainDestination.settings)
            }
        }
        .environment(\.openEditors, openEditors)
        .animation(.snappy, value: showsSideMenu)
    }

    private var tabBarVisibility: Visibility {
        isWideLayout ? .hidden : .automatic
    }
}
