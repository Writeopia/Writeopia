import Observation
import SwiftUI
import WrDesign
import WrModels
import WrNetwork
import WrSession

@Observable
final class ChooseWorkspaceViewModel {
    private(set) var workspaces: [Workspace] = []
    private(set) var isLoading = false
    private(set) var errorMessage: String?
    private(set) var isCreating = false
    private(set) var createError: String?

    private let session: AppSession

    init(session: AppSession) {
        self.session = session
    }

    var selectedWorkspaceId: String? { session.workspace?.id }

    var userEmail: String? { session.user?.email }

    func load() async {
        isLoading = true
        defer { isLoading = false }

        do {
            workspaces = try await session.workspacesAPI.userWorkspaces()
                .sorted { $0.name.localizedCaseInsensitiveCompare($1.name) == .orderedAscending }
            errorMessage = nil

            if session.user == nil, let user = try? await session.authAPI.currentUser() {
                session.updateUser(user)
            }
        } catch {
            errorMessage = error.userMessage
        }
    }

    func select(_ workspace: Workspace) {
        session.select(workspace: workspace)
    }

    /// The API doesn't return the new workspace, so the list is reloaded after creating it.
    func create(name: String) async -> Bool {
        let trimmed = name.trimmingCharacters(in: .whitespaces)
        guard FieldValidator.isValidWorkspaceName(trimmed) else {
            createError = String(localized: "The name must have between 3 and 30 characters.")
            return false
        }

        isCreating = true
        createError = nil
        defer { isCreating = false }

        do {
            try await session.workspacesAPI.createWorkspace(name: trimmed)
            await load()
            return true
        } catch {
            createError = error.userMessage
            return false
        }
    }

    func resetCreateError() {
        createError = nil
    }

    func signOut() async {
        await session.logout()
    }
}

public struct ChooseWorkspaceView: View {
    @State private var viewModel: ChooseWorkspaceViewModel
    @State private var showCreate = false
    @State private var newWorkspaceName = ""

    public init(session: AppSession) {
        _viewModel = State(initialValue: ChooseWorkspaceViewModel(session: session))
    }

    public var body: some View {
        NavigationStack {
            WrOnboardingScreen(width: 520, back: signOutBack) {
                VStack(alignment: .leading, spacing: WrTheme.Space.s300) {
                    WrTitleBlock(eyebrow: "Open space", title: "Choose a workspace", subtitle: subtitle)

                    workspaces

                    WrButton("New workspace", systemImage: "plus", kind: .secondary, size: .large, fillsWidth: true, action: startCreating)
                        .accessibilityIdentifier("workspace.new")
                }
            }
            #if os(iOS)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Sign out") {
                        Task { await viewModel.signOut() }
                    }
                }
            }
            #endif
            .task { await viewModel.load() }
            .refreshable { await viewModel.load() }
            .alert("New workspace", isPresented: $showCreate) {
                TextField("Name", text: $newWorkspaceName)
                Button("Cancel", role: .cancel) {}
                Button("Create") {
                    Task { _ = await viewModel.create(name: newWorkspaceName) }
                }
            } message: {
                Text("Create a team workspace and invite people to it from Settings.")
            }
            .alert(
                "Could not create the workspace",
                isPresented: Binding(
                    get: { viewModel.createError != nil },
                    set: { if !$0 { viewModel.resetCreateError() } }
                )
            ) {
                Button("OK", role: .cancel) {}
            } message: {
                Text(viewModel.createError ?? "")
            }
        }
    }

    private var subtitle: LocalizedStringKey {
        if let email = viewModel.userEmail {
            "Signed in as \(email)"
        } else {
            "Pick the workspace to open on this device."
        }
    }

    /// The way out at the top of the window on the Mac; iOS has it in the toolbar.
    private var signOutBack: (title: LocalizedStringKey, action: () -> Void)? {
        #if os(macOS)
        return ("Sign out", { Task { await viewModel.signOut() } })
        #else
        return nil
        #endif
    }

    @ViewBuilder
    private var workspaces: some View {
        if viewModel.isLoading && viewModel.workspaces.isEmpty {
            WrStatusBanner("Loading your workspaces…")
        } else if let error = viewModel.errorMessage, viewModel.workspaces.isEmpty {
            VStack(alignment: .leading, spacing: WrTheme.Space.s150) {
                WrErrorText(error)
                WrButton("Try again", kind: .secondary) {
                    Task { await viewModel.load() }
                }
            }
        } else if viewModel.workspaces.isEmpty {
            WrEmptyState(
                systemImage: "person.3",
                title: "You're not in any team yet",
                description: "Create a workspace, or ask a teammate to invite you."
            )
        } else {
            VStack(spacing: 0) {
                ForEach(Array(viewModel.workspaces.enumerated()), id: \.element.id) { index, workspace in
                    if index > 0 { WrDivider() }
                    WorkspaceRow(workspace: workspace, isSelected: workspace.id == viewModel.selectedWorkspaceId) {
                        viewModel.select(workspace)
                    }
                    .accessibilityIdentifier("workspace.\(workspace.name)")
                }
            }
            .clipShape(RoundedRectangle(cornerRadius: WrTheme.Radius.lg))
            .overlay {
                RoundedRectangle(cornerRadius: WrTheme.Radius.lg)
                    .strokeBorder(WrTheme.Colors.borderDefault)
            }
        }
    }

    private func startCreating() {
        newWorkspaceName = ""
        viewModel.resetCreateError()
        showCreate = true
    }
}

/// One workspace of the list, the v4 `WorkspaceRow`. The one used last is marked.
struct WorkspaceRow: View {
    let workspace: Workspace
    let isSelected: Bool
    let action: () -> Void

    var body: some View {
        WrWorkspaceRow(
            workspace.name,
            meta: Text("\(workspace.role.capitalized) · \(workspace.documentCount) documents"),
            badge: isSelected ? WrPill("Last used", kind: .neutral) : nil,
            action: action
        )
    }
}
