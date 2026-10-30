import Observation
import SwiftUI
import UniformTypeIdentifiers
import WrData
import WrDesign
import WrModels
import WrSession

/// Saves the workspace to its folder and syncs it, reporting what happened for the page.
@Observable
final class WorkspaceFolderViewModel {
    enum Activity: Equatable {
        case idle
        case saving
        case syncing
    }

    private(set) var activity: Activity = .idle
    /// What the last save or sync did, or why it failed.
    private(set) var statusMessage: String?
    private(set) var errorMessage: String?

    let session: AppSession

    init(session: AppSession) {
        self.session = session
    }

    var isBusy: Bool { activity != .idle }

    func save() async {
        await run(.saving) { sync, folder in
            let result = try await sync.save(to: folder)
            return String(localized: "Saved \(result.savedDocuments) documents and \(result.savedFolders) folders at \(Self.time()).")
        }
    }

    func sync() async {
        await run(.syncing) { sync, folder in
            let result = try await sync.sync(with: folder)
            var message = String(
                localized: "Loaded \(result.loadedDocuments) documents and \(result.loadedFolders) folders from the folder, saved \(result.savedDocuments) documents and \(result.savedFolders) folders at \(Self.time())."
            )
            if !result.unreadableFiles.isEmpty {
                message += " " + String(localized: "Skipped \(result.unreadableFiles.count) files that aren't documents.")
            }
            return message
        }
    }

    private func run(_ activity: Activity, _ work: (WorkspaceFolderSync, URL) async throws -> String) async {
        guard !isBusy, let folder = session.workspaceFolder, let sync = session.workspaceFolderSync else { return }
        self.activity = activity
        errorMessage = nil
        defer { self.activity = .idle }
        do {
            statusMessage = try await work(sync, folder)
        } catch {
            errorMessage = String(localized: "The folder couldn't be written or read: \(error.localizedDescription)")
        }
    }

    private static func time() -> String {
        Date.now.formatted(date: .omitted, time: .shortened)
    }
}

/// The "Workspace" page of Settings: the folder of the computer the workspace is kept in as
/// JSON files, with a button to save the workspace there and one to sync with it, like the
/// local folder of the Compose desktop app.
struct WorkspaceSettingsView: View {
    @Environment(AppSession.self) private var session
    @State private var viewModel: WorkspaceFolderViewModel
    @State private var showsPicker = false
    @State private var folderError: String?

    init(session: AppSession) {
        _viewModel = State(initialValue: WorkspaceFolderViewModel(session: session))
    }

    var body: some View {
        WrForm {
            Section("Workspace") {
                LabeledContent("Name", value: session.workspace?.name ?? String(localized: "Private space"))
                LabeledContent("Space", value: session.spaceType == .online ? String(localized: "Open space") : String(localized: "Private space"))
            }

            Section {
                folderRow
                    .listRowBackground(Color.clear)
                    .listRowInsets(EdgeInsets())
            } header: {
                Text("Sync folder")
            } footer: {
                Text("Writeopia keeps a copy of this workspace in the folder, one JSON file per document and folder, the same files the desktop app uses. Nothing is written until you save or sync.")
            }

            Section {
                actionsRow
                WrErrorText(viewModel.errorMessage)
                if let status = viewModel.statusMessage {
                    Text(status)
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                        .accessibilityIdentifier("workspace.status")
                }
            } header: {
                Text("Files")
            } footer: {
                Text("Save writes every document and folder of the workspace to the folder. Sync first loads what is newer in the folder, then saves.")
            }
        }
        .navigationTitle("Workspace")
        .animation(.default, value: viewModel.errorMessage)
        .animation(.default, value: folderError)
        .fileImporter(isPresented: $showsPicker, allowedContentTypes: [.folder]) { result in
            switch result {
            case .success(let url):
                choose(url)
            case .failure(let error):
                folderError = error.localizedDescription
            }
        }
    }

    private var folderRow: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(spacing: 12) {
                Image(systemName: "folder")
                    .foregroundStyle(WrColors.accent)

                Text(folderName)
                    .lineLimit(1)
                    .truncationMode(.middle)
                    .foregroundStyle(session.workspaceFolder == nil ? .secondary : WrColors.textLight)
                    .help(folderName)

                Spacer()

                if session.workspaceFolder != nil {
                    Button("Remove") { choose(nil) }
                        .accessibilityIdentifier("workspace.folder.remove")
                }

                Button("Choose…") { showsPicker = true }
                    .accessibilityIdentifier("workspace.folder.choose")
            }
            .padding(14)
            .background(WrColors.surface, in: RoundedRectangle(cornerRadius: 12))
            .overlay {
                RoundedRectangle(cornerRadius: 12)
                    .strokeBorder(WrColors.divider)
            }

            WrErrorText(folderError)
        }
    }

    private var actionsRow: some View {
        HStack(spacing: 12) {
            Button {
                Task { await viewModel.save() }
            } label: {
                Label("Save to folder", systemImage: "square.and.arrow.down")
            }
            .accessibilityIdentifier("workspace.save")

            Button {
                Task { await viewModel.sync() }
            } label: {
                Label("Sync", systemImage: "arrow.triangle.2.circlepath")
            }
            .buttonStyle(.borderedProminent)
            .tint(WrColors.accent)
            .accessibilityIdentifier("workspace.sync")

            if viewModel.isBusy {
                ProgressView()
                    .controlSize(.small)
                Text(viewModel.activity == .saving ? "Saving…" : "Syncing…")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }
        }
        .disabled(session.workspaceFolder == nil || viewModel.isBusy)
        .padding(.vertical, 4)
    }

    private var folderName: String {
        session.workspaceFolder?.path(percentEncoded: false) ?? String(localized: "No folder chosen")
    }

    private func choose(_ url: URL?) {
        do {
            try session.setWorkspaceFolder(url)
            folderError = nil
        } catch {
            folderError = String(localized: "This folder can't be used. Pick another one.")
        }
    }
}
