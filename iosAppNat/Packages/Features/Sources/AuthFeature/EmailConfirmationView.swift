import Observation
import SwiftUI
import WrData
import WrDesign
import WrNetwork
import WrSession

@Observable
final class EmailConfirmationViewModel {
    let email: String
    var code = ""
    private(set) var isLoading = false
    private(set) var errorMessage: String?
    private(set) var infoMessage: String?
    private(set) var resendCooldown = 0

    private let session: AppSession
    private var cooldownTask: Task<Void, Never>?

    init(email: String, session: AppSession) {
        self.email = email
        self.session = session
    }

    var canConfirm: Bool { code.count >= 4 && !isLoading }

    func confirm() async {
        guard canConfirm else { return }

        isLoading = true
        errorMessage = nil
        defer { isLoading = false }

        do {
            switch try await session.authAPI.confirmEmail(email: email, code: code) {
            case .loggedIn(let user):
                session.loggedIn(user)
            case .emailNotConfirmed:
                errorMessage = AuthError.invalidCode.userMessage
            }
        } catch let error as AuthError {
            errorMessage = error.userMessage
        } catch {
            errorMessage = error.userMessage
        }
    }

    func resend() async {
        guard resendCooldown == 0 else { return }

        errorMessage = nil
        do {
            try await session.authAPI.resendConfirmation(email: email)
            infoMessage = String(localized: "We sent a new code to \(email).")
            startCooldown()
        } catch {
            errorMessage = error.userMessage
        }
    }

    func cancel() {
        session.cancelEmailConfirmation()
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

public struct EmailConfirmationView: View {
    @State private var viewModel: EmailConfirmationViewModel

    public init(email: String, session: AppSession) {
        _viewModel = State(initialValue: EmailConfirmationViewModel(email: email, session: session))
    }

    public var body: some View {
        NavigationStack {
            AuthFormContainer(
                title: "Confirm your email",
                subtitle: "Enter the code we sent to \(viewModel.email)."
            ) {
                WrTextField("Confirmation code", text: $viewModel.code, systemImage: "number", kind: .code)
                    .accessibilityIdentifier("confirm.code")

                WrErrorText(viewModel.errorMessage)

                if let info = viewModel.infoMessage {
                    Text(info)
                        .font(.footnote)
                        .foregroundStyle(WrColors.textLighter)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }

                WrPrimaryButton("Confirm", isLoading: viewModel.isLoading) {
                    Task { await viewModel.confirm() }
                }
                .disabled(!viewModel.canConfirm)

                ResendButton(cooldown: viewModel.resendCooldown) {
                    Task { await viewModel.resend() }
                }
            }
            .background(WrColors.background)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Back to sign in", action: viewModel.cancel)
                }
            }
        }
    }
}
