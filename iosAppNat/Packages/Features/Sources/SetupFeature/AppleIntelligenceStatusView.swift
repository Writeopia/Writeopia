import SwiftUI
import WrData
import WrDesign

/// Whether Apple Intelligence can answer on this device, with the way to turn it on.
public struct AppleIntelligenceStatusView: View {
    @State private var status = AppleIntelligenceAi.status
    @Environment(\.scenePhase) private var scenePhase

    public init() {}

    public var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 12) {
                Image(systemName: status.isAvailable ? "apple.intelligence" : "exclamationmark.triangle")
                    .foregroundStyle(status.isAvailable ? WrColors.accent : .orange)
                    .frame(width: 20)

                VStack(alignment: .leading, spacing: 2) {
                    Text(status.isAvailable ? "Apple Intelligence available" : "Apple Intelligence unavailable")
                        .font(.headline)
                        .foregroundStyle(WrColors.textLight)
                    Text(status.message)
                        .font(.footnote)
                        .foregroundStyle(WrColors.textLighter)
                }

                Spacer()

                if status == .notEnabled, let url = SystemSettings.appleIntelligenceURL {
                    Link("Open Settings", destination: url)
                }
            }
        }
        .accessibilityIdentifier("setup.ai.appleIntelligence")
        .onChange(of: scenePhase) { _, phase in
            // Back from the system settings: the user may have turned it on.
            if phase == .active { status = AppleIntelligenceAi.status }
        }
    }
}
