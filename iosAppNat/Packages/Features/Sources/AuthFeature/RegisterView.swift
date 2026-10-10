import Observation
import SwiftUI
import WrData
import WrDesign
import WrNetwork
import WrSession

/// Sign-up verifies the email first. The account details are only asked for, and the account
/// only created, once the email is verified.
enum RegisterStep: Hashable {
    case email
    case code
    case details
}

/// Whether the username typed in the sign-up form can be used, as far as the app knows.
enum UsernameAvailability: Equatable {
    /// Not checked: empty, not a valid username yet, or the check failed.
    case unknown
    case checking
    case available
    case taken
}

@Observable
final class RegisterViewModel {
    var name = ""
    var username = ""
    var workspaceName = ""
    var email = ""
    var code = ""
    var password = ""
    private(set) var isLoading = false
    private(set) var errorMessage: String?
    private(set) var resendCooldown = 0
    private(set) var usernameAvailability = UsernameAvailability.unknown

    private let session: AppSession
    private var cooldownTask: Task<Void, Never>?

    init(session: AppSession) {
        self.session = session
    }

    var passwordValidation: PasswordValidation { PasswordValidator.validate(password) }

    var usernameHint: String? {
        if usernameAvailability == .taken {
            return String(localized: "This username is already taken.")
        }
        guard !username.isEmpty, !FieldValidator.isValidUsername(username) else { return nil }
        return String(localized: "3 to 30 letters, numbers, - or _")
    }

    /// Asks the backend whether the username is free, once the user stops typing for a moment.
    /// Run from `.task(id:)`, so a newer keystroke cancels it. A failed check leaves it unknown:
    /// registering still catches a taken username.
    func checkUsernameAvailability() async {
        let candidate = username

        guard FieldValidator.isValidUsername(candidate) else {
            usernameAvailability = .unknown
            return
        }

        usernameAvailability = .checking
        try? await Task.sleep(for: .milliseconds(400))
        if Task.isCancelled { return }

        do {
            let available = try await session.authAPI.isUsernameAvailable(candidate)
            if Task.isCancelled || candidate != username { return }
            usernameAvailability = available ? .available : .taken
        } catch {
            if candidate == username { usernameAvailability = .unknown }
        }
    }

    var workspaceHint: String? {
        guard !workspaceName.isEmpty, !FieldValidator.isValidWorkspaceName(workspaceName) else { return nil }
        return String(localized: "Between 3 and 30 characters")
    }

    var canSendCode: Bool { FieldValidator.isValidEmail(normalizedEmail) && !isLoading }
    var canVerifyCode: Bool { code.count == 6 && !isLoading }

    var canRegister: Bool {
        !name.trimmingCharacters(in: .whitespaces).isEmpty &&
            FieldValidator.isValidUsername(username) &&
            usernameAvailability != .taken &&
            usernameAvailability != .checking &&
            FieldValidator.isValidWorkspaceName(workspaceName) &&
            passwordValidation.isValid &&
            !isLoading
    }

    var normalizedEmail: String {
        email.trimmingCharacters(in: .whitespaces).lowercased()
    }

    /// Step 1: emails a verification code.
    func sendCode() async -> Bool {
        guard canSendCode else { return false }

        return await run {
            try await self.session.authAPI.sendRegisterCode(email: self.normalizedEmail)
            self.startCooldown()
        }
    }

    func resendCode() async {
        guard resendCooldown == 0 else { return }
        _ = await sendCode()
    }

    /// Step 2: checks the code before the account details are asked for.
    func verifyCode() async -> Bool {
        guard canVerifyCode else { return false }

        return await run {
            try await self.session.authAPI.verifyRegisterCode(email: self.normalizedEmail, code: self.code)
        }
    }

    /// Step 3: creates the account for the verified email.
    func register() async {
        guard canRegister else { return }

        _ = await run {
            let result = try await self.session.authAPI.register(
                name: self.name.trimmingCharacters(in: .whitespaces),
                email: self.normalizedEmail,
                username: self.username,
                workspaceName: self.workspaceName.trimmingCharacters(in: .whitespaces),
                password: self.password,
                verificationCode: self.code
            )

            switch result {
            case .loggedIn(let user):
                self.session.loggedIn(user)
            case .emailNotConfirmed(let user):
                self.session.needsEmailConfirmation(email: user.email)
            }
        }
    }

    private func run(_ operation: () async throws -> Void) async -> Bool {
        isLoading = true
        errorMessage = nil
        defer { isLoading = false }

        do {
            try await operation()
            return true
        } catch let error as AuthError {
            errorMessage = error.userMessage
        } catch {
            errorMessage = error.userMessage
        }
        return false
    }

    private func startCooldown() {
        cooldownTask?.cancel()
        resendCooldown = 30
        cooldownTask = Task { [weak self] in
            while let self, self.resendCooldown > 0 {
                try? await Task.sleep(for: .seconds(1))
                if Task.isCancelled { return }
                self.resendCooldown -= 1
            }
        }
    }
}

struct RegisterEmailView: View {
    @Bindable var viewModel: RegisterViewModel
    let onCodeSent: () -> Void

    var body: some View {
        AuthFormContainer(
            title: "Create your account",
            subtitle: "First, verify your email. We'll send you a code."
        ) {
            WrTextField("Email", text: $viewModel.email, systemImage: "envelope", kind: .email)
                .accessibilityIdentifier("register.email")

            WrErrorText(viewModel.errorMessage)

            WrPrimaryButton("Send code", isLoading: viewModel.isLoading) {
                Task {
                    if await viewModel.sendCode() {
                        onCodeSent()
                    }
                }
            }
            .disabled(!viewModel.canSendCode)
            .accessibilityIdentifier("register.sendCode")
        }
        .navigationTitle("Register")
        .animation(.default, value: viewModel.errorMessage)
    }
}

struct RegisterCodeView: View {
    @Bindable var viewModel: RegisterViewModel
    let onCodeVerified: () -> Void

    var body: some View {
        AuthFormContainer(
            title: "Verify your email",
            subtitle: "If \(viewModel.normalizedEmail) can be used to register, we sent a code to it."
        ) {
            WrTextField("Code", text: $viewModel.code, systemImage: "number", kind: .code)
                .accessibilityIdentifier("register.code")

            WrErrorText(viewModel.errorMessage)

            WrPrimaryButton("Verify code", isLoading: viewModel.isLoading) {
                Task {
                    if await viewModel.verifyCode() {
                        onCodeVerified()
                    }
                }
            }
            .disabled(!viewModel.canVerifyCode)
            .accessibilityIdentifier("register.verifyCode")

            ResendButton(cooldown: viewModel.resendCooldown) {
                Task { await viewModel.resendCode() }
            }
        }
        .navigationTitle("Register")
        .animation(.default, value: viewModel.errorMessage)
    }
}

struct RegisterView: View {
    @Bindable var viewModel: RegisterViewModel

    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                WrScreenHeader(
                    title: "Create your account",
                    subtitle: "Your account for \(viewModel.normalizedEmail) comes with a team workspace you can invite people to."
                )
                .padding(.bottom, 8)

                WrTextField("Name", text: $viewModel.name, systemImage: "person", kind: .name)
                    .accessibilityIdentifier("register.name")

                VStack(spacing: 4) {
                    WrTextField("Username", text: $viewModel.username, systemImage: "at", kind: .email)
                        .accessibilityIdentifier("register.username")
                        .overlay(alignment: .trailing) {
                            if viewModel.usernameAvailability == .checking {
                                ProgressView()
                                    .controlSize(.small)
                                    .padding(.trailing, 12)
                            }
                        }
                        .task(id: viewModel.username) {
                            await viewModel.checkUsernameAvailability()
                        }
                    hint(viewModel.usernameHint)
                }

                VStack(spacing: 4) {
                    WrTextField("Team workspace name", text: $viewModel.workspaceName, systemImage: "person.3")
                        .accessibilityIdentifier("register.workspace")
                    hint(viewModel.workspaceHint)
                }

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
        .toolbarTitleDisplayMode(.inline)
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
