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
            List {
                Section {
                    ForEach(viewModel.workspaces) { workspace in
                        Button {
                            viewModel.select(workspace)
                        } label: {
                            WorkspaceRow(workspace: workspace, isSelected: workspace.id == viewModel.selectedWorkspaceId)
                        }
                        .accessibilityIdentifier("workspace.\(workspace.name)")
                    }
                } header: {
                    if !viewModel.workspaces.isEmpty {
                        Text("Your teams")
                    }
                }
            }
            .overlay {
                WrStateOverlay(
                    isLoading: viewModel.isLoading,
                    isEmpty: viewModel.workspaces.isEmpty,
                    errorMessage: viewModel.errorMessage,
                    emptyTitle: "You're not in any team yet",
                    emptyImage: "person.3",
                    retry: { Task { await viewModel.load() } }
                )
            }
            .navigationTitle("Choose a workspace")
            #if os(macOS)
            .safeAreaInset(edge: .top) {
                WrBackButton("Sign out") {
                    Task { await viewModel.signOut() }
                }
                .padding(.horizontal, 20)
                .padding(.vertical, 12)
            }
            #endif
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Sign out") {
                        Task { await viewModel.signOut() }
                    }
                }
                ToolbarItem(placement: .primaryAction) {
                    Button {
                        newWorkspaceName = ""
                        viewModel.resetCreateError()
                        showCreate = true
                    } label: {
                        Label("New workspace", systemImage: "plus")
                    }
                }
            }
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
}

struct WorkspaceRow: View {
    let workspace: Workspace
    let isSelected: Bool

    var body: some View {
        HStack(spacing: 14) {
            Text(String(workspace.name.prefix(1)).uppercased())
                .font(.headline)
                .foregroundStyle(.white)
                .frame(width: 40, height: 40)
                .background(WrColors.accent.gradient, in: RoundedRectangle(cornerRadius: 10))

            VStack(alignment: .leading, spacing: 2) {
                Text(workspace.name)
                    .font(.headline)
                    .foregroundStyle(WrColors.textLight)
                Text("\(workspace.role.capitalized) · \(workspace.documentCount) documents")
                    .font(.subheadline)
                    .foregroundStyle(WrColors.textLighter)
            }

            Spacer()

            if isSelected {
                Image(systemName: "checkmark.circle.fill")
                    .foregroundStyle(WrColors.accent)
            }
        }
        .padding(.vertical, 4)
        .contentShape(Rectangle())
    }
}
