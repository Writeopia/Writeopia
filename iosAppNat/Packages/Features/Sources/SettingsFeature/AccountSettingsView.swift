import Observation
import SwiftUI
import WrDesign
import WrModels
import WrNetwork
import WrSession

@Observable
final class AccountSettingsViewModel {
    private(set) var isWorking = false
    var errorMessage: String?
    var passwordChanged = false

    let session: AppSession

    init(session: AppSession) {
        self.session = session
    }

    func refreshUser() async {
        guard session.isOnline, let user = try? await session.authAPI.currentUser() else { return }
        session.updateUser(user)
    }

    func logout() async {
        isWorking = true
        defer { isWorking = false }
        await session.logout()
    }

    func deleteAccount() async {
        isWorking = true
        defer { isWorking = false }

        do {
            try await session.deleteAccount()
        } catch {
            errorMessage = error.userMessage
        }
    }

    func changePassword(_ password: String) async -> Bool {
        isWorking = true
        defer { isWorking = false }

        do {
            try await session.authAPI.changePassword(newPassword: password)
            passwordChanged = true
            return true
        } catch {
            errorMessage = error.userMessage
            return false
        }
    }
}

struct AccountSettingsView: View {
    @State private var viewModel: AccountSettingsViewModel
    @State private var showDeleteConfirmation = false
    @State private var showChangePassword = false
    @State private var showLogoutConfirmation = false

    init(session: AppSession) {
        _viewModel = State(initialValue: AccountSettingsViewModel(session: session))
    }

    private var session: AppSession { viewModel.session }

    var body: some View {
        Form {
            if session.isOnline {
                onlineSections
            } else {
                offlineSections
            }
        }
        .navigationTitle("Account")
        .disabled(viewModel.isWorking)
        .overlay {
            if viewModel.isWorking {
                ProgressView()
                    .controlSize(.large)
                    .padding(24)
                    .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 16))
            }
        }
        .task { await viewModel.refreshUser() }
        .sheet(isPresented: $showDeleteConfirmation) {
            DeleteAccountSheet(email: session.user?.email ?? "") {
                showDeleteConfirmation = false
                Task { await viewModel.deleteAccount() }
            }
        }
        .sheet(isPresented: $showChangePassword) {
            ChangePasswordSheet { password in
                Task {
                    if await viewModel.changePassword(password) {
                        showChangePassword = false
                    }
                }
            }
        }
        .alert(
            "Something went wrong",
            isPresented: Binding(get: { viewModel.errorMessage != nil }, set: { if !$0 { viewModel.errorMessage = nil } })
        ) {
            Button("OK", role: .cancel) {}
        } message: {
            Text(viewModel.errorMessage ?? "")
        }
        .alert("Password changed", isPresented: $viewModel.passwordChanged) {
            Button("OK", role: .cancel) {}
        }
    }

    @ViewBuilder
    private var onlineSections: some View {
        Section("Signed in as") {
            LabeledContent("Name", value: session.user?.name ?? "—")
            LabeledContent("Email", value: session.user?.email ?? "—")
            LabeledContent("Plan") {
                PlanBadge(user: session.user)
            }
            .accessibilityIdentifier("account.plan")
        }

        Section {
            LabeledContent("Workspace", value: session.workspace?.name ?? "—")
            Button {
                session.changeWorkspace()
            } label: {
                Label("Change workspace", systemImage: "arrow.left.arrow.right")
            }
            Button {
                session.switchSpace()
            } label: {
                Label("Switch space", systemImage: "rectangle.2.swap")
            }
            .accessibilityIdentifier("account.switchSpace")
        }

        Section {
            Button {
                showChangePassword = true
            } label: {
                Label("Change password", systemImage: "key")
            }
            Button {
                showLogoutConfirmation = true
            } label: {
                Label("Sign out", systemImage: "rectangle.portrait.and.arrow.right")
            }
            .confirmationDialog("Sign out of Writeopia?", isPresented: $showLogoutConfirmation, titleVisibility: .visible) {
                Button("Sign out", role: .destructive) {
                    Task { await viewModel.logout() }
                }
            }
        }

        Section {
            Button(role: .destructive) {
                showDeleteConfirmation = true
            } label: {
                Label("Delete account", systemImage: "trash")
            }
        } header: {
            Text("Danger zone")
        } footer: {
            Text("All your documents in the open space will be deleted.")
        }
    }

    @ViewBuilder
    private var offlineSections: some View {
        Section {
            VStack(alignment: .leading, spacing: 6) {
                Label("You are offline", systemImage: "lock.shield")
                    .font(.headline)
                Text("You're using the private space. Your notes are stored only on this device.")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            }
            .padding(.vertical, 4)
        }

        Section {
            Button {
                session.signIn()
            } label: {
                Label("Sign in", systemImage: "person.crop.circle.badge.checkmark")
            }
            .accessibilityIdentifier("account.signIn")

            Button {
                session.switchSpace()
            } label: {
                Label("Switch space", systemImage: "rectangle.2.swap")
            }
            .accessibilityIdentifier("account.switchSpace")
        }
    }
}

private struct DeleteAccountSheet: View {
    let email: String
    let onConfirm: () -> Void
    @State private var typedEmail = ""
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    Text("This can't be undone. Your account and all your notes in the open space will be deleted.")
                        .font(.callout.weight(.semibold))
                }
                Section {
                    TextField("Email", text: $typedEmail)
                        .textInputAutocapitalization(.never)
                        .keyboardType(.emailAddress)
                        .autocorrectionDisabled()
                } footer: {
                    Text("Type \(email) to confirm.")
                }
                Section {
                    Button("Delete my account", role: .destructive, action: onConfirm)
                        .disabled(typedEmail.trimmingCharacters(in: .whitespaces).caseInsensitiveCompare(email) != .orderedSame)
                }
            }
            .navigationTitle("Are you sure?")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
            }
        }
        .presentationDetents([.medium, .large])
    }
}

private struct ChangePasswordSheet: View {
    let onSave: (String) -> Void
    @State private var password = ""
    @State private var repeatPassword = ""
    @Environment(\.dismiss) private var dismiss

    private var isValid: Bool {
        password.count >= 8 && password == repeatPassword
    }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    SecureField("New password", text: $password)
                        .textContentType(.newPassword)
                    SecureField("Repeat password", text: $repeatPassword)
                        .textContentType(.newPassword)
                } footer: {
                    if !repeatPassword.isEmpty && password != repeatPassword {
                        Text("The passwords don't match.").foregroundStyle(.red)
                    } else {
                        Text("At least 8 characters.")
                    }
                }
            }
            .navigationTitle("Change password")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") { onSave(password) }
                        .disabled(!isValid)
                }
            }
        }
        .presentationDetents([.medium])
    }
}

/// Premium or free, like the tier shown in the Compose account screen.
struct PlanBadge: View {
    let user: User?

    var body: some View {
        if let plan = user?.planName {
            let isPremium = user?.isPremium == true
            Label(plan, systemImage: isPremium ? "crown.fill" : "person")
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(isPremium ? WrColors.accent : .secondary)
                .padding(.horizontal, 10)
                .padding(.vertical, 4)
                .background(
                    (isPremium ? WrColors.accent.opacity(0.15) : Color.secondary.opacity(0.12)),
                    in: Capsule()
                )
        } else {
            Text("Unknown")
                .foregroundStyle(.secondary)
        }
    }
}
