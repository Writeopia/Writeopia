import DocumentsFeature
import SwiftUI
import WrAnalytics
import WrDesign
import WrSession

@main
struct WriteopiaNativeApp: App {
    @State private var session = WriteopiaNativeApp.makeSession()

    init() {
        PostHogAnalyticsTracker.shared.setup()
    }

    var body: some Scene {
        #if os(macOS)
        WindowGroup {
            RootView()
                .environment(session)
                .colorTheme(session.colorTheme)
                .frame(minWidth: 900, minHeight: 600)
        }
        .defaultSize(width: 1100, height: 800)
        .commands {
            SidebarCommands()
        }
        // Presentations play in a window of their own.
        PresentationWindowScene(session: session)
        #else
        WindowGroup {
            RootView()
                .environment(session)
                .colorTheme(session.colorTheme)
        }
        #endif
    }

    /// The Mac app walks the first-run setup of the desktop app after choosing the private
    /// space (local AI, then the folder of the workspace); iOS goes straight to the documents.
    private static func makeSession() -> AppSession {
        #if os(macOS)
        AppSession(offlineSetupSteps: [.localAi, .localFolder])
        #else
        AppSession()
        #endif
    }
}
