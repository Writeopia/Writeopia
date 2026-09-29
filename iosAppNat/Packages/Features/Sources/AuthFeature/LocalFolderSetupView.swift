import SetupFeature
import SwiftUI
import WrDesign
import WrSession

/// Second setup screen of the private space, like `LocalFolderSetupScreen` of the desktop app:
/// the folder in the computer where the workspace is kept.
public struct LocalFolderSetupView: View {
    @Environment(AppSession.self) private var session

    public init() {}

    public var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 24) {
                WrScreenHeader(
                    eyebrow: "Private space",
                    title: "Choose a local folder",
                    subtitle: "Pick a folder in your computer to keep your workspace, or skip and do it later from Settings."
                )

                VStack(alignment: .leading, spacing: 12) {
                    Text("Local Folder")
                        .font(.headline)
                        .foregroundStyle(WrColors.textLight)

                    PrivateSpaceFolderPicker()
                }

                WrPrimaryButton("Continue", action: session.advanceOfflineSetup)
                    .accessibilityIdentifier("setup.folder.continue")
            }
            .padding(24)
            .frame(maxWidth: 700)
            .frame(maxWidth: .infinity)
        }
        .background(WrColors.background)
    }
}
