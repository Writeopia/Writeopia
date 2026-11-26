import SwiftUI
import WrDesign
import WrSession

/// "Choose your space": the private (offline) space or the open (connected) space, on the v4
/// design. The two cards sit side by side when the window is wide and stack when it's narrow.
///
/// Pointing at a card highlights it and dims the other. Pointing at the private space also
/// turns the whole screen dark, "nothing ever leaves the room"; the open space keeps it light.
public struct SpaceChoiceView: View {
    public init() {}

    public var body: some View {
        SpaceChoiceLayout()
            .readsWideLayout()
    }
}

private enum HoveredSpace {
    case none
    case privateSpace
    case openSpace
}

private struct SpaceChoiceLayout: View {
    @Environment(AppSession.self) private var session
    @Environment(\.isWideLayout) private var isWideLayout
    @Environment(\.colorScheme) private var systemScheme
    @State private var hovered: HoveredSpace = .none

    var body: some View {
        Group {
            if isWideLayout {
                VStack(alignment: .leading, spacing: WrTheme.Space.s400) {
                    header
                    HStack(alignment: .top, spacing: WrTheme.Space.s300) {
                        cards
                    }
                    .fixedSize(horizontal: false, vertical: true)
                }
                .padding(WrTheme.Space.s400)
                .frame(maxWidth: 1024)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else {
                ScrollView {
                    VStack(alignment: .leading, spacing: WrTheme.Space.s300) {
                        header
                        cards
                    }
                    .padding(WrTheme.Space.s300)
                    .frame(maxWidth: 700)
                    .frame(maxWidth: .infinity)
                }
            }
        }
        .background(WrTheme.Colors.bgApp.ignoresSafeArea())
        .wrV4()
        // The room goes dark while the pointer is on the private space.
        .environment(\.colorScheme, hovered == .privateSpace ? .dark : systemScheme)
        .animation(.easeInOut(duration: 0.35), value: hovered)
    }

    private var header: some View {
        WrTitleBlock(
            eyebrow: "Choose your space",
            title: "Where are you writing today?",
            subtitle: "You can have both and switch anytime in Settings › Account.",
            titleStyle: isWideLayout ? .displayLarge : .headingXL
        )
    }

    @ViewBuilder
    private var cards: some View {
        WrSpaceCard(
            eyebrow: "Private space",
            title: "Nothing ever leaves the room.",
            description: "Your notes stay on this device. No account, no telemetry, no cloud.",
            systemImage: "lock",
            tone: .neutral,
            status: WrPill("Offline", kind: .neutral),
            models: ["llama3", "mistral", "deepseek-r1"],
            benefits: ["Works fully offline", "Plain files in a folder you pick", "AI runs on this computer"],
            actionTitle: "Enter private space",
            footnote: "No account needed",
            isHighlighted: hovered == .privateSpace,
            isDimmed: hovered == .openSpace,
            actionIdentifier: "space.private",
            onHover: { hover(.privateSpace, $0) },
            action: session.chooseOfflineSpace
        )

        WrSpaceCard(
            eyebrow: "Open space",
            title: "Bring in the big brains.",
            description: "Sync your notes, work with your team and use frontier models for the drafts that deserve them.",
            systemImage: "cloud",
            tone: .accent,
            status: WrPill("Connected", kind: .success),
            models: ["claude", "gemini", "gpt"],
            benefits: ["Sync across all your devices", "Shared workspaces for teams", "Publish documents to the web"],
            actionTitle: "Enter open space",
            footnote: "Sign in or create an account",
            isHighlighted: hovered == .openSpace,
            isDimmed: hovered == .privateSpace,
            actionIdentifier: "space.open",
            onHover: { hover(.openSpace, $0) },
            action: session.chooseOnlineSpace
        )
    }

    private func hover(_ space: HoveredSpace, _ isHovering: Bool) {
        if isHovering {
            hovered = space
        } else if hovered == space {
            hovered = .none
        }
    }
}
