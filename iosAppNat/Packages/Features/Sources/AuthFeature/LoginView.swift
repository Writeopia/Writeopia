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
        WrOnboardingScreen(width: 440, back: backToSpaces) {
            VStack(alignment: .leading, spacing: WrTheme.Space.s200) {
                WrTitleBlock(
                    eyebrow: "Open space",
                    title: "Welcome back",
                    subtitle: "Sign in to sync your documents across devices.",
                    eyebrowColor: WrTheme.Colors.accent
                )
                .padding(.bottom, WrTheme.Space.s100)

                field("Email") {
                    WrTextField("Email", text: $viewModel.email, systemImage: "envelope", kind: .email)
                        .focused($focusedField, equals: .email)
                        .submitLabel(.next)
                        .onSubmit { focusedField = .password }
                        .accessibilityIdentifier("login.email")
                }

                field("Password") {
                    WrTextField("Password", text: $viewModel.password, systemImage: "lock", kind: .password)
                        .focused($focusedField, equals: .password)
                        .submitLabel(.go)
                        .onSubmit(logIn)
                        .accessibilityIdentifier("login.password")
                } trailing: {
                    Button("Forgot password?") { navigate(.forgotPassword) }
                        .buttonStyle(.plain)
                        .wrTextStyle(.labelSmall)
                        .foregroundStyle(WrTheme.Colors.textLink)
                }

                WrErrorText(viewModel.errorMessage)

                WrPrimaryButton("Sign in", isLoading: viewModel.isLoading, action: logIn)
                    .disabled(!viewModel.canLogIn)
                    .accessibilityIdentifier("login.submit")

                if viewModel.isGoogleAvailable {
                    orDivider

                    if viewModel.isGoogleLoading {
                        WrStatusBanner("Finish signing in on Google's page…")
                            .accessibilityIdentifier("login.google.waiting")
                    } else {
                        GoogleSignInButton(isLoading: false, action: signInWithGoogle)
                            .disabled(viewModel.isLoading)
                            .accessibilityIdentifier("login.google")
                    }
                }

                HStack(spacing: WrTheme.Space.s50) {
                    Text("New to Writeopia?")
                        .foregroundStyle(WrTheme.Colors.textTertiary)
                    Button("Create an account") { navigate(.register) }
                        .buttonStyle(.plain)
                        .fontWeight(.semibold)
                        .foregroundStyle(WrTheme.Colors.textLink)
                }
                .wrTextStyle(.bodySmall)
                .frame(maxWidth: .infinity)
                .padding(.top, WrTheme.Space.s100)
            }
        }
        .scrollDismissesKeyboard(.interactively)
        .animation(.default, value: viewModel.errorMessage)
        .animation(.default, value: viewModel.isGoogleLoading)
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

    /// A Mac window has no navigation bar for the way back, so the screen shows it; on iOS the
    /// toolbar has it.
    private var backToSpaces: (title: LocalizedStringKey, action: () -> Void)? {
        #if os(macOS)
        return ("Choose space", session.switchSpace)
        #else
        return nil
        #endif
    }

    /// A label above a field, with an optional link at its end, like the v4 forms.
    private func field<Content: View, Trailing: View>(
        _ label: LocalizedStringKey,
        @ViewBuilder field: () -> Content,
        @ViewBuilder trailing: () -> Trailing = { EmptyView() }
    ) -> some View {
        VStack(alignment: .leading, spacing: WrTheme.Space.s100) {
            HStack {
                Text(label)
                    .wrTextStyle(.labelSmall)
                    .foregroundStyle(WrTheme.Colors.textSecondary)
                Spacer()
                trailing()
            }
            field()
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
        HStack(spacing: WrTheme.Space.s150) {
            WrDivider()
            Text("or")
                .wrTextStyle(.bodySmall)
                .foregroundStyle(WrTheme.Colors.textTertiary)
            WrDivider()
        }
        .padding(.vertical, WrTheme.Space.s50)
    }
}

/// "Continue with Google", bordered so it stays second to the email sign-in.
struct GoogleSignInButton: View {
    let isLoading: Bool
    let action: () -> Void
    @Environment(\.wrUsesV4) private var usesV4

    var body: some View {
        if usesV4 {
            Button(action: action) {
                HStack(spacing: WrTheme.Space.s100) {
                    GoogleLogo()
                        .frame(width: 16, height: 16)
                    Text("Continue with Google")
                }
            }
            .buttonStyle(WrButtonStyle(kind: .secondary, size: .large, fillsWidth: true))
        } else {
            legacy
        }
    }

    private var legacy: some View {
        Button(action: action) {
            ZStack {
                HStack(spacing: 10) {
                    GoogleLogo()
                        .frame(width: 18, height: 18)
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
