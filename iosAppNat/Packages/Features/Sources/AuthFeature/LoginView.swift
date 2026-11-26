import AuthenticationServices
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
    private(set) var isGoogleLoading = false
    private(set) var errorMessage: String?
    /// The first sign-in of a Google account, asking for a username and the Terms.
    var googleSignUp: GoogleSignUpViewModel?

    private let session: AppSession
    private let googleTokens: GoogleTokenExchanger

    init(session: AppSession, googleTokens: GoogleTokenExchanger = GoogleTokenExchanger()) {
        self.session = session
        self.googleTokens = googleTokens
    }

    var isGoogleAvailable: Bool { GoogleOAuthConfig.isConfigured }

    var canLogIn: Bool {
        FieldValidator.isValidEmail(email) && !password.isEmpty && !isLoading && !isGoogleLoading
    }

    func logIn() async {
        guard canLogIn else { return }

        isLoading = true
        errorMessage = nil
        defer { isLoading = false }

        do {
            switch try await session.authAPI.login(email: email, password: password) {
            case .loggedIn(let user):
                await session.loggedIn(user)
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

    /// Opens Google's page in a browser session, exchanges the code it sends back for an ID token
    /// and signs in with it. `authenticate` opens the page and returns the redirect URL (the
    /// SwiftUI `WebAuthenticationSession`, replaced in tests).
    func signInWithGoogle(
        authorization: GoogleAuthorization = GoogleAuthorization(),
        authenticate: (_ url: URL, _ callbackScheme: String) async throws -> URL
    ) async {
        guard !isLoading, !isGoogleLoading else { return }

        isGoogleLoading = true
        errorMessage = nil
        defer { isGoogleLoading = false }

        do {
            let callback: URL
            do {
                callback = try await authenticate(authorization.url, GoogleOAuthConfig.callbackScheme)
            } catch let error as ASWebAuthenticationSessionError where error.code == .canceledLogin {
                throw GoogleSignInError.cancelled
            }

            let code = try authorization.code(from: callback)
            let idToken = try await googleTokens.idToken(code: code, authorization: authorization)

            switch try await session.authAPI.loginWithGoogle(idToken: idToken) {
            case .loggedIn(let user):
                await session.loggedIn(user)
            case .signUpRequired(let signUp):
                googleSignUp = GoogleSignUpViewModel(signUp: signUp, session: session)
            }
        } catch GoogleSignInError.cancelled {
            // The user closed Google's page.
        } catch let error as AuthError {
            errorMessage = error.userMessage
        } catch is GoogleSignInError {
            errorMessage = AuthError.googleSignInFailed.userMessage
        } catch {
            errorMessage = error.userMessage
        }
    }
}

struct LoginView: View {
    @State private var viewModel: LoginViewModel
    @FocusState private var focusedField: Field?
    @Environment(\.webAuthenticationSession) private var webAuthenticationSession
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
                    orDivider

                    GoogleSignInButton(isLoading: viewModel.isGoogleLoading, action: signInWithGoogle)
                        .disabled(viewModel.isLoading || viewModel.isGoogleLoading)
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
        .sheet(item: $viewModel.googleSignUp) { signUp in
            GoogleSignUpView(viewModel: signUp) { viewModel.googleSignUp = nil }
        }
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
        Task {
            await viewModel.signInWithGoogle { url, scheme in
                try await webAuthenticationSession.authenticate(using: url, callbackURLScheme: scheme)
            }
        }
    }

    private var orDivider: some View {
        HStack(spacing: 12) {
            Rectangle().fill(WrColors.divider).frame(height: 1)
            Text("or")
                .font(.footnote)
                .foregroundStyle(WrColors.textLighter)
            Rectangle().fill(WrColors.divider).frame(height: 1)
        }
        .padding(.vertical, 4)
    }
}

/// "Continue with Google", bordered so it stays second to the email sign-in.
struct GoogleSignInButton: View {
    let isLoading: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            ZStack {
                HStack(spacing: 8) {
                    Text("G")
                        .font(.system(.headline, design: .rounded).weight(.heavy))
                        .foregroundStyle(WrColors.accent)
                    Text("Continue with Google")
                        .font(.headline)
                }
                .opacity(isLoading ? 0 : 1)

                if isLoading {
                    ProgressView()
                        .controlSize(.small)
                }
            }
            .frame(maxWidth: .infinity, minHeight: 28)
        }
        .buttonStyle(.bordered)
        .controlSize(.large)
    }
}
