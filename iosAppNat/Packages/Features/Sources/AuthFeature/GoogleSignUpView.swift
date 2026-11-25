import Observation
import SwiftUI
import WrData
import WrDesign
import WrNetwork
import WrSession

/// The first sign-in of a Google account: the username of the new account (Google's suggestion
/// to start with) and the agreement to the Terms and the Privacy Policy.
@Observable
final class GoogleSignUpViewModel: Identifiable {
    let signUp: GoogleSignUp
    var username: String
    var termsAccepted = false
    var legalDocument: LegalDocumentRoute?
    private(set) var isLoading = false
    private(set) var errorMessage: String?
    private(set) var usernameTaken = false

    private let session: AppSession

    init(signUp: GoogleSignUp, session: AppSession) {
        self.signUp = signUp
        self.session = session
        username = signUp.suggestedUsername
    }

    var id: String { signUp.idToken }

    var usernameHint: String? {
        if usernameTaken {
            return String(localized: "This username is already taken.")
        }
        guard !username.isEmpty, !FieldValidator.isValidUsername(username) else { return nil }
        return String(localized: "3 to 30 letters, numbers, - or _")
    }

    var canCreate: Bool {
        FieldValidator.isValidUsername(trimmedUsername) && termsAccepted && !usernameTaken && !isLoading
    }

    private var trimmedUsername: String { username.trimmingCharacters(in: .whitespaces) }

    func usernameChanged() {
        usernameTaken = false
    }

    func loadLegalDocument(_ type: String) async throws -> LegalDocument {
        try await session.authAPI.legalDocument(type)
    }

    /// Creates the account with the chosen username and the Terms as served now. True once signed in.
    func create() async -> Bool {
        guard canCreate else { return false }

        isLoading = true
        errorMessage = nil
        defer { isLoading = false }

        do {
            // The version of the texts the user agreed to is the one served right now: the
            // backend refuses an older one.
            let legal = try await session.authAPI.legalDocument(LegalDocumentRoute.terms.type)

            let result = try await session.authAPI.loginWithGoogle(
                idToken: signUp.idToken,
                username: trimmedUsername,
                acceptedLegalVersion: legal.version
            )

            switch result {
            case .loggedIn(let user):
                await session.loggedIn(user)
                return true
            case .signUpRequired:
                // Sent with a username, the backend makes the account or says why not.
                errorMessage = AuthError.googleSignInFailed.userMessage
            }
        } catch AuthError.usernameTaken {
            usernameTaken = true
        } catch {
            errorMessage = error.userMessage
        }
        return false
    }
}

struct GoogleSignUpView: View {
    @Bindable var viewModel: GoogleSignUpViewModel
    let onDone: () -> Void

    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 16) {
                    WrScreenHeader(
                        title: "Choose your username",
                        subtitle: "This is the first time \(viewModel.signUp.email) signs in to Writeopia. Pick a username to create your account."
                    )
                    .padding(.bottom, 8)

                    VStack(spacing: 4) {
                        WrTextField("Username", text: $viewModel.username, systemImage: "at", kind: .email)
                            .accessibilityIdentifier("googleSignUp.username")
                            .onChange(of: viewModel.username) { viewModel.usernameChanged() }
                        if let hint = viewModel.usernameHint {
                            Text(hint)
                                .font(.caption)
                                .foregroundStyle(.orange)
                                .frame(maxWidth: .infinity, alignment: .leading)
                        }
                    }

                    TermsAgreementRow(
                        accepted: $viewModel.termsAccepted,
                        disabled: viewModel.isLoading,
                        open: { viewModel.legalDocument = $0 }
                    )

                    WrErrorText(viewModel.errorMessage)

                    WrPrimaryButton("Create account", isLoading: viewModel.isLoading) {
                        Task {
                            if await viewModel.create() { onDone() }
                        }
                    }
                    .disabled(!viewModel.canCreate)
                    .accessibilityIdentifier("googleSignUp.create")
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
                    Button("Cancel") { dismiss() }
                        .disabled(viewModel.isLoading)
                }
            }
            .sheet(item: $viewModel.legalDocument) { route in
                LegalDocumentView(type: route.type) { type in
                    try await viewModel.loadLegalDocument(type)
                }
            }
        }
        .interactiveDismissDisabled(viewModel.isLoading)
        #if os(macOS)
        .frame(minWidth: 440, minHeight: 420)
        #endif
    }
}
