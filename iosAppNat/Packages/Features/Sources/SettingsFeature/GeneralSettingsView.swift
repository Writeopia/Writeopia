import Observation
import SetupFeature
import SwiftUI
import WrData
import WrDesign
import WrModels
import WrNetwork
import WrSession

/// The storage of the user on the backend, for the "Storage" section. Like `AiSettingsViewModel`.
@Observable
final class StorageUsageViewModel {
    private(set) var usage: StorageUsage?
    private(set) var isLoading = false
    private(set) var errorMessage: String?

    private let session: AppSession

    init(session: AppSession) {
        self.session = session
    }

    func load() async {
        guard let api = session.mediaAPI else { return }

        isLoading = true
        defer { isLoading = false }

        do {
            usage = try await api.usage()
            errorMessage = nil
        } catch {
            usage = nil
            errorMessage = error.userMessage
        }
    }
}

struct GeneralSettingsView: View {
    @Environment(AppSession.self) private var session
    @State private var storage: StorageUsageViewModel?

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

            if session.isOnline {
                storageSection
            }

            Section("About") {
                LabeledContent("Version", value: Bundle.main.appVersion)
                LabeledContent("Space", value: session.spaceType == .online ? String(localized: "Open space") : String(localized: "Private space"))
            }
        }
        .navigationTitle("General")
        .task(id: session.isOnline) {
            guard session.isOnline else { return }
            let viewModel = storage ?? StorageUsageViewModel(session: session)
            storage = viewModel
            await viewModel.load()
        }
        .refreshable { await storage?.load() }
    }

    /// How much of the storage the pictures of the user take, with the quota.
    @ViewBuilder
    private var storageSection: some View {
        Section {
            if let usage = storage?.usage {
                VStack(alignment: .leading, spacing: 8) {
                    Text("\(usage.usedBytes.bytesFormatted) / \(usage.quotaBytes.bytesFormatted)")
                        .font(.title2.bold())
                        .monospacedDigit()
                        .accessibilityIdentifier("settings.storage.usage")
                    ProgressView(value: usage.progress)
                        .tint(usage.progress > 0.9 ? .red : WrColors.accent)
                    Text(usage.progress, format: .percent.precision(.fractionLength(0)))
                        .font(.caption)
                        .foregroundStyle(.secondary)
                    if usage.isFull {
                        Text("Your storage is full. Delete some pictures to make room.")
                            .font(.caption)
                            .foregroundStyle(.red)
                    }
                }
                .padding(.vertical, 4)
            } else if storage?.isLoading == true {
                HStack {
                    ProgressView()
                    Text("Loading storage…").foregroundStyle(.secondary)
                }
            } else if let error = storage?.errorMessage {
                Text(error).foregroundStyle(.red)
            }
        } header: {
            Text("Storage")
        } footer: {
            Text("Pictures you upload and the ones the AI draws for you count here. Deleting a picture from a document gives its space back.")
        }
    }
}

extension Int64 {
    /// Bytes for people: "512 KB", "1.5 MB", "20 GB".
    var bytesFormatted: String {
        ByteCountFormatter.string(fromByteCount: self, countStyle: .binary)
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
