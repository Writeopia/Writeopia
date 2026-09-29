import SwiftUI
import WrDesign
import WrSession

/// "Choose your space": the private (offline) space or the open (connected) space. The two
/// cards sit side by side and fill the height when the window is wide, like the Compose app,
/// and stack when it's narrow.
public struct SpaceChoiceView: View {
    public init() {}

    public var body: some View {
        SpaceChoiceLayout()
            .readsWideLayout()
    }
}

private struct SpaceChoiceLayout: View {
    @Environment(AppSession.self) private var session
    @Environment(\.isWideLayout) private var isWideLayout

    var body: some View {
        Group {
            if isWideLayout {
                VStack(alignment: .leading, spacing: 24) {
                    header
                    HStack(spacing: 16) {
                        cards
                    }
                }
                .padding(24)
                .frame(maxWidth: 1000)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else {
                ScrollView {
                    VStack(alignment: .leading, spacing: 24) {
                        header
                        cards
                    }
                    .padding(24)
                    .frame(maxWidth: 700)
                    .frame(maxWidth: .infinity)
                }
            }
        }
        .background(WrColors.background)
    }

    private var header: some View {
        WrScreenHeader(eyebrow: "Choose your space", title: "Where are you writing today?")
    }

    @ViewBuilder
    private var cards: some View {
        SpaceCard(
            label: "Private space — offline",
            title: "Nothing ever leaves the room.",
            description: "Your notes stay on this device. No account, no telemetry, no cloud.",
            systemImage: "lock.shield",
            chips: ["llama3", "mistral", "deepseek-r1"],
            fillsHeight: isWideLayout,
            action: session.chooseOfflineSpace
        )
        .accessibilityIdentifier("space.private")

        SpaceCard(
            label: "Open space — connected",
            title: "Bring in the big brains.",
            description: "Sync your notes, work with your team and use frontier models for the drafts that deserve them.",
            systemImage: "globe",
            chips: ["claude", "gemini", "gpt"],
            fillsHeight: isWideLayout,
            action: session.chooseOnlineSpace
        )
        .accessibilityIdentifier("space.open")
    }
}

private struct SpaceCard: View {
    let label: LocalizedStringKey
    let title: LocalizedStringKey
    let description: LocalizedStringKey
    let systemImage: String
    let chips: [String]
    let fillsHeight: Bool
    let action: () -> Void
    @State private var isHovered = false

    var body: some View {
        Button(action: action) {
            VStack(alignment: .leading, spacing: 14) {
                HStack {
                    Label(label, systemImage: systemImage)
                        .textCase(.uppercase)
                        .font(.caption.weight(.bold))
                        .foregroundStyle(WrColors.accent)
                    Spacer()
                    if !fillsHeight {
                        Image(systemName: "arrow.right")
                            .foregroundStyle(WrColors.textLighter)
                    }
                }

                Text(title)
                    .font(.title2.bold())
                    .foregroundStyle(WrColors.textLight)
                    .multilineTextAlignment(.leading)

                Text(description)
                    .font(.callout)
                    .foregroundStyle(WrColors.textLighter)
                    .multilineTextAlignment(.leading)

                if !chips.isEmpty {
                    chipsRow
                }

                if fillsHeight {
                    Spacer(minLength: 0)
                    HStack {
                        Spacer()
                        Label("Enter", systemImage: "arrow.right")
                            .labelStyle(.titleAndIcon)
                            .font(.callout.weight(.semibold))
                            .foregroundStyle(isHovered ? WrColors.accent : WrColors.textLighter)
                    }
                }
            }
            .padding(20)
            .frame(maxWidth: .infinity, maxHeight: fillsHeight ? .infinity : nil, alignment: .topLeading)
            .background(WrColors.surface, in: RoundedRectangle(cornerRadius: 20))
            .overlay {
                RoundedRectangle(cornerRadius: 20)
                    .strokeBorder(isHovered ? WrColors.accent : WrColors.divider)
            }
            .contentShape(RoundedRectangle(cornerRadius: 20))
        }
        .buttonStyle(.plain)
        .onHover { isHovered = $0 }
        .animation(.easeInOut(duration: 0.2), value: isHovered)
    }

    private var chipsRow: some View {
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
}
