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
        VStack(spacing: 24) {
            switch viewModel.state {
            case .syncing, .done:
                ProgressView()
                    .controlSize(.large)

                VStack(spacing: 8) {
                    Text("Syncing your workspace")
                        .font(.title2.bold())
                        .foregroundStyle(WrColors.textLight)
                    Text("Your documents are being brought to this device. This may take a moment.")
                        .font(.callout)
                        .foregroundStyle(WrColors.textLighter)
                }
                .multilineTextAlignment(.center)

                WrPrimaryButton("Continue in background", action: viewModel.continueToDocuments)
                    .accessibilityIdentifier("workspaceSync.continue")
            case .failed:
                Text("The sync didn't finish. It will be tried again when you open your documents.")
                    .font(.callout)
                    .foregroundStyle(WrColors.textLight)
                    .multilineTextAlignment(.center)

                WrPrimaryButton("Continue", action: viewModel.continueToDocuments)
                    .accessibilityIdentifier("workspaceSync.continue")
            }
        }
        .padding(24)
        .frame(maxWidth: 400)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(WrColors.background)
        .task { viewModel.start() }
    }
}
