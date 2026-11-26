import SetupFeature
import SwiftUI
import WrDesign
import WrSession

/// Second setup step of the private space, on the v4 design: the folder in the computer where
/// the workspace is kept, like `LocalFolderSetupScreen` of the desktop app.
public struct LocalFolderSetupView: View {
    @Environment(AppSession.self) private var session

    public init() {}

    public var body: some View {
        WrOnboardingScreen(width: WrTheme.Size.dialogMd, back: ("Back", session.retreatOfflineSetup)) {
            VStack(alignment: .leading, spacing: WrTheme.Space.s300) {
                WrTitleBlock(
                    eyebrow: "Private space · Step 2 of 2",
                    title: "Choose a local folder",
                    subtitle: "Your documents are saved here as plain files. You can skip this and pick a folder later in Settings."
                )

                VStack(alignment: .leading, spacing: WrTheme.Space.s100) {
                    Text("Folder")
                        .wrTextStyle(.labelSmall)
                        .foregroundStyle(WrTheme.Colors.textSecondary)

                    PrivateSpaceFolderPicker()
                }

                WrPrimaryButton("Start writing", action: session.advanceOfflineSetup)
                    .accessibilityIdentifier("setup.folder.continue")
            }
        }
    }
}
