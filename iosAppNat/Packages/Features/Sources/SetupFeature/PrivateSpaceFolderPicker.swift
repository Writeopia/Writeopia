import SwiftUI
import UniformTypeIdentifiers
import WrDesign
import WrSession

/// Where the private space is kept: the default location or a folder the user picks, like the
/// `WorkspacePathSelector` of the desktop app. Shown in the first-run setup and in Settings.
public struct PrivateSpaceFolderPicker: View {
    @Environment(AppSession.self) private var session
    @State private var showsPicker = false
    @State private var errorMessage: String?
    @Environment(\.wrUsesV4) private var usesV4

    public init() {}

    public var body: some View {
        Group {
            if usesV4 { v4Picker } else { legacyPicker }
        }
        .animation(.default, value: errorMessage)
        .fileImporter(isPresented: $showsPicker, allowedContentTypes: [.folder]) { result in
            switch result {
            case .success(let url):
                choose(url)
            case .failure(let error):
                errorMessage = error.localizedDescription
            }
        }
    }

    /// v4: the path in a quiet field, "Use default" and "Choose…" beside it.
    private var v4Picker: some View {
        VStack(alignment: .leading, spacing: WrTheme.Space.s100) {
            HStack(spacing: WrTheme.Space.s100) {
                HStack(spacing: WrTheme.Space.s100) {
                    Image(systemName: "folder")
                        .foregroundStyle(WrTheme.Colors.iconSecondary)
                    Text(folderName)
                        .wrTextStyle(.bodyMedium)
                        .foregroundStyle(WrTheme.Colors.textPrimary)
                        .lineLimit(1)
                        .truncationMode(.middle)
                        .help(folderName)
                    Spacer(minLength: 0)
                }
                .padding(.horizontal, WrTheme.Space.s150)
                .frame(height: 36)
                .background(WrTheme.Colors.bgHover, in: RoundedRectangle(cornerRadius: WrTheme.Radius.md))

                if session.privateSpaceFolder != nil {
                    WrButton("Use default", kind: .ghost) { choose(nil) }
                }
                WrButton("Choose…", kind: .secondary) { showsPicker = true }
                    .accessibilityIdentifier("folder.choose")
            }

            if let errorMessage {
                WrErrorText(errorMessage)
            } else {
                Label {
                    Text("Ready to use.")
                        .foregroundStyle(WrTheme.Colors.textTertiary)
                } icon: {
                    Image(systemName: "checkmark")
                        .foregroundStyle(WrTheme.Colors.success)
                }
                .wrTextStyle(.bodySmall)
            }
        }
    }

    private var legacyPicker: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(spacing: 12) {
                Image(systemName: "folder")
                    .foregroundStyle(WrColors.accent)

                Text(folderName)
                    .lineLimit(1)
                    .truncationMode(.middle)
                    .foregroundStyle(WrColors.textLight)
                    .help(folderName)

                Spacer()

                if session.privateSpaceFolder != nil {
                    Button("Use default") { choose(nil) }
                }

                Button("Choose…") { showsPicker = true }
                    .accessibilityIdentifier("folder.choose")
            }
            .padding(14)
            .background(WrColors.surface, in: RoundedRectangle(cornerRadius: 12))
            .overlay {
                RoundedRectangle(cornerRadius: 12)
                    .strokeBorder(WrColors.divider)
            }

            WrErrorText(errorMessage)
        }
    }

    private var folderName: String {
        session.privateSpaceFolder?.path(percentEncoded: false) ?? String(localized: "Application Support (default)")
    }

    private func choose(_ url: URL?) {
        do {
            try session.setPrivateSpaceFolder(url)
            errorMessage = nil
        } catch {
            errorMessage = String(localized: "This folder can't be used. Pick another one.")
        }
    }
}
