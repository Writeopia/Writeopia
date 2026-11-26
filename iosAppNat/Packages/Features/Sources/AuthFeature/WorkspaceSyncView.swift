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
                try await syncing.syncWorkspaceChanges()
                state = .done
            } catch is CancellationError {
                return
            } catch {
                state = .failed
            }
            self?.finish(state)
        }
    }

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
                    icon("arrow.triangle.2.circlepath", tint: WrTheme.Colors.accent, background: WrTheme.Colors.accentSubtle)

                    WrTitleBlock(
                        title: "Syncing your workspace",
                        subtitle: "Your documents are being brought to this device. You can start writing while the rest arrives.",
                        alignment: .center
                    )

                    WrStatusBanner("Bringing your documents…")
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

    private func icon(_ systemImage: String, tint: Color, background: Color) -> some View {
        Image(systemName: systemImage)
            .font(.system(size: 18, weight: .semibold))
            .foregroundStyle(tint)
            .frame(width: 48, height: 48)
            .background(background, in: RoundedRectangle(cornerRadius: WrTheme.Radius.lg))
    }
}
