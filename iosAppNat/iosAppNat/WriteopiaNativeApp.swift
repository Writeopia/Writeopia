import DocumentsFeature
import SwiftUI
import WrDesign
import WrSession

@main
struct WriteopiaNativeApp: App {
    @State private var session = WriteopiaNativeApp.makeSession()

    #if os(macOS)
    init() {
        // The tabs come back at the next launch. The system setting "Close windows when quitting
        // an application" is on by default and would drop them; registered as a default, this
        // only applies until the person picks otherwise for the app.
        UserDefaults.standard.register(defaults: ["NSQuitAlwaysKeepsWindows": true])
    }
    #endif

    var body: some Scene {
        #if os(macOS)
        // Each window is a tab (`WindowTabbing`) with its own navigation; its value says what a
        // new tab opens on.
        WindowGroup(id: MainWindow.id, for: TabLaunch.self) { $launch in
            RootView()
                .environment(session)
                .environment(\.tabLaunch, launch)
                .colorTheme(session.colorTheme)
                .frame(minWidth: 900, minHeight: 600)
        }
        .defaultSize(width: 1100, height: 800)
        .commands {
            SidebarCommands()
            CommandGroup(after: .newItem) {
                NewTabCommand()
            }
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
