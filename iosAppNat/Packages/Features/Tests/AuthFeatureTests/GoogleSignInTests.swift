import Foundation
import Testing
@testable import AuthFeature
import WrData
import WrSession
import WrStorage

private let googleLoginJson = #"{"accessToken":"a","refreshToken":"r","writeopiaUser":{"id":"u1","email":"ana@gmail.com","name":"Ana","tier":"FREE"},"enabled":true}"#
private let signUpJson = #"{"idToken":"server-token","email":"ana@gmail.com","name":"Ana","suggestedUsername":"ana"}"#
private let legalJson = #"{"type":"terms","version":"v1","effectiveDate":"2026-11-12","title":"Terms of Service","sections":[]}"#
private let tokenJson = #"{"access_token":"x","id_token":"google-id-token","token_type":"Bearer"}"#

private func makeSession(_ routes: [String: (Int, String)]) -> (AppSession, StubTransport) {
    let transport = StubTransport(routes)
    let defaults = UserDefaults(suiteName: "tests.\(UUID().uuidString)")!
    let session = AppSession(tokenStore: InMemoryTokenStore(), preferences: Preferences(defaults: defaults), transport: transport)
    session.chooseOnlineSpace()
    return (session, transport)
}

private let authorization = GoogleAuthorization(
    clientID: "123-abc.apps.googleusercontent.com",
    redirectURI: "com.googleusercontent.apps.123-abc:/oauthredirect",
    verifier: "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk",
    state: "state-1"
)

private func callback(_ query: String) -> URL {
    URL(string: "com.googleusercontent.apps.123-abc:/oauthredirect?\(query)")!
}

@Suite struct GoogleAuthorizationTests {
    @Test func challengeFollowsRfc7636() {
        // The example of RFC 7636, appendix B.
        #expect(GoogleAuthorization.challenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk") == "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM")
    }

    @Test func randomVerifiersAreUrlSafeAndLongEnough() {
        let verifier = GoogleAuthorization.randomString(bytes: 64)
        #expect(verifier.count == 86)
        #expect(verifier.wholeMatch(of: /[A-Za-z0-9\-_]+/) != nil)
        #expect(verifier != GoogleAuthorization.randomString(bytes: 64))
    }

    @Test func urlAsksForACodeWithPkce() throws {
        let components = try #require(URLComponents(url: authorization.url, resolvingAgainstBaseURL: false))
        let query = Dictionary(uniqueKeysWithValues: (components.queryItems ?? []).map { ($0.name, $0.value ?? "") })

        #expect(components.host == "accounts.google.com")
        #expect(query["client_id"] == "123-abc.apps.googleusercontent.com")
        #expect(query["redirect_uri"] == "com.googleusercontent.apps.123-abc:/oauthredirect")
        #expect(query["response_type"] == "code")
        #expect(query["scope"] == "openid email profile")
        #expect(query["code_challenge"] == "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM")
        #expect(query["code_challenge_method"] == "S256")
        #expect(query["state"] == "state-1")
    }

    @Test func readsTheCodeOfTheRedirect() throws {
        #expect(try authorization.code(from: callback("state=state-1&code=4/abc")) == "4/abc")
    }

    @Test func refusesAnotherState() {
        #expect(throws: GoogleSignInError.failed("OAuth state mismatch")) {
            try authorization.code(from: callback("state=other&code=4/abc"))
        }
    }

    @Test func aDeniedAccessIsACancel() {
        #expect(throws: GoogleSignInError.cancelled) {
            try authorization.code(from: callback("error=access_denied&state=state-1"))
        }
    }

    @Test func callbackSchemeIsTheReversedClientId() {
        guard GoogleOAuthConfig.isConfigured else { return }
        #expect(GoogleOAuthConfig.callbackScheme.hasPrefix("com.googleusercontent.apps."))
        #expect(!GoogleOAuthConfig.callbackScheme.hasSuffix(".apps.googleusercontent.com"))
    }
}

@Suite struct GoogleTokenExchangerTests {
    @Test func exchangesTheCodeWithoutASecret() async throws {
        let transport = StubTransport(["/token": (200, tokenJson)])

        let idToken = try await GoogleTokenExchanger(transport: transport).idToken(code: "4/a+b", authorization: authorization)

        #expect(idToken == "google-id-token")
        let body = try #require(transport.bodies.first)
        #expect(body.contains("client_id=123-abc.apps.googleusercontent.com"))
        #expect(body.contains("code=4/a%2Bb"))
        #expect(body.contains("code_verifier=dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"))
        #expect(body.contains("grant_type=authorization_code"))
        #expect(!body.contains("client_secret"))
    }

    @Test func reportsGooglesError() async {
        let transport = StubTransport(["/token": (400, #"{"error":"invalid_grant","error_description":"Bad Request"}"#)])

        await #expect(throws: GoogleSignInError.failed("Google code exchange failed: Bad Request")) {
            try await GoogleTokenExchanger(transport: transport).idToken(code: "c", authorization: authorization)
        }
    }
}

@Suite struct GoogleLoginTests {
    private func signIn(_ viewModel: LoginViewModel, query: String = "state=state-1&code=c") async {
        await viewModel.signInWithGoogle(authorization: authorization) { _, _ in callback(query) }
    }

    @Test func anExistingAccountSignsInAndGoesToThePaywall() async {
        let (session, transport) = makeSession([
            "/token": (200, tokenJson),
            "/api/auth/login/google": (200, googleLoginJson),
            "/api/auth/subscription": (200, freeJson),
        ])
        let viewModel = LoginViewModel(session: session, googleTokens: GoogleTokenExchanger(transport: transport))

        await signIn(viewModel)

        #expect(session.phase == .paywall)
        #expect(session.user?.email == "ana@gmail.com")
        #expect(viewModel.errorMessage == nil)
        let loginBody = transport.bodies[transport.paths.firstIndex(of: "/api/auth/login/google")!]
        #expect(loginBody == #"{"idToken":"google-id-token"}"#)
    }

    @Test func aNewAccountAsksForAUsername() async {
        let (session, transport) = makeSession([
            "/token": (200, tokenJson),
            "/api/auth/login/google": (422, signUpJson),
        ])
        let viewModel = LoginViewModel(session: session, googleTokens: GoogleTokenExchanger(transport: transport))

        await signIn(viewModel)

        #expect(viewModel.googleSignUp?.username == "ana")
        #expect(viewModel.googleSignUp?.signUp.idToken == "server-token")
        #expect(viewModel.errorMessage == nil)
        #expect(session.phase != .paywall)
    }

    @Test func closingGooglesPageShowsNothing() async {
        let (session, transport) = makeSession([:])
        let viewModel = LoginViewModel(session: session, googleTokens: GoogleTokenExchanger(transport: transport))

        await signIn(viewModel, query: "error=access_denied&state=state-1")

        #expect(viewModel.errorMessage == nil)
        #expect(transport.paths.isEmpty)
    }

    @Test func anAccountBeingDeletedSaysSo() async {
        let (session, transport) = makeSession([
            "/token": (200, tokenJson),
            "/api/auth/login/google": (403, "Account is being deleted"),
        ])
        let viewModel = LoginViewModel(session: session, googleTokens: GoogleTokenExchanger(transport: transport))

        await signIn(viewModel)

        #expect(viewModel.errorMessage == AuthError.accountDeletionPending.userMessage)
    }

    @Test func aRefusedTokenFails() async {
        let (session, transport) = makeSession([
            "/token": (200, tokenJson),
            "/api/auth/login/google": (401, "Invalid Google token"),
        ])
        let viewModel = LoginViewModel(session: session, googleTokens: GoogleTokenExchanger(transport: transport))

        await signIn(viewModel)

        #expect(viewModel.errorMessage == AuthError.googleSignInFailed.userMessage)
        #expect(session.user == nil)
    }
}

@Suite struct GoogleSignUpTests {
    private let signUp = GoogleSignUp(idToken: "server-token", email: "ana@gmail.com", name: "Ana", suggestedUsername: "ana")

    @Test func createsTheAccountWithTheUsernameAndTheTerms() async {
        let (session, transport) = makeSession([
            "/api/auth/legal/terms": (200, legalJson),
            "/api/auth/login/google": (200, googleLoginJson),
            "/api/auth/subscription": (200, premiumJson),
        ])
        let viewModel = GoogleSignUpViewModel(signUp: signUp, session: session)
        #expect(!viewModel.canCreate)

        viewModel.username = " ana_b "
        viewModel.termsAccepted = true
        #expect(await viewModel.create())

        #expect(session.phase == .chooseWorkspace)
        let body = transport.bodies[transport.paths.firstIndex(of: "/api/auth/login/google")!]
        #expect(body.contains(#""idToken":"server-token""#))
        #expect(body.contains(#""username":"ana_b""#))
        #expect(body.contains(#""acceptedLegalVersion":"v1""#))
    }

    @Test func aTakenUsernameIsShownUntilChanged() async {
        let (session, _) = makeSession([
            "/api/auth/legal/terms": (200, legalJson),
            "/api/auth/login/google": (409, "Username taken"),
        ])
        let viewModel = GoogleSignUpViewModel(signUp: signUp, session: session)
        viewModel.termsAccepted = true

        #expect(await viewModel.create() == false)
        #expect(viewModel.usernameHint == "This username is already taken.")
        #expect(!viewModel.canCreate)

        viewModel.username = "ana2"
        viewModel.usernameChanged()
        #expect(viewModel.canCreate)
    }
}
