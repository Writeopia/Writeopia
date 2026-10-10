import SwiftUI
import WrDesign
import WrSession

enum AuthRoute: Hashable {
    /// The three sign-up steps share one view model, so the email and code carry over.
    case register(RegisterStep, RegisterViewModel)
    /// The three recovery steps share one view model, so the email and code carry over.
    case forgotPassword(ForgotPasswordStep, ForgotPasswordViewModel)
}

enum ForgotPasswordStep: Hashable {
    case email
    case code
    case newPassword
}

extension RegisterViewModel: Hashable {
    nonisolated static func == (lhs: RegisterViewModel, rhs: RegisterViewModel) -> Bool {
        lhs === rhs
    }

    nonisolated func hash(into hasher: inout Hasher) {
        hasher.combine(ObjectIdentifier(self))
    }
}

extension ForgotPasswordViewModel: Hashable {
    nonisolated static func == (lhs: ForgotPasswordViewModel, rhs: ForgotPasswordViewModel) -> Bool {
        lhs === rhs
    }

    nonisolated func hash(into hasher: inout Hasher) {
        hasher.combine(ObjectIdentifier(self))
    }
}

/// Login, registration and password recovery of the open space.
public struct AuthFlowView: View {
    @Environment(AppSession.self) private var session
    @State private var path: [AuthRoute] = []

    public init() {}

    public var body: some View {
        NavigationStack(path: $path) {
            LoginView(session: session, navigate: navigate)
                .navigationDestination(for: AuthRoute.self) { route in
                    destination(route)
                        .background(WrColors.background)
                }
        }
    }

    private func navigate(_ destination: LoginDestination) {
        switch destination {
        case .register:
            path.append(.register(.email, RegisterViewModel(session: session)))
        case .forgotPassword:
            path.append(.forgotPassword(.email, ForgotPasswordViewModel(authAPI: session.authAPI)))
        }
    }

    @ViewBuilder
    private func destination(_ route: AuthRoute) -> some View {
        switch route {
        case .register(.email, let viewModel):
            RegisterEmailView(viewModel: viewModel) {
                path.append(.register(.code, viewModel))
            }
        case .register(.code, let viewModel):
            RegisterCodeView(viewModel: viewModel) {
                path.append(.register(.details, viewModel))
            }
        case .register(.details, let viewModel):
            RegisterView(viewModel: viewModel)
        case .forgotPassword(.email, let viewModel):
            ForgotPasswordEmailView(viewModel: viewModel) {
                path.append(.forgotPassword(.code, viewModel))
            }
        case .forgotPassword(.code, let viewModel):
            ForgotPasswordCodeView(viewModel: viewModel) {
                path.append(.forgotPassword(.newPassword, viewModel))
            }
        case .forgotPassword(.newPassword, let viewModel):
            ForgotPasswordNewPasswordView(viewModel: viewModel) {
                path.removeAll()
            }
        }
    }
}

enum LoginDestination {
    case register
    case forgotPassword
}
