import SetupFeature
import SwiftUI
import WrDesign
import WrModels
import WrSession

struct GeneralSettingsView: View {
    @Environment(AppSession.self) private var session

    var body: some View {
        #if os(macOS)
        macBody
        #else
        formBody
        #endif
    }

    #if os(macOS)
    /// Laid out by hand on the Mac: the grouped form would box the theme cards and the folder
    /// picker, which are controls of their own.
    private var macBody: some View {
        @Bindable var session = session

        return ScrollView {
            VStack(alignment: .leading, spacing: 28) {
                MacSettingsBlock("Color theme", footer: "System follows the appearance of your device.") {
                    HStack(spacing: 12) {
                        ForEach(ColorTheme.allCases) { theme in
                            ThemeOption(theme: theme, isSelected: session.colorTheme == theme) {
                                withAnimation(.snappy) { session.colorTheme = theme }
                            }
                        }
                    }
                }

                if session.spaceType == .offline {
                    MacSettingsBlock("Local folder", footer: "Pick a folder in your computer to keep your workspace.") {
                        PrivateSpaceFolderPicker()
                    }
                }

                MacSettingsBlock("About") {
                    VStack(spacing: 0) {
                        aboutRow("Version", value: Bundle.main.appVersion)
                        Divider()
                        aboutRow("Space", value: session.spaceType == .online ? String(localized: "Open space") : String(localized: "Private space"))
                    }
                    .padding(.horizontal, 14)
                    .background(WrColors.surface.opacity(0.6), in: RoundedRectangle(cornerRadius: 10, style: .continuous))
                }
            }
            .padding(24)
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .navigationTitle("General")
    }

    private func aboutRow(_ title: LocalizedStringKey, value: String) -> some View {
        HStack {
            Text(title)
            Spacer()
            Text(value)
                .foregroundStyle(.secondary)
        }
        .padding(.vertical, 10)
    }
    #endif

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

#if os(macOS)
/// A titled block of the Mac settings, with the spacing of the grouped form and no box.
private struct MacSettingsBlock<Content: View>: View {
    let title: LocalizedStringKey
    var footer: LocalizedStringKey?
    @ViewBuilder let content: Content

    init(_ title: LocalizedStringKey, footer: LocalizedStringKey? = nil, @ViewBuilder content: () -> Content) {
        self.title = title
        self.footer = footer
        self.content = content()
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(title)
                .font(.headline)
            content
            if let footer {
                Text(footer)
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }
        }
    }
}
#endif

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
