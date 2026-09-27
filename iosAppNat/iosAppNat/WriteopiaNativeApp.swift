import SwiftUI
import WrDesign
import WrSession

@main
struct WriteopiaNativeApp: App {
    @State private var session = AppSession()

    var body: some Scene {
        WindowGroup {
            RootView()
                .environment(session)
                .preferredColorScheme(session.colorTheme.colorScheme)
        }
    }
}
