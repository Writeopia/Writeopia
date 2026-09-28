import SwiftUI
import WrDesign
import WrSession

enum SettingsRoute: Hashable {
    case general
    case teams
    case ai
    case account
}

public struct SettingsRootView: View {
    @Environment(AppSession.self) private var session

    public init() {}

    public var body: some View {
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
            .navigationDestination(for: SettingsRoute.self) { route in
                switch route {
                case .general: GeneralSettingsView()
                case .teams: TeamsSettingsView(session: session)
                case .ai: AiSettingsView(session: session)
                case .account: AccountSettingsView(session: session)
                }
            }
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
        Label {
            Text(title)
        } icon: {
            Image(systemName: systemImage)
                .font(.footnote.weight(.semibold))
                .foregroundStyle(.white)
                .frame(width: 28, height: 28)
                .background(color.gradient, in: RoundedRectangle(cornerRadius: 7))
        }
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
