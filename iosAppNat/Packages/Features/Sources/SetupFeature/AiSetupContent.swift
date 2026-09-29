import SwiftUI
import WrData
import WrDesign
import WrSession

/// The AI choices of the private space: Apple Intelligence on the device, or a local model
/// served by Ollama or llmman. Shared by the first-run setup and Settings.
public struct AiSetupContent: View {
    @Environment(AppSession.self) private var session

    public init() {}

    public var body: some View {
        @Bindable var session = session

        VStack(alignment: .leading, spacing: 20) {
            Picker("Run AI with", selection: $session.aiProvider) {
                ForEach(AiProvider.offlineChoices) { provider in
                    Text(provider.title).tag(provider)
                }
            }
            .pickerStyle(.segmented)
            .accessibilityIdentifier("setup.ai.provider")

            card(title: "Apple Intelligence", systemImage: "apple.intelligence") {
                AppleIntelligenceStatusView()
            }

            card(title: "Local AI (Ollama or llmman)", systemImage: "desktopcomputer") {
                LocalAiConfigView()
            }
        }
    }

    private func card<Content: View>(title: LocalizedStringKey, systemImage: String, @ViewBuilder content: () -> Content) -> some View {
        VStack(alignment: .leading, spacing: 14) {
            Label(title, systemImage: systemImage)
                .textCase(.uppercase)
                .font(.caption.weight(.bold))
                .foregroundStyle(WrColors.accent)
            content()
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(WrColors.surface, in: RoundedRectangle(cornerRadius: 16))
        .overlay {
            RoundedRectangle(cornerRadius: 16)
                .strokeBorder(WrColors.divider)
        }
    }
}

public extension AiProvider {
    /// The providers that work without the backend.
    static var offlineChoices: [AiProvider] { [.appleIntelligence, .ollama] }
}
