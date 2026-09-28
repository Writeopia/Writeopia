import Observation
import SwiftUI
import WrDesign
import WrModels
import WrNetwork
import WrSession

@Observable
final class TeamsViewModel {
    private(set) var workspaces: [Workspace] = []
    private(set) var isLoading = false
    private(set) var errorMessage: String?

    let session: AppSession

    init(session: AppSession) {
        self.session = session
    }

    func load() async {
        guard session.isOnline else { return }

        isLoading = true
        defer { isLoading = false }

        do {
            workspaces = try await session.workspacesAPI.userWorkspaces()
                .sorted { $0.name.localizedCaseInsensitiveCompare($1.name) == .orderedAscending }
            errorMessage = nil
        } catch {
            errorMessage = error.userMessage
        }
    }
}

struct TeamsSettingsView: View {
    @State private var viewModel: TeamsViewModel

    init(session: AppSession) {
        _viewModel = State(initialValue: TeamsViewModel(session: session))
    }

    var body: some View {
        Group {
            if viewModel.session.isOnline {
                List(viewModel.workspaces) { workspace in
                    NavigationLink {
                        TeamDetailView(workspace: workspace, session: viewModel.session)
                    } label: {
                        HStack {
                            VStack(alignment: .leading, spacing: 2) {
                                Text(workspace.name)
                                Text(workspace.isAdmin ? "Admin" : "Member")
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                            }
                            Spacer()
                            if workspace.id == viewModel.session.workspace?.id {
                                Text("Current")
                                    .font(.caption.weight(.semibold))
                                    .padding(.horizontal, 8)
                                    .padding(.vertical, 3)
                                    .background(WrColors.accent.opacity(0.15), in: Capsule())
                                    .foregroundStyle(WrColors.accent)
                            }
                        }
                    }
                }
                .overlay {
                    WrStateOverlay(
                        isLoading: viewModel.isLoading,
                        isEmpty: viewModel.workspaces.isEmpty,
                        errorMessage: viewModel.errorMessage,
                        emptyTitle: "No teams",
                        emptyImage: "person.3",
                        retry: { Task { await viewModel.load() } }
                    )
                }
                .task { await viewModel.load() }
                .refreshable { await viewModel.load() }
            } else {
                OfflineNotice(
                    title: "Teams need an account",
                    message: "Sign in to the open space to create teams and share documents with other people."
                )
            }
        }
        .navigationTitle("Teams")
    }
}

@Observable
final class TeamDetailViewModel {
    private(set) var workspace: Workspace
    private(set) var members: [WorkspaceUser] = []
    private(set) var isLoading = false
    private(set) var errorMessage: String?
    var actionError: String?
    var newMemberEmail = ""
    var newMemberRole: WorkspaceRole = .user
    private(set) var isAdding = false

    private let session: AppSession

    init(workspace: Workspace, session: AppSession) {
        self.workspace = workspace
        self.session = session
    }

    var currentUserId: String? { session.user?.id }

    var canAddMember: Bool {
        newMemberEmail.contains("@") && newMemberEmail.contains(".") && !isAdding
    }

    func load() async {
        guard workspace.isAdmin else { return }

        isLoading = true
        defer { isLoading = false }

        do {
            members = try await session.workspacesAPI.users(workspaceId: workspace.id)
                .sorted { $0.name.localizedCaseInsensitiveCompare($1.name) == .orderedAscending }
            errorMessage = nil
        } catch {
            errorMessage = error.userMessage
        }
    }

    func addMember() async {
        guard canAddMember else { return }

        isAdding = true
        defer { isAdding = false }

        do {
            try await session.workspacesAPI.addUser(email: newMemberEmail, workspaceId: workspace.id, role: newMemberRole)
            newMemberEmail = ""
            newMemberRole = .user
            await load()
        } catch {
            actionError = error.userMessage
        }
    }

    func changeRole(of member: WorkspaceUser, to role: WorkspaceRole) async {
        await perform {
            try await self.session.workspacesAPI.changeRole(workspaceId: self.workspace.id, userId: member.id, role: role)
        }
    }

    func remove(_ member: WorkspaceUser) async {
        await perform {
            try await self.session.workspacesAPI.removeUser(workspaceId: self.workspace.id, userId: member.id)
        }
    }

    func rename(to name: String) async {
        let trimmed = name.trimmingCharacters(in: .whitespaces)
        guard !trimmed.isEmpty, trimmed != workspace.name else { return }

        do {
            try await session.workspacesAPI.rename(workspaceId: workspace.id, newName: trimmed)
            workspace = Workspace(
                id: workspace.id,
                userId: workspace.userId,
                name: trimmed,
                role: workspace.role,
                documentCount: workspace.documentCount
            )
            if session.workspace?.id == workspace.id {
                session.select(workspace: workspace)
            }
        } catch {
            actionError = error.userMessage
        }
    }

    private func perform(_ operation: () async throws -> Void) async {
        do {
            try await operation()
            await load()
        } catch {
            actionError = error.userMessage
        }
    }
}

struct TeamDetailView: View {
    @State private var viewModel: TeamDetailViewModel
    @State private var showRename = false
    @State private var newName = ""

    init(workspace: Workspace, session: AppSession) {
        _viewModel = State(initialValue: TeamDetailViewModel(workspace: workspace, session: session))
    }

    var body: some View {
        Form {
            Section {
                LabeledContent("Name", value: viewModel.workspace.name)
                LabeledContent("Your role", value: viewModel.workspace.isAdmin ? String(localized: "Admin") : String(localized: "Member"))
                LabeledContent("Documents", value: "\(viewModel.workspace.documentCount)")
            }

            if viewModel.workspace.isAdmin {
                Section {
                    TextField("Email", text: $viewModel.newMemberEmail)
                        .textContentType(.emailAddress)
                        .keyboardType(.emailAddress)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()

                    Picker("Role", selection: $viewModel.newMemberRole) {
                        ForEach(WorkspaceRole.allCases) { role in
                            Text(role.title).tag(role)
                        }
                    }

                    Button {
                        Task { await viewModel.addMember() }
                    } label: {
                        if viewModel.isAdding {
                            ProgressView()
                        } else {
                            Label("Add to team", systemImage: "person.badge.plus")
                        }
                    }
                    .disabled(!viewModel.canAddMember)
                } header: {
                    Text("Invite someone")
                } footer: {
                    Text("They need to have a Writeopia account.")
                }

                Section("Members") {
                    if viewModel.isLoading && viewModel.members.isEmpty {
                        ProgressView()
                    } else if let error = viewModel.errorMessage {
                        Text(error).foregroundStyle(.red)
                    }

                    ForEach(viewModel.members) { member in
                        MemberRow(member: member, isCurrentUser: member.id == viewModel.currentUserId)
                            .swipeActions {
                                if member.id != viewModel.currentUserId {
                                    Button("Remove", role: .destructive) {
                                        Task { await viewModel.remove(member) }
                                    }
                                }
                            }
                            .contextMenu {
                                ForEach(WorkspaceRole.allCases) { role in
                                    Button {
                                        Task { await viewModel.changeRole(of: member, to: role) }
                                    } label: {
                                        Label(role == .admin ? "Make admin" : "Make member", systemImage: role == .admin ? "crown" : "person")
                                    }
                                    .disabled(member.role.uppercased() == role.rawValue)
                                }
                            }
                    }
                }
            } else {
                Section {
                    Text("Only admins can see and manage the members of this team.")
                        .foregroundStyle(.secondary)
                }
            }
        }
        .navigationTitle(viewModel.workspace.name)
        .toolbar {
            if viewModel.workspace.isAdmin {
                Button("Rename") {
                    newName = viewModel.workspace.name
                    showRename = true
                }
            }
        }
        .task { await viewModel.load() }
        .refreshable { await viewModel.load() }
        .alert("Rename team", isPresented: $showRename) {
            TextField("Name", text: $newName)
            Button("Cancel", role: .cancel) {}
            Button("Save") { Task { await viewModel.rename(to: newName) } }
        }
        .alert(
            "Something went wrong",
            isPresented: Binding(get: { viewModel.actionError != nil }, set: { if !$0 { viewModel.actionError = nil } })
        ) {
            Button("OK", role: .cancel) {}
        } message: {
            Text(viewModel.actionError ?? "")
        }
    }
}

private struct MemberRow: View {
    let member: WorkspaceUser
    let isCurrentUser: Bool

    var body: some View {
        HStack {
            VStack(alignment: .leading, spacing: 2) {
                Text(isCurrentUser ? String(localized: "\(member.name) (you)") : member.name)
                Text(member.email)
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            Spacer()
            Text(member.isAdmin ? "Admin" : "Member")
                .font(.caption)
                .foregroundStyle(.secondary)
        }
    }
}
