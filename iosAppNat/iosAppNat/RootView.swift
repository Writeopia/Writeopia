import AuthFeature
import SwiftUI
import WrDesign
import WrSession

/// Routes between the space choice, the auth flow and the main app based on the session phase.
struct RootView: View {
    @Environment(AppSession.self) private var session

    var body: some View {
        Group {
            switch session.phase {
            case .spaceChoice:
                SpaceChoiceView()
            case .signedOut:
                AuthFlowView()
            case .emailConfirmation(let email):
                EmailConfirmationView(email: email, session: session)
            case .chooseWorkspace:
                ChooseWorkspaceView(session: session)
            case .offlineSetup(.localAi):
                LocalAiSetupView()
            case .offlineSetup(.localFolder):
                LocalFolderSetupView()
            case .ready:
                #if os(macOS)
                MacMainView()
                #else
                MainTabView()
                #endif
            }
        }
        .tint(WrColors.accent)
        .animation(.easeInOut(duration: 0.25), value: session.phase)
    }
}
