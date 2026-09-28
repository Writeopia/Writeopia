import Observation
import SwiftUI
import WrData
import WrDesign
import WrNetwork
import WrSession

@Observable
final class RegisterViewModel {
    var name = ""
    var username = ""
    var workspaceName = ""
    var email = ""
    var password = ""
    private(set) var isLoading = false
    private(set) var errorMessage: String?

    private let session: AppSession

    init(session: AppSession) {
        self.session = session
    }

    var passwordValidation: PasswordValidation { PasswordValidator.validate(password) }

    var usernameHint: String? {
        guard !username.isEmpty, !FieldValidator.isValidUsername(username) else { return nil }
        return String(localized: "3 to 30 letters, numbers, - or _")
    }

    var workspaceHint: String? {
        guard !workspaceName.isEmpty, !FieldValidator.isValidWorkspaceName(workspaceName) else { return nil }
        return String(localized: "Between 3 and 30 characters")
    }

    var canRegister: Bool {
        !name.trimmingCharacters(in: .whitespaces).isEmpty &&
            FieldValidator.isValidUsername(username) &&
            FieldValidator.isValidWorkspaceName(workspaceName) &&
            FieldValidator.isValidEmail(email) &&
            passwordValidation.isValid &&
            !isLoading
    }

    func register() async {
        guard canRegister else { return }

        isLoading = true
        errorMessage = nil
        defer { isLoading = false }

        do {
            let response = try await session.authAPI.register(
                name: name.trimmingCharacters(in: .whitespaces),
                email: email,
                username: username,
                workspaceName: workspaceName.trimmingCharacters(in: .whitespaces),
                password: password
            )
            session.needsEmailConfirmation(email: response.writeopiaUser.email)
        } catch let error as AuthError {
            errorMessage = error.userMessage
        } catch {
            errorMessage = error.userMessage
        }
    }
}

struct RegisterView: View {
    @State private var viewModel: RegisterViewModel

    init(session: AppSession) {
        _viewModel = State(initialValue: RegisterViewModel(session: session))
    }

    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                WrScreenHeader(
                    title: "Create your account",
                    subtitle: "Your account comes with a team workspace you can invite people to."
                )
                .padding(.bottom, 8)

                WrTextField("Name", text: $viewModel.name, systemImage: "person", kind: .name)
                    .accessibilityIdentifier("register.name")

                VStack(spacing: 4) {
                    WrTextField("Username", text: $viewModel.username, systemImage: "at", kind: .email)
                        .accessibilityIdentifier("register.username")
                    hint(viewModel.usernameHint)
                }

                VStack(spacing: 4) {
                    WrTextField("Team workspace name", text: $viewModel.workspaceName, systemImage: "person.3")
                        .accessibilityIdentifier("register.workspace")
                    hint(viewModel.workspaceHint)
                }

                WrTextField("Email", text: $viewModel.email, systemImage: "envelope", kind: .email)
                    .accessibilityIdentifier("register.email")

                VStack(spacing: 8) {
                    WrTextField("Password", text: $viewModel.password, systemImage: "lock", kind: .newPassword)
                        .accessibilityIdentifier("register.password")
                    PasswordStrengthView(validation: viewModel.passwordValidation)
                }

                WrErrorText(viewModel.errorMessage)

                WrPrimaryButton("Create account", isLoading: viewModel.isLoading) {
                    Task { await viewModel.register() }
                }
                .disabled(!viewModel.canRegister)
                .accessibilityIdentifier("register.submit")
            }
            .padding(24)
            .frame(maxWidth: 520)
            .frame(maxWidth: .infinity)
        }
        .scrollDismissesKeyboard(.interactively)
        .navigationTitle("Register")
        .navigationBarTitleDisplayMode(.inline)
        .animation(.default, value: viewModel.errorMessage)
    }

    @ViewBuilder
    private func hint(_ text: String?) -> some View {
        if let text {
            Text(text)
                .font(.caption)
                .foregroundStyle(.orange)
                .frame(maxWidth: .infinity, alignment: .leading)
        }
    }
}

struct PasswordStrengthView: View {
    let validation: PasswordValidation

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack(spacing: 4) {
                ForEach(1...3, id: \.self) { index in
                    Capsule()
                        .fill(index <= validation.strength.rawValue ? color : WrColors.divider)
                        .frame(height: 4)
                }
            }

            requirement("At least \(PasswordValidator.minLength) characters", met: validation.hasMinLength)
            requirement("One special character", met: validation.hasSpecialChar)
        }
        .animation(.easeInOut, value: validation)
    }

    private var color: Color {
        switch validation.strength {
        case .none, .weak: .red
        case .medium: .orange
        case .strong: .green
        }
    }

    private func requirement(_ text: LocalizedStringKey, met: Bool) -> some View {
        Label(text, systemImage: met ? "checkmark.circle.fill" : "circle")
            .font(.caption)
            .foregroundStyle(met ? .green : WrColors.textLighter)
    }
}
