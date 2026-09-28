import DocumentsFeature
import SearchFeature
import SettingsFeature
import SwiftUI

struct MainTabView: View {
    private enum Tab: Hashable {
        case documents
        case search
        case settings
    }

    @State private var selection: Tab = .documents

    var body: some View {
        TabView(selection: $selection) {
            DocumentsRootView()
                .tabItem { Label("Documents", systemImage: "doc.text") }
                .tag(Tab.documents)

            SearchRootView()
                .tabItem { Label("Search", systemImage: "magnifyingglass") }
                .tag(Tab.search)

            SettingsRootView()
                .tabItem { Label("Settings", systemImage: "gearshape") }
                .tag(Tab.settings)
        }
    }
}
