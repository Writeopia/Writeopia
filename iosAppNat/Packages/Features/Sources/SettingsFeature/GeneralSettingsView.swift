import SwiftUI
import WrDesign
import WrModels
import WrSession

struct GeneralSettingsView: View {
    @Environment(AppSession.self) private var session

    var body: some View {
        @Bindable var session = session

        Form {
            Section {
                HStack(spacing: 12) {
                    ForEach(ColorTheme.allCases) { theme in
                        ThemeOption(theme: theme, isSelected: session.colorTheme == theme) {
                            withAnimation(.snappy) { session.colorTheme = theme }
                        }
                    }
                }
                .padding(.vertical, 6)
            } header: {
                Text("Color theme")
            } footer: {
                Text("System follows the appearance of your device.")
            }

            Section("About") {
                LabeledContent("Version", value: Bundle.main.appVersion)
                LabeledContent("Space", value: session.spaceType == .online ? "Open space" : "Private space")
            }
        }
        .navigationTitle("General")
    }
}

private struct ThemeOption: View {
    let theme: ColorTheme
    let isSelected: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            VStack(spacing: 8) {
                Image(systemName: theme.systemImage)
                    .font(.title2)
                    .frame(height: 30)
                Text(theme.title)
                    .font(.footnote.weight(.semibold))
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 14)
            .foregroundStyle(isSelected ? WrColors.accent : .secondary)
            .background {
                RoundedRectangle(cornerRadius: 12)
                    .fill(isSelected ? WrColors.accent.opacity(0.12) : Color.clear)
            }
            .overlay {
                RoundedRectangle(cornerRadius: 12)
                    .strokeBorder(isSelected ? WrColors.accent : WrColors.divider, lineWidth: isSelected ? 2 : 1)
            }
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier("theme.\(theme.rawValue)")
        .accessibilityAddTraits(isSelected ? .isSelected : [])
    }
}

extension Bundle {
    var appVersion: String {
        let version = infoDictionary?["CFBundleShortVersionString"] as? String ?? "1.0"
        let build = infoDictionary?["CFBundleVersion"] as? String ?? "1"
        return "\(version) (\(build))"
    }
}
