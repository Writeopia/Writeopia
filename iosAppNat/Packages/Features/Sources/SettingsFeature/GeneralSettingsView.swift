import SetupFeature
import SwiftUI
import WrData
import WrDesign
import WrModels
import WrSession

struct GeneralSettingsView: View {
    @Environment(AppSession.self) private var session
    /// Easter egg: called with the document about the writer of the version, ten taps on it.
    var onOpenDocument: ((WrDocument) -> Void)?
    @State private var versionTaps = 0
    /// Hidden on the Mac: ten clicks on the space of an online workspace offer to erase its
    /// local copy and bring it whole from the backend again.
    @State private var spaceClicks = 0
    @State private var showsResync = false
    @State private var resyncError: String?

    /// The grouped form on every platform, like the other settings pages.
    var body: some View {
        formBody
    }

    private func tapVersion() {
        versionTaps += 1
        guard versionTaps >= 10 else { return }
        versionTaps = 0

        let repository = session.documents
        Task {
            guard let document = try? await EasterEggs.createVersionWriterDocument(in: repository) else { return }
            onOpenDocument?(document)
        }
    }

    private func clickSpace() {
        guard session.canResyncWorkspace else { return }
        spaceClicks += 1
        guard spaceClicks >= 10 else { return }
        spaceClicks = 0
        showsResync = true
    }

    private func resync() {
        Task {
            do {
                try await session.eraseAndResyncWorkspace()
            } catch {
                resyncError = error.localizedDescription
            }
        }
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
                    .contentShape(Rectangle())
                    .onTapGesture { tapVersion() }
                LabeledContent("Space", value: session.spaceType == .online ? String(localized: "Open space") : String(localized: "Private space"))
                    #if os(macOS)
                    .contentShape(Rectangle())
                    .onTapGesture { clickSpace() }
                    #endif
            }
        }
        .navigationTitle("General")
        .alert("Erase and resync the workspace?", isPresented: $showsResync) {
            Button("Cancel", role: .cancel) {}
            Button("Erase and resync", role: .destructive) { resync() }
                .accessibilityIdentifier("general.resync.confirm")
        } message: {
            Text("Every document of this workspace will be removed from this device and downloaded again. Changes not synced yet will be lost.")
        }
        .alert(
            "The workspace couldn't be erased",
            isPresented: Binding(get: { resyncError != nil }, set: { if !$0 { resyncError = nil } })
        ) {
            Button("OK", role: .cancel) {}
        } message: {
            Text(resyncError ?? "")
        }
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
