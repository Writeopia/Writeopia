import SwiftUI

// Pieces of the v4 app shell (sidebar and canvas of the Mac window), from the Figma library
// (page "01 Components · v4") and the frames "v4 · Home" and "v4 · Folder".

// MARK: - Icon button

/// `v4/IconButton`: a 32 pt square with an 18 pt icon. `isSelected` gives it the selected
/// background, for a view option that is on.
public struct WrIconButton: View {
    private let systemImage: String
    private let label: LocalizedStringKey
    private let isSelected: Bool
    private let action: () -> Void

    public init(_ label: LocalizedStringKey, systemImage: String, isSelected: Bool = false, action: @escaping () -> Void) {
        self.label = label
        self.systemImage = systemImage
        self.isSelected = isSelected
        self.action = action
    }

    public var body: some View {
        Button(action: action) {
            WrIconButtonLabel(systemImage: systemImage, isSelected: isSelected)
        }
        .buttonStyle(.plain)
        .help(Text(label))
        .accessibilityLabel(Text(label))
    }
}

/// The look of `WrIconButton`, for a `Menu` whose label must look like one.
public struct WrIconButtonLabel: View {
    private let systemImage: String
    private let isSelected: Bool
    @State private var isHovered = false

    public init(systemImage: String, isSelected: Bool = false) {
        self.systemImage = systemImage
        self.isSelected = isSelected
    }

    public var body: some View {
        Image(systemName: systemImage)
            .font(.system(size: 14, weight: .medium))
            .foregroundStyle(isSelected ? WrTheme.Colors.iconPrimary : WrTheme.Colors.iconSecondary)
            .frame(width: 18, height: 18)
            .frame(width: WrTheme.Size.controlMd, height: WrTheme.Size.controlMd)
            .background(
                isSelected ? WrTheme.Colors.bgSelected : (isHovered ? WrTheme.Colors.bgHover : .clear),
                in: RoundedRectangle(cornerRadius: WrTheme.Radius.md)
            )
            .contentShape(RoundedRectangle(cornerRadius: WrTheme.Radius.md))
            .onHover { isHovered = $0 }
    }
}

// MARK: - Nav item

/// `v4/NavItem`: a 32 pt row of the sidebar, with an icon, a title and an optional shortcut.
public struct WrNavItem: View {
    private let title: LocalizedStringKey
    private let systemImage: String
    private let shortcut: String?
    private let isSelected: Bool
    private let action: () -> Void
    @State private var isHovered = false

    public init(
        _ title: LocalizedStringKey,
        systemImage: String,
        shortcut: String? = nil,
        isSelected: Bool = false,
        action: @escaping () -> Void
    ) {
        self.title = title
        self.systemImage = systemImage
        self.shortcut = shortcut
        self.isSelected = isSelected
        self.action = action
    }

    public var body: some View {
        Button(action: action) {
            HStack(spacing: 10) {
                Image(systemName: systemImage)
                    .font(.system(size: 14, weight: .regular))
                    .foregroundStyle(isSelected ? WrTheme.Colors.iconPrimary : WrTheme.Colors.iconSecondary)
                    .frame(width: 18, height: 18)
                Text(title)
                    .wrTextStyle(isSelected ? .labelLarge : .bodyMedium)
                    .foregroundStyle(isSelected ? WrTheme.Colors.textPrimary : WrTheme.Colors.textSecondary)
                    .lineLimit(1)
                    .frame(maxWidth: .infinity, alignment: .leading)
                if let shortcut {
                    WrShortcutBadge(shortcut)
                }
            }
            .padding(.leading, 10)
            .padding(.trailing, WrTheme.Space.s100)
            .frame(height: WrTheme.Size.controlMd)
            .background(
                isSelected ? WrTheme.Colors.bgSelected : (isHovered ? WrTheme.Colors.bgHover : .clear),
                in: RoundedRectangle(cornerRadius: WrTheme.Radius.md)
            )
            .contentShape(RoundedRectangle(cornerRadius: WrTheme.Radius.md))
        }
        .buttonStyle(.plain)
        .onHover { isHovered = $0 }
        .accessibilityAddTraits(isSelected ? .isSelected : [])
    }
}

/// The key combination at the end of a nav item, like "⌘K".
public struct WrShortcutBadge: View {
    private let shortcut: String

    public init(_ shortcut: String) {
        self.shortcut = shortcut
    }

    public var body: some View {
        Text(verbatim: shortcut)
            .wrTextStyle(.labelSmall)
            .foregroundStyle(WrTheme.Colors.textTertiary)
            .padding(.horizontal, 5)
            .padding(.vertical, 1)
            .overlay {
                RoundedRectangle(cornerRadius: WrTheme.Radius.sm)
                    .strokeBorder(WrTheme.Colors.borderSubtle)
            }
            .accessibilityHidden(true)
    }
}

// MARK: - Section label

/// The uppercase label above a group, like "FOLDERS", with an optional trailing view (an add
/// button in the sidebar, the sort order in the canvas).
public struct WrSectionLabel<Trailing: View>: View {
    private let title: LocalizedStringKey
    @ViewBuilder private let trailing: Trailing

    public init(_ title: LocalizedStringKey, @ViewBuilder trailing: () -> Trailing = { EmptyView() }) {
        self.title = title
        self.trailing = trailing()
    }

    public var body: some View {
        HStack(spacing: 0) {
            Text(title)
                .wrTextStyle(.overline)
                .textCase(.uppercase)
                .foregroundStyle(WrTheme.Colors.textTertiary)
                .lineLimit(1)
                .accessibilityAddTraits(.isHeader)
            Spacer(minLength: WrTheme.Space.s100)
            trailing
        }
    }
}

// MARK: - Avatar

/// The square or round tile with initials or an icon: the workspace of the switcher (accent,
/// square) and the person of the sidebar footer (selected tone, round).
public struct WrAvatar: View {
    public enum Content: Sendable {
        case initials(String)
        case systemImage(String)
    }

    public enum Tone: Sendable {
        /// Accent background, white initials: the workspace.
        case accent
        /// Selected background: the person, or the private space.
        case subtle
    }

    private let content: Content
    private let tone: Tone
    private let size: CGFloat
    private let isRound: Bool

    public init(_ content: Content, tone: Tone, size: CGFloat, isRound: Bool) {
        self.content = content
        self.tone = tone
        self.size = size
        self.isRound = isRound
    }

    public var body: some View {
        Group {
            switch content {
            case .initials(let initials):
                Text(verbatim: initials)
                    .wrTextStyle(.overline)
                    .textCase(.uppercase)
            case .systemImage(let name):
                Image(systemName: name)
                    .font(.system(size: size * 0.45, weight: .semibold))
            }
        }
        .foregroundStyle(tone == .accent ? WrTheme.Colors.textOnAccent : WrTheme.Colors.textPrimary)
        .frame(width: size, height: size)
        .background(
            tone == .accent ? WrTheme.Colors.accent : WrTheme.Colors.bgSelected,
            in: RoundedRectangle(cornerRadius: isRound ? size / 2 : WrTheme.Radius.md)
        )
        .accessibilityHidden(true)
    }

    /// Up to two initials of a name: "Murilo Muniz" is "MM", "Writeopia" is "W".
    public static func initials(of name: String, limit: Int = 2) -> String {
        let words = name.split(whereSeparator: { $0.isWhitespace })
        let letters = words.prefix(limit).compactMap(\.first).map(String.init)
        return letters.isEmpty ? "?" : letters.joined().uppercased()
    }
}

// MARK: - Canvas

public extension View {
    /// The white canvas of the Mac window: rounded, inset from the window edges by 8 pt on the
    /// top, right and bottom, next to the sidebar on the left, on the app background.
    /// `insetsLeading` insets the left too, for when the sidebar is hidden.
    func wrCanvas(insetsLeading: Bool = false) -> some View {
        background(WrTheme.Colors.bgCanvas)
            .clipShape(RoundedRectangle(cornerRadius: WrTheme.Radius.lg))
            .padding([.top, .trailing, .bottom], WrTheme.Space.s100)
            .padding(.leading, insetsLeading ? WrTheme.Space.s100 : 0)
            .background(WrTheme.Colors.bgApp)
    }
}
