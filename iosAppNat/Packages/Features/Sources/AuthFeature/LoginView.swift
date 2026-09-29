import Observation
import SwiftUI
import WrData
import WrDesign
import WrModels
import WrNetwork
import WrSession

@Observable
final class LoginViewModel {
    var email = ""
    var password = ""
    private(set) var isLoading = false
    private(set) var errorMessage: String?

    private let session: AppSession

    init(session: AppSession) {
        self.session = session
    }

    var canLogIn: Bool {
        FieldValidator.isValidEmail(email) && !password.isEmpty && !isLoading
    }

    func logIn() async {
        guard canLogIn else { return }

        isLoading = true
        errorMessage = nil
        defer { isLoading = false }

        do {
            switch try await session.authAPI.login(email: email, password: password) {
            case .loggedIn(let user):
                session.loggedIn(user)
            case .emailNotConfirmed(let user):
                try? await session.authAPI.resendConfirmation(email: user.email)
                session.needsEmailConfirmation(email: user.email)
            }
        } catch let error as AuthError {
            errorMessage = error.userMessage
        } catch {
            errorMessage = error.userMessage
        }
    }
}

struct LoginView: View {
    @State private var viewModel: LoginViewModel
    @FocusState private var focusedField: Field?
    private let session: AppSession
    private let navigate: (LoginDestination) -> Void

    private enum Field {
        case email
        case password
    }

    init(session: AppSession, navigate: @escaping (LoginDestination) -> Void) {
        self.session = session
        self.navigate = navigate
        _viewModel = State(initialValue: LoginViewModel(session: session))
    }

    var body: some View {
        ScrollView {
            VStack(spacing: 20) {
                #if os(macOS)
                WrBackButton("Choose space", action: session.switchSpace)
                    .accessibilityIdentifier("login.back")
                #endif

                WrScreenHeader(
                    eyebrow: "Open space",
                    title: "Welcome back",
                    subtitle: "Sign in to sync your documents across devices."
                )
                .padding(.bottom, 12)

                WrTextField("Email", text: $viewModel.email, systemImage: "envelope", kind: .email)
                    .focused($focusedField, equals: .email)
                    .submitLabel(.next)
                    .onSubmit { focusedField = .password }
                    .accessibilityIdentifier("login.email")

                WrTextField("Password", text: $viewModel.password, systemImage: "lock", kind: .password)
                    .focused($focusedField, equals: .password)
                    .submitLabel(.go)
                    .onSubmit(logIn)
                    .accessibilityIdentifier("login.password")

                HStack {
                    Spacer()
                    Button("Forgot password?") { navigate(.forgotPassword) }
                        .font(.footnote.weight(.semibold))
                        .tint(WrColors.accent)
                }

                WrErrorText(viewModel.errorMessage)

                WrPrimaryButton("Sign in", isLoading: viewModel.isLoading, action: logIn)
                    .disabled(!viewModel.canLogIn)
                    .accessibilityIdentifier("login.submit")

                HStack(spacing: 4) {
                    Text("New to Writeopia?")
                        .foregroundStyle(WrColors.textLighter)
                    Button("Create an account") { navigate(.register) }
                        .fontWeight(.semibold)
                        .tint(WrColors.accent)
                }
                .font(.callout)
                .padding(.top, 8)
            }
            .padding(24)
            .frame(maxWidth: 520)
            .frame(maxWidth: .infinity)
        }
        .scrollDismissesKeyboard(.interactively)
        .background(WrColors.background)
        .animation(.default, value: viewModel.errorMessage)
        .toolbar {
            ToolbarItem(placement: .cancellationAction) {
                Button {
                    session.switchSpace()
                } label: {
                    Label("Choose space", systemImage: "chevron.backward")
                }
            }
        }
    }

    private func logIn() {
        focusedField = nil
        Task { await viewModel.logIn() }
    }
}
