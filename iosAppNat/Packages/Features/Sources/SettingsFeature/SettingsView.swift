import SwiftUI
import WrDesign
import WrSession

enum SettingsRoute: String, Hashable, CaseIterable {
    case general
    case teams
    case ai
    case account

    var title: LocalizedStringKey {
        switch self {
        case .general: "General"
        case .teams: "Teams"
        case .ai: "AI"
        case .account: "Account"
        }
    }

    var systemImage: String {
        switch self {
        case .general: "gearshape"
        case .teams: "person.3.fill"
        case .ai: "sparkles"
        case .account: "person.crop.circle"
        }
    }

    var color: Color {
        switch self {
        case .general: .gray
        case .teams: .blue
        case .ai: .purple
        case .account: .orange
        }
    }
}

public struct SettingsRootView: View {
    @Environment(AppSession.self) private var session
    @State private var macRoute: SettingsRoute = .general

    public init() {}

    public var body: some View {
        #if os(macOS)
        macBody
        #else
        stackBody
        #endif
    }

    #if os(macOS)
    /// The Mac: the sections on the left and the chosen one on the right, like System Settings.
    private var macBody: some View {
        HStack(spacing: 0) {
            VStack(alignment: .leading, spacing: 0) {
                AccountHeader()
                    .padding(.horizontal, 16)
                    .padding(.top, 20)
                    .padding(.bottom, 12)

                List(selection: $macRoute) {
                    ForEach(SettingsRoute.allCases, id: \.self) { route in
                        SettingsLabel(route.title, systemImage: route.systemImage, color: route.color)
                            .tag(route)
                            .accessibilityIdentifier("settings.\(route.rawValue)")
                    }
                }
                .listStyle(.sidebar)
                .scrollContentBackground(.hidden)
            }
            .frame(width: 240)
            .background(WrColors.surface.opacity(0.6))

            Divider()

            NavigationStack {
                // The grouped form centers itself in a wide window; capping its width and pinning
                // it to the leading edge keeps every page aligned with the sidebar.
                section(macRoute)
                    .scrollContentBackground(.hidden)
                    .frame(maxWidth: 820, alignment: .leading)
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
            }
            .id(macRoute)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
        .background(WrColors.background)
    }
    #endif

    @ViewBuilder
    private func section(_ route: SettingsRoute) -> some View {
        switch route {
        case .general: GeneralSettingsView()
        case .teams: TeamsSettingsView(session: session)
        case .ai: AiSettingsView(session: session)
        case .account: AccountSettingsView(session: session)
        }
    }

    private var stackBody: some View {
        NavigationStack {
            List {
                Section {
                    AccountHeader()
                }

                Section {
                    NavigationLink(value: SettingsRoute.general) {
                        SettingsLabel("General", systemImage: "gearshape", color: .gray)
                    }
                    NavigationLink(value: SettingsRoute.teams) {
                        SettingsLabel("Teams", systemImage: "person.3.fill", color: .blue)
                    }
                    NavigationLink(value: SettingsRoute.ai) {
                        SettingsLabel("AI", systemImage: "sparkles", color: .purple)
                    }
                    NavigationLink(value: SettingsRoute.account) {
                        SettingsLabel("Account", systemImage: "person.crop.circle", color: .orange)
                    }
                    .accessibilityIdentifier("settings.account")
                }
            }
            .navigationTitle("Settings")
            .navigationDestination(for: SettingsRoute.self, destination: section)
        }
    }
}

private struct AccountHeader: View {
    @Environment(AppSession.self) private var session

    var body: some View {
        HStack(spacing: 14) {
            Image(systemName: session.isOnline ? "person.crop.circle.fill" : "lock.shield.fill")
                .font(.system(size: 40))
                .foregroundStyle(WrColors.accent)

            VStack(alignment: .leading, spacing: 2) {
                Text(session.isOnline ? (session.user?.name ?? String(localized: "Signed in")) : String(localized: "Private space"))
                    .font(.headline)
                Text(subtitle)
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            }
        }
        .padding(.vertical, 4)
    }

    private var subtitle: String {
        if session.isOnline {
            return [session.user?.email, session.user?.planName, session.workspace?.name]
                .compactMap { $0 }
                .joined(separator: " · ")
        }
        return String(localized: "Your notes stay on this device")
    }
}

struct SettingsLabel: View {
    let title: LocalizedStringKey
    let systemImage: String
    let color: Color

    init(_ title: LocalizedStringKey, systemImage: String, color: Color) {
        self.title = title
        self.systemImage = systemImage
        self.color = color
    }

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: systemImage)
                .font(.footnote.weight(.semibold))
                .foregroundStyle(.white)
                .frame(width: 28, height: 28)
                .background(color.gradient, in: RoundedRectangle(cornerRadius: 7))
            Text(title)
        }
        .padding(.vertical, 2)
    }
}

/// Shown on screens that only make sense in the open space.
struct OfflineNotice: View {
    @Environment(AppSession.self) private var session
    let title: LocalizedStringKey
    let message: LocalizedStringKey

    var body: some View {
        ContentUnavailableView {
            Label(title, systemImage: "icloud.slash")
        } description: {
            Text(message)
        } actions: {
            Button("Sign in", action: session.signIn)
                .buttonStyle(.borderedProminent)
                .tint(WrColors.accent)
        }
    }
}
