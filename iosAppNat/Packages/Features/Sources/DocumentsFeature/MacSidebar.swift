#if os(macOS)
import SwiftUI
import WrData
import WrDesign
import WrModels
import WrSession

/// The sidebar of the Mac window on the v4 redesign (Figma "v4 · Home"): the button that hides
/// it, the space or workspace switcher, Search, Home, Favorites and Graph, the folder tree, the
/// tags, and at the bottom who is using the app and the way to Settings.
public struct MacSidebar: View {
    @Binding private var selection: MainDestination
    @Binding private var columnVisibility: NavigationSplitViewVisibility
    private let router: DocumentsRouter
    @Environment(AppSession.self) private var session

    public init(
        selection: Binding<MainDestination>,
        columnVisibility: Binding<NavigationSplitViewVisibility>,
        router: DocumentsRouter
    ) {
        _selection = selection
        _columnVisibility = columnVisibility
        self.router = router
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            titleBar
            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    MacSpaceSwitcher()
                    destinations
                        .padding(.top, WrTheme.Space.s100)
                    SidebarFolders(router: router) { selection = .documents }
                        .padding(.top, 20)
                    SidebarTags(router: router) { selection = .documents }
                        .padding(.top, 20)
                }
            }
            .scrollIndicators(.never)
            MacSidebarFooter(isSettingsSelected: selection == .settings) { selection = .settings }
        }
        .padding([.horizontal, .bottom], WrTheme.Space.s100)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        .background(WrTheme.Colors.bgApp)
        .wrV4()
    }

    /// The 44 pt bar the traffic lights sit in, with the button that hides the sidebar at its
    /// right, where the window toolbar had it.
    private var titleBar: some View {
        HStack {
            Spacer()
            WrIconButton("Hide sidebar", systemImage: "sidebar.left") {
                withAnimation(.snappy) { columnVisibility = .detailOnly }
            }
            .accessibilityIdentifier("sideMenu.hide")
        }
        .frame(height: 44)
    }

    private var destinations: some View {
        VStack(spacing: 2) {
            WrNavItem("Search", systemImage: "magnifyingglass", shortcut: "⌘K", isSelected: selection == .search) {
                selection = .search
            }
            .accessibilityIdentifier("sideMenu.search")
            WrNavItem("Home", systemImage: "house", isSelected: isHome) {
                if selection == .documents { router.reset() }
                selection = .documents
            }
            .accessibilityIdentifier("sideMenu.documents")
            WrNavItem("Favorites", systemImage: "star", isSelected: selection == .documents && router.showsFavorites) {
                selection = .documents
                router.openFavorites()
            }
            .accessibilityIdentifier("sideMenu.favorites")
            WrNavItem("Graph", systemImage: "point.3.connected.trianglepath.dotted", isSelected: selection == .documents && router.showsGraph) {
                selection = .documents
                router.openGraph()
            }
            .accessibilityIdentifier("sideMenu.graph")
        }
    }

    /// Home is highlighted on the root of the documents only; inside a folder the tree shows
    /// where the canvas is.
    private var isHome: Bool {
        selection == .documents && router.path.isEmpty
    }
}

/// The space or workspace the window shows, with what can be switched to: the open space
/// changes workspace or space, the private space signs in or changes space.
private struct MacSpaceSwitcher: View {
    @Environment(AppSession.self) private var session
    @State private var isHovered = false

    var body: some View {
        Menu {
            if session.spaceType == .online {
                Button("Change workspace") { session.changeWorkspace() }
            } else {
                Button("Sign in") { session.signIn() }
            }
            Button("Switch space") { session.switchSpace() }
        } label: {
            HStack(spacing: WrTheme.Space.s100) {
                avatar
                Text(title)
                    .wrTextStyle(.labelLarge)
                    .foregroundStyle(WrTheme.Colors.textPrimary)
                    .lineLimit(1)
                Image(systemName: "chevron.down")
                    .font(.system(size: 10, weight: .semibold))
                    .foregroundStyle(WrTheme.Colors.iconSecondary)
                Spacer(minLength: 0)
            }
            .padding(.horizontal, WrTheme.Space.s100)
            .frame(height: WrTheme.Size.controlLg)
            .background(isHovered ? WrTheme.Colors.bgHover : .clear, in: RoundedRectangle(cornerRadius: WrTheme.Radius.md))
            .contentShape(RoundedRectangle(cornerRadius: WrTheme.Radius.md))
        }
        .menuStyle(.button)
        .buttonStyle(.plain)
        .menuIndicator(.hidden)
        .onHover { isHovered = $0 }
        .accessibilityIdentifier("sideMenu.space")
    }

    private var isPrivate: Bool { session.spaceType != .online }

    private var title: String {
        isPrivate ? String(localized: "Private space") : (session.workspace?.name ?? String(localized: "Documents"))
    }

    @ViewBuilder
    private var avatar: some View {
        if isPrivate {
            WrAvatar(.systemImage("lock.fill"), tone: .subtle, size: 24, isRound: false)
        } else {
            WrAvatar(.initials(WrAvatar.initials(of: title, limit: 1)), tone: .accent, size: 24, isRound: false)
        }
    }
}

/// The bottom of the sidebar: who is using the app, or that the private space stays on this
/// device, and the button to Settings.
private struct MacSidebarFooter: View {
    let isSettingsSelected: Bool
    let openSettings: () -> Void
    @Environment(AppSession.self) private var session

    var body: some View {
        HStack(spacing: WrTheme.Space.s100) {
            avatar
            VStack(alignment: .leading, spacing: 0) {
                Text(name)
                    .wrTextStyle(.labelMedium)
                    .foregroundStyle(WrTheme.Colors.textPrimary)
                    .lineLimit(1)
                HStack(spacing: WrTheme.Space.s50) {
                    if session.spaceType != .online {
                        Circle()
                            .fill(WrTheme.Colors.iconTertiary)
                            .frame(width: 6, height: 6)
                    }
                    Text(detail)
                        .wrTextStyle(.labelSmall)
                        .foregroundStyle(WrTheme.Colors.textTertiary)
                        .lineLimit(1)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            WrIconButton("Settings", systemImage: "slider.horizontal.3", isSelected: isSettingsSelected, action: openSettings)
                .accessibilityIdentifier("sideMenu.settings")
        }
        .padding(.leading, WrTheme.Space.s100)
        .padding(.trailing, WrTheme.Space.s50)
        .frame(height: WrTheme.Size.controlXl)
        .accessibilityElement(children: .contain)
    }

    @ViewBuilder
    private var avatar: some View {
        if let user = session.user, session.spaceType == .online {
            WrAvatar(.initials(WrAvatar.initials(of: user.name.isEmpty ? user.email : user.name)), tone: .subtle, size: 28, isRound: true)
        } else {
            WrAvatar(.systemImage("lock.fill"), tone: .subtle, size: 28, isRound: true)
        }
    }

    private var name: String {
        if session.spaceType == .online, let user = session.user {
            return user.name.isEmpty ? user.email : user.name
        }
        return String(localized: "Private space")
    }

    private var detail: String {
        if session.spaceType == .online, let user = session.user {
            return user.email
        }
        return String(localized: "On this device · offline")
    }
}

/// "TAGS" and the tags of the workspace; a tag opens its documents like a folder.
private struct SidebarTags: View {
    let router: DocumentsRouter
    let onOpen: () -> Void
    @Environment(AppSession.self) private var session
    @State private var tags: [DocumentTag] = []

    var body: some View {
        Group {
            if !tags.isEmpty {
                VStack(alignment: .leading, spacing: 2) {
                    WrSectionLabel("Tags")
                        .frame(height: 24)
                        .padding(.leading, WrTheme.Space.s100)
                    VStack(alignment: .leading, spacing: 1) {
                        ForEach(tags) { tag in
                            TagRow(tag: tag) {
                                onOpen()
                                router.openFromSidebar(tag: tag)
                            }
                        }
                    }
                }
            }
        }
        .task(id: router.treeVersion) {
            let loaded = (try? await session.documents.tags()) ?? []
            tags = loaded.sorted { $0.name.localizedCaseInsensitiveCompare($1.name) == .orderedAscending }
        }
    }
}

private struct TagRow: View {
    let tag: DocumentTag
    let action: () -> Void
    @State private var isHovered = false

    var body: some View {
        Button(action: action) {
            HStack(spacing: WrTheme.Space.s50) {
                Image(systemName: "number")
                    .font(.system(size: 11, weight: .medium))
                    .foregroundStyle(WrTheme.Colors.iconSecondary)
                    .frame(width: 14, height: 14)
                Text(tag.name)
                    .wrTextStyle(.bodySmall)
                    .foregroundStyle(WrTheme.Colors.textSecondary)
                    .lineLimit(1)
                Spacer(minLength: 0)
            }
            .padding(.leading, WrTheme.Space.s300)
            .padding(.trailing, WrTheme.Space.s100)
            .frame(height: 30)
            .background(isHovered ? WrTheme.Colors.bgHover : .clear, in: RoundedRectangle(cornerRadius: WrTheme.Radius.md))
            .contentShape(RoundedRectangle(cornerRadius: WrTheme.Radius.md))
        }
        .buttonStyle(.plain)
        .onHover { isHovered = $0 }
        .accessibilityIdentifier("sideMenu.tag.\(tag.name)")
    }
}
#endif
