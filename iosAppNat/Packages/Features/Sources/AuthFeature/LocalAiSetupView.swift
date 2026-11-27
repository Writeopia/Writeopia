import SetupFeature
import SwiftUI
import WrDesign
import WrSession

/// First setup step of the private space, on the v4 design: which AI answers in the editor,
/// Apple Intelligence or a local model, like `LocalAiSetupScreen` of the desktop app.
public struct LocalAiSetupView: View {
    @Environment(AppSession.self) private var session

    public init() {}

    public var body: some View {
        WrOnboardingScreen(width: WrTheme.Size.dialogMd, back: ("Choose space", session.retreatOfflineSetup)) {
            VStack(alignment: .leading, spacing: WrTheme.Space.s300) {
                WrTitleBlock(
                    eyebrow: "Private space · Step 1 of 2",
                    title: "Set up AI",
                    subtitle: "Choose what runs AI on this computer. Your text never leaves the device, and you can change this later in Settings."
                )

                AiSetupContent()

                VStack(spacing: WrTheme.Space.s100) {
                    WrPrimaryButton("Continue", action: session.advanceOfflineSetup)
                        .disabled(session.localAiConfig.wizard == .detecting)
                        .accessibilityIdentifier("setup.ai.continue")

                    // Goes on without configuring anything now: the AI choice stays as it is
                    // and can be changed in Settings.
                    WrButton("Skip, set up later", kind: .ghost, action: skip)
                        .accessibilityIdentifier("setup.ai.skip")
                }
            }
        }
    }

    private func skip() {
        session.localAiConfig.closeWizard()
        session.advanceOfflineSetup()
    }
}
