import Observation
import SwiftUI
import WrData
import WrDesign
import WrNetwork

@Observable
final class ForgotPasswordViewModel {
    var email = ""
    var code = ""
    var password = ""
    var repeatPassword = ""
    private(set) var isLoading = false
    private(set) var errorMessage: String?
    private(set) var resendCooldown = 0
    private(set) var didResetPassword = false

    private let authAPI: AuthAPI
    private var cooldownTask: Task<Void, Never>?

    init(authAPI: AuthAPI) {
        self.authAPI = authAPI
    }

    var canSendCode: Bool { FieldValidator.isValidEmail(email) && !isLoading }
    var canVerifyCode: Bool { code.count >= 4 && !isLoading }
    var passwordValidation: PasswordValidation { PasswordValidator.validate(password) }
    var passwordsMatch: Bool { password == repeatPassword }
    var canResetPassword: Bool {
        passwordValidation.isValid && passwordsMatch && !isLoading
    }

    func sendCode() async -> Bool {
        await run {
            try await self.authAPI.requestPasswordReset(email: self.email.trimmingCharacters(in: .whitespaces))
            self.startCooldown()
        }
    }

    func resendCode() async {
        guard resendCooldown == 0 else { return }
        _ = await sendCode()
    }

    func verifyCode() async -> Bool {
        await run {
            try await self.authAPI.verifyResetCode(email: self.normalizedEmail, code: self.code)
        }
    }

    func resetPassword() async -> Bool {
        await run {
            try await self.authAPI.resetPassword(email: self.normalizedEmail, code: self.code, newPassword: self.password)
            self.didResetPassword = true
        }
    }

    private var normalizedEmail: String {
        email.trimmingCharacters(in: .whitespaces).lowercased()
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
        resendCooldown = 60
        cooldownTask = Task { [weak self] in
            while let self, self.resendCooldown > 0 {
                try? await Task.sleep(for: .seconds(1))
                if Task.isCancelled { return }
                self.resendCooldown -= 1
            }
        }
    }
}

struct ForgotPasswordEmailView: View {
    @Bindable var viewModel: ForgotPasswordViewModel
    let onCodeSent: () -> Void

    var body: some View {
        AuthFormContainer(
            title: "Reset your password",
            subtitle: "Enter the email of your account and we'll send you a code."
        ) {
            WrTextField("Email", text: $viewModel.email, systemImage: "envelope", kind: .email)
                .accessibilityIdentifier("forgot.email")

            WrErrorText(viewModel.errorMessage)

            WrPrimaryButton("Send code", isLoading: viewModel.isLoading) {
                Task {
                    if await viewModel.sendCode() {
                        onCodeSent()
                    }
                }
            }
            .disabled(!viewModel.canSendCode)
        }
        .navigationTitle("Password recovery")
    }
}

struct ForgotPasswordCodeView: View {
    @Bindable var viewModel: ForgotPasswordViewModel
    let onCodeVerified: () -> Void

    var body: some View {
        AuthFormContainer(
            title: "Check your inbox",
            subtitle: "If \(viewModel.email) has an account, we sent a code to it."
        ) {
            WrTextField("Code", text: $viewModel.code, systemImage: "number", kind: .code)
                .accessibilityIdentifier("forgot.code")

            WrErrorText(viewModel.errorMessage)

            WrPrimaryButton("Verify code", isLoading: viewModel.isLoading) {
                Task {
                    if await viewModel.verifyCode() {
                        onCodeVerified()
                    }
                }
            }
            .disabled(!viewModel.canVerifyCode)

            ResendButton(cooldown: viewModel.resendCooldown) {
                Task { await viewModel.resendCode() }
            }
        }
        .navigationTitle("Password recovery")
    }
}

struct ForgotPasswordNewPasswordView: View {
    @Bindable var viewModel: ForgotPasswordViewModel
    let onFinished: () -> Void

    var body: some View {
        AuthFormContainer(title: "Choose a new password", subtitle: nil) {
            WrTextField("New password", text: $viewModel.password, systemImage: "lock", kind: .newPassword)
            PasswordStrengthView(validation: viewModel.passwordValidation)

            WrTextField("Repeat password", text: $viewModel.repeatPassword, systemImage: "lock.rotation", kind: .newPassword)

            if !viewModel.repeatPassword.isEmpty && !viewModel.passwordsMatch {
                WrErrorText(String(localized: "The passwords don't match."))
            }

            WrErrorText(viewModel.errorMessage)

            WrPrimaryButton("Reset password", isLoading: viewModel.isLoading) {
                Task { _ = await viewModel.resetPassword() }
            }
            .disabled(!viewModel.canResetPassword)
        }
        .navigationTitle("Password recovery")
        .alert("Password changed", isPresented: .constant(viewModel.didResetPassword)) {
            Button("Sign in", action: onFinished)
        } message: {
            Text("You can now sign in with your new password.")
        }
    }
}

struct ResendButton: View {
    let cooldown: Int
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            if cooldown > 0 {
                Text("Resend code in \(cooldown)s")
            } else {
                Text("Resend code")
            }
        }
            .font(.callout.weight(.semibold))
            .tint(WrColors.accent)
            .disabled(cooldown > 0)
            .monospacedDigit()
    }
}

struct AuthFormContainer<Content: View>: View {
    let title: LocalizedStringKey
    let subtitle: LocalizedStringKey?
    @ViewBuilder let content: Content

    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                WrScreenHeader(title: title, subtitle: subtitle)
                    .padding(.bottom, 8)
                content
            }
            .padding(24)
            .frame(maxWidth: 520)
            .frame(maxWidth: .infinity)
        }
        .scrollDismissesKeyboard(.interactively)
        .toolbarTitleDisplayMode(.inline)
    }
}
