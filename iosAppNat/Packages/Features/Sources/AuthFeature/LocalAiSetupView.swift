import SetupFeature
import SwiftUI
import WrDesign
import WrSession

/// First setup screen of the private space, like `LocalAiSetupScreen` of the desktop app:
/// which AI answers in the editor, Apple Intelligence or a local model.
public struct LocalAiSetupView: View {
    @Environment(AppSession.self) private var session

    public init() {}

    public var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 24) {
                WrScreenHeader(
                    eyebrow: "Private space",
                    title: "Set up your AI",
                    subtitle: "Use Apple Intelligence, or configure Ollama or llmman now. You can change this later in Settings."
                )

                AiSetupContent()

                WrPrimaryButton("Continue", action: session.advanceOfflineSetup)
                    .accessibilityIdentifier("setup.ai.continue")
            }
            .padding(24)
            .frame(maxWidth: 700)
            .frame(maxWidth: .infinity)
        }
        .background(WrColors.background)
    }
}
