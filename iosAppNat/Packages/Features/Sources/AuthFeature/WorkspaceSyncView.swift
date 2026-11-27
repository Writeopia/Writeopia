import Observation
import SwiftUI
import WrData
import WrDesign
import WrSession

/// Brings the workspace just chosen to this device, whole, before the documents are shown.
/// The sync is a task of its own, not of the view: the person may go on to the documents
/// while it runs, and it goes on in the background.
@Observable
final class WorkspaceSyncViewModel {
    enum State: Equatable {
        case syncing
        case done
        case failed
    }

    private(set) var state: State = .syncing
    /// Documents merged so far and how many came, once the backend has answered.
    private(set) var progress: (done: Int, total: Int)?
    private let session: AppSession
    private var task: Task<Void, Never>?

    init(session: AppSession) {
        self.session = session
    }

    /// Starts the sync, once; a workspace with nothing to sync (the private space) is done at
    /// once. When the sync ends while the screen is still shown, the documents are opened.
    func start() {
        guard task == nil else { return }

        guard let syncing = session.documents as? DocumentSyncing else {
            finish(.done)
            return
        }

        task = Task { [weak self] in
            let state: State
            do {
                try await syncing.syncWorkspaceChanges { done, total in
                    self?.progress = (done, total)
                }
                state = .done
            } catch is CancellationError {
                return
            } catch {
                state = .failed
            }
            self?.finish(state)
        }
    }

    /// The workspace being synced, for the subtitle.
    var workspaceName: String? { session.workspace?.name }

    /// Goes on to the documents; a sync still running keeps running.
    func continueToDocuments() {
        session.workspaceSynced()
    }

    private func finish(_ state: State) {
        self.state = state
        if state == .done {
            session.workspaceSynced()
        }
    }
}

public struct WorkspaceSyncView: View {
    @State private var viewModel: WorkspaceSyncViewModel

    public init(session: AppSession) {
        _viewModel = State(initialValue: WorkspaceSyncViewModel(session: session))
    }

    public var body: some View {
        WrOnboardingScreen(width: 440) {
            VStack(spacing: WrTheme.Space.s300) {
                switch viewModel.state {
                case .syncing, .done:
                    VStack(spacing: WrTheme.Space.s200 + WrTheme.Space.s50) {
                        WrBrandMark()
                        WrTitleBlock(title: "Syncing your workspace", subtitle: subtitle, alignment: .center)
                    }

                    VStack(spacing: WrTheme.Space.s100) {
                        WrProgressBar(value: fraction)
                        HStack {
                            if let progress = viewModel.progress {
                                Text("\(progress.done) of \(progress.total) documents")
                                    .monospacedDigit()
                            } else {
                                Text("Bringing your documents…")
                            }
                            Spacer(minLength: 0)
                        }
                        .wrTextStyle(.bodySmall)
                        .foregroundStyle(WrTheme.Colors.textSecondary)
                    }
                    .accessibilityElement(children: .combine)
                    .accessibilityIdentifier("workspaceSync.progress")

                    WrPrimaryButton("Continue in background", action: viewModel.continueToDocuments)
                        .accessibilityIdentifier("workspaceSync.continue")
                case .failed:
                    icon("exclamationmark.triangle", tint: WrTheme.Colors.warning, background: WrTheme.Colors.warningSubtle)

                    WrTitleBlock(
                        title: "The sync didn't finish",
                        subtitle: "It will be tried again when you open your documents.",
                        alignment: .center
                    )

                    WrPrimaryButton("Continue", action: viewModel.continueToDocuments)
                        .accessibilityIdentifier("workspaceSync.continue")
                }
            }
        }
        .task { viewModel.start() }
    }

    private var fraction: Double? {
        guard let progress = viewModel.progress, progress.total > 0 else { return nil }
        return Double(progress.done) / Double(progress.total)
    }

    /// Names the workspace, like the design; the computer on the Mac, the device elsewhere.
    private var subtitle: LocalizedStringKey {
        guard let name = viewModel.workspaceName else {
            return "Your documents are being brought to this device. You can start writing while the rest arrives."
        }
        #if os(macOS)
        return "Bringing \(name) to this computer. You can start writing while the rest arrives."
        #else
        return "Bringing \(name) to this device. You can start writing while the rest arrives."
        #endif
    }

    private func icon(_ systemImage: String, tint: Color, background: Color) -> some View {
        Image(systemName: systemImage)
            .font(.system(size: 18, weight: .semibold))
            .foregroundStyle(tint)
            .frame(width: 48, height: 48)
            .background(background, in: RoundedRectangle(cornerRadius: WrTheme.Radius.lg))
    }
}
