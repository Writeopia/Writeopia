import SwiftUI
import WrDesign
import WrSession

/// "Choose your space": the private (offline) space or the open (connected) space.
public struct SpaceChoiceView: View {
    @Environment(AppSession.self) private var session

    public init() {}

    public var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 24) {
                WrScreenHeader(eyebrow: "Choose your space", title: "Where are you writing today?")

                SpaceCard(
                    label: "Private space — offline",
                    title: "Nothing ever leaves the room.",
                    description: "Your notes stay on this device. No account, no telemetry, no cloud.",
                    systemImage: "lock.shield",
                    chips: ["llama3", "mistral", "deepseek-r1"],
                    action: session.chooseOfflineSpace
                )
                .accessibilityIdentifier("space.private")

                SpaceCard(
                    label: "Open space — connected",
                    title: "Bring in the big brains.",
                    description: "Sync your notes, work with your team and use frontier models for the drafts that deserve them.",
                    systemImage: "globe",
                    chips: ["claude", "gemini", "gpt"],
                    action: session.chooseOnlineSpace
                )
                .accessibilityIdentifier("space.open")
            }
            .padding(24)
            .frame(maxWidth: 700)
            .frame(maxWidth: .infinity)
        }
        .background(WrColors.background)
    }
}

private struct SpaceCard: View {
    let label: String
    let title: String
    let description: String
    let systemImage: String
    let chips: [String]
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            VStack(alignment: .leading, spacing: 14) {
                HStack {
                    Label(label.uppercased(), systemImage: systemImage)
                        .font(.caption.weight(.bold))
                        .foregroundStyle(WrColors.accent)
                    Spacer()
                    Image(systemName: "arrow.right")
                        .foregroundStyle(WrColors.textLighter)
                }

                Text(title)
                    .font(.title2.bold())
                    .foregroundStyle(WrColors.textLight)
                    .multilineTextAlignment(.leading)

                Text(description)
                    .font(.callout)
                    .foregroundStyle(WrColors.textLighter)
                    .multilineTextAlignment(.leading)

                HStack(spacing: 8) {
                    ForEach(chips, id: \.self) { chip in
                        Text(chip)
                            .font(.caption.monospaced())
                            .padding(.horizontal, 10)
                            .padding(.vertical, 4)
                            .background(WrColors.divider.opacity(0.5), in: Capsule())
                            .foregroundStyle(WrColors.textLight)
                    }
                }
            }
            .padding(20)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(WrColors.surface, in: RoundedRectangle(cornerRadius: 20))
            .overlay {
                RoundedRectangle(cornerRadius: 20)
                    .strokeBorder(WrColors.divider)
            }
        }
        .buttonStyle(.plain)
    }
}
