import SetupFeature
import SwiftUI
import WrDesign
import WrModels
import WrSession

struct GeneralSettingsView: View {
    @Environment(AppSession.self) private var session

    /// The grouped form on every platform, like the other settings pages.
    var body: some View {
        formBody
    }

    private var formBody: some View {
        @Bindable var session = session

        return WrForm {
            Section {
                HStack(spacing: 12) {
                    ForEach(ColorTheme.allCases) { theme in
                        ThemeOption(theme: theme, isSelected: session.colorTheme == theme) {
                            withAnimation(.snappy) { session.colorTheme = theme }
                        }
                    }
                }
                .padding(.vertical, 6)
                // The cards are the controls; the row needs no box of its own.
                .listRowBackground(Color.clear)
                .listRowInsets(EdgeInsets())
            } header: {
                Text("Color theme")
            } footer: {
                Text("System follows the appearance of your device.")
            }

            #if os(macOS)
            if session.spaceType == .offline {
                Section {
                    PrivateSpaceFolderPicker()
                        .listRowBackground(Color.clear)
                        .listRowInsets(EdgeInsets())
                } header: {
                    Text("Local folder")
                } footer: {
                    Text("Pick a folder in your computer to keep your workspace.")
                }
            }
            #endif

            Section("About") {
                LabeledContent("Version", value: Bundle.main.appVersion)
                LabeledContent("Space", value: session.spaceType == .online ? String(localized: "Open space") : String(localized: "Private space"))
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
            // The whole card takes the click, not only its icon and text.
            .contentShape(RoundedRectangle(cornerRadius: 12))
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
