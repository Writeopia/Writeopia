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

/// A lantern hanging above the window, like the first native version and the Compose app: only
/// its glow reaches the screen, brightest at the top and fading down, with the corners and the
/// bottom of the room in shade.
///
/// At rest it hangs in the middle, dimmed. Over the open space it brightens and swings to
/// follow the pointer, a little behind it. Over the private space the room goes dark and only
/// a narrow, faint beam is left over the card, "nothing ever leaves the room". With Reduce
/// Motion on it doesn't follow the pointer, it only changes in strength.
private struct LanternLight: View {
    let hovered: HoveredSpace
    let pointerX: CGFloat?
    @Environment(\.colorScheme) private var colorScheme
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        GeometryReader { proxy in
            let size = proxy.size
            let beam = beamSize(in: size)

            ZStack(alignment: .topLeading) {
                // The shade: the room is darker away from the lantern.
                LinearGradient(
                    colors: [.black.opacity(0), .black.opacity(shade)],
                    startPoint: .top,
                    endPoint: .bottom
                )

                // The glow: an ellipse of light whose top half is above the window.
                EllipticalGradient(
                    colors: [glow.opacity(0.95), glow.opacity(0.4), glow.opacity(0)],
                    center: .center,
                    startRadiusFraction: 0,
                    endRadiusFraction: 0.5
                )
                .frame(width: beam.width, height: beam.height)
                .position(x: lanternX(in: size), y: 0)
                .blur(radius: 24)
                .opacity(intensity)
                .blendMode(colorScheme == .dark ? .plusLighter : .normal)
            }
            .frame(width: size.width, height: size.height)
        }
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }

    /// Follows the pointer while a card is hovered; in the middle otherwise.
    private func lanternX(in size: CGSize) -> CGFloat {
        guard hovered != .none, let pointerX, !reduceMotion else { return size.width / 2 }
        return min(max(pointerX, 0), size.width)
    }

    private func beamSize(in size: CGSize) -> CGSize {
        switch hovered {
        case .none: CGSize(width: size.width * 1.2, height: size.height * 1.3)
        case .openSpace: CGSize(width: size.width * 0.9, height: size.height * 1.6)
        case .privateSpace: CGSize(width: size.width * 0.45, height: size.height * 1.4)
        }
    }

    private var glow: Color {
        colorScheme == .dark ? Color(white: 0.85) : .white
    }

    private var intensity: Double {
        switch hovered {
        case .none: 0.7
        case .openSpace: 1
        case .privateSpace: 0.16
        }
    }

    /// Light mode needs the shade for the white glow to read; dark mode is mostly shade.
    private var shade: Double {
        switch (hovered, colorScheme) {
        case (.privateSpace, _): 0.45
        case (_, .dark): 0.3
        case (.openSpace, _): 0.16
        default: 0.09
        }
    }
}

private struct SpaceChoiceLayout: View {
    @Environment(AppSession.self) private var session
    @Environment(\.isWideLayout) private var isWideLayout
    @Environment(\.colorScheme) private var systemScheme
    @State private var hovered: HoveredSpace = .none
    @State private var pointerX: CGFloat?

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
        .background {
            ZStack {
                WrTheme.Colors.bgApp
                LanternLight(hovered: hovered, pointerX: pointerX)
            }
            .ignoresSafeArea()
        }
        // Where the pointer is across the screen, so the lantern can follow it.
        .onContinuousHover(coordinateSpace: .local) { phase in
            switch phase {
            case .active(let location):
                withAnimation(.easeOut(duration: 0.6)) { pointerX = location.x }
            case .ended:
                pointerX = nil
            }
        }
        .wrV4()
        // The room goes dark while the pointer is on the private space.
        .environment(\.colorScheme, hovered == .privateSpace ? .dark : systemScheme)
        .animation(.easeInOut(duration: 0.35), value: hovered)
    }

    private var header: some View {
        VStack(alignment: .leading, spacing: WrTheme.Space.s300) {
            WrBrandMark()

            WrTitleBlock(
                eyebrow: "Choose your space",
                title: "Where are you writing today?",
                subtitle: "You can have both and switch anytime in Settings › Account.",
                titleStyle: isWideLayout ? .displayLarge : .headingXL
            )
        }
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
