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
    private let googleSignIn: GoogleSignInProviding

    init(session: AppSession, googleSignIn: GoogleSignInProviding = GoogleSignInService()) {
        self.session = session
        self.googleSignIn = googleSignIn
    }

    var canLogIn: Bool {
        FieldValidator.isValidEmail(email) && !password.isEmpty && !isLoading
    }

    /// The Google button only shows once the OAuth client IDs are configured.
    var isGoogleAvailable: Bool {
        GoogleSignInConfig.isConfigured
    }

    func signInWithGoogle() async {
        guard !isLoading else { return }

        isLoading = true
        errorMessage = nil
        defer { isLoading = false }

        do {
            let idToken = try await googleSignIn.signIn()
            try await complete(session.authAPI.loginWithGoogle(idToken: idToken))
        } catch GoogleSignInError.cancelled {
            // Dismissing Google's sheet is not an error.
        } catch let error as AuthError {
            errorMessage = error.userMessage
        } catch is GoogleSignInError {
            errorMessage = AuthError.googleSignInFailed.userMessage
        } catch {
            errorMessage = error.userMessage
        }
    }

    func logIn() async {
        guard canLogIn else { return }

        isLoading = true
        errorMessage = nil
        defer { isLoading = false }

        do {
            try await complete(session.authAPI.login(email: email, password: password))
        } catch let error as AuthError {
            errorMessage = error.userMessage
        } catch {
            errorMessage = error.userMessage
        }
    }

    private func complete(_ result: LoginResult) async throws {
        switch result {
        case .loggedIn(let user):
            session.loggedIn(user)
        case .emailNotConfirmed(let user):
            try? await session.authAPI.resendConfirmation(email: user.email)
            session.needsEmailConfirmation(email: user.email)
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

                if viewModel.isGoogleAvailable {
                    Button(action: signInWithGoogle) {
                        Label("Continue with Google", systemImage: "person.crop.circle.badge.checkmark")
                            .frame(maxWidth: .infinity, minHeight: 28)
                    }
                    .buttonStyle(.bordered)
                    .controlSize(.large)
                    .disabled(viewModel.isLoading)
                    .accessibilityIdentifier("login.google")
                }

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

    private func signInWithGoogle() {
        focusedField = nil
        Task { await viewModel.signInWithGoogle() }
    }
}
