import Foundation
import Testing
@testable import AuthFeature
import WrData
import WrModels
import WrNetwork
import WrSession
import WrStorage

final class StubTransport: HTTPTransport {
    private(set) var paths: [String] = []
    private let routes: [String: (Int, String)]

    init(_ routes: [String: (Int, String)]) {
        self.routes = routes
    }

    func data(for request: URLRequest) async throws -> (Data, URLResponse) {
        let path = request.url!.path()
        paths.append(path)
        let (status, body) = routes[path] ?? (404, "")
        return (Data(body.utf8), HTTPURLResponse(url: request.url!, statusCode: status, httpVersion: nil, headerFields: nil)!)
    }
}

private let userJson = #"{"id":"u1","email":"ana@writeopia.io","name":"Ana"}"#

private func makeSession(
    _ routes: [String: (Int, String)] = [:],
    tokens: InMemoryTokenStore = InMemoryTokenStore()
) -> (AppSession, StubTransport) {
    let transport = StubTransport(routes)
    let defaults = UserDefaults(suiteName: "tests.\(UUID().uuidString)")!
    let session = AppSession(tokenStore: tokens, preferences: Preferences(defaults: defaults), transport: transport)
    return (session, transport)
}

@Suite struct PasswordValidatorTests {
    @Test func strengthFollowsComposeRules() {
        #expect(PasswordValidator.validate("").strength == .none)
        #expect(PasswordValidator.validate("abc").strength == .weak)
        #expect(PasswordValidator.validate("abcdefgh").strength == .medium)
        #expect(PasswordValidator.validate("ab!").strength == .medium)
        #expect(PasswordValidator.validate("abcdefg!").strength == .strong)
        #expect(PasswordValidator.validate("abcdefg!").isValid)
    }

    @Test func fieldRulesMatchBackend() {
        #expect(FieldValidator.isValidEmail("a@b.io"))
        #expect(!FieldValidator.isValidEmail("a@b"))
        #expect(FieldValidator.isValidUsername("ana_b-1"))
        #expect(!FieldValidator.isValidUsername("an"))
        #expect(!FieldValidator.isValidUsername("ana b"))
        #expect(!FieldValidator.isValidWorkspaceName("ab"))
    }
}

@Suite struct AuthFlowTests {
    @Test func startsOnSpaceChoiceAndOfflineGoesStraightToApp() {
        let (session, _) = makeSession()
        #expect(session.phase == .spaceChoice)

        session.chooseOfflineSpace()

        #expect(session.phase == .ready)
        #expect(session.workspace == .local)
        #expect(!session.isOnline)
    }

    @Test func offlineSetupStepsRunBeforeTheApp() {
        let defaults = UserDefaults(suiteName: "tests.\(UUID().uuidString)")!
        let session = AppSession(
            tokenStore: InMemoryTokenStore(),
            preferences: Preferences(defaults: defaults),
            transport: StubTransport([:]),
            offlineSetupSteps: [.localAi, .localFolder]
        )

        session.chooseOfflineSpace()
        #expect(session.phase == .offlineSetup(.localAi))
        // Saved before the setup, so quitting in the middle lands in the app next time.
        #expect(session.spaceType == .offline)
        #expect(session.workspace == .local)

        session.advanceOfflineSetup()
        #expect(session.phase == .offlineSetup(.localFolder))

        session.advanceOfflineSetup()
        #expect(session.phase == .ready)

        // Outside the setup it does nothing.
        session.advanceOfflineSetup()
        #expect(session.phase == .ready)
    }

    @Test func loginStoresTokensAndAsksForWorkspace() async {
        let tokens = InMemoryTokenStore()
        let (session, transport) = makeSession(
            ["/api/auth/login": (200, #"{"accessToken":"a","refreshToken":"r","writeopiaUser":\#(userJson),"enabled":true}"#)],
            tokens: tokens
        )
        session.chooseOnlineSpace()
        #expect(session.phase == .signedOut)

        let viewModel = LoginViewModel(session: session)
        viewModel.email = "ana@writeopia.io"
        viewModel.password = "secret"
        await viewModel.logIn()

        #expect(viewModel.errorMessage == nil)
        #expect(transport.paths == ["/api/auth/login"])
        #expect(tokens.accessToken == "a")
        #expect(session.user?.name == "Ana")
        #expect(session.phase == .chooseWorkspace)

        session.select(workspace: Workspace(id: "w1", userId: "u1", name: "Team", role: "ADMIN"))
        #expect(session.phase == .ready)
        #expect(session.isOnline)
    }

    @Test func invalidCredentialsShowError() async {
        let (session, _) = makeSession(["/api/auth/login": (401, "Invalid credentials")])
        session.chooseOnlineSpace()

        let viewModel = LoginViewModel(session: session)
        viewModel.email = "ana@writeopia.io"
        viewModel.password = "wrong"
        await viewModel.logIn()

        #expect(viewModel.errorMessage == "Wrong email or password.")
        #expect(session.phase == .signedOut)
    }

    @Test func unconfirmedEmailGoesToConfirmationThenToWorkspaces() async {
        let (session, transport) = makeSession([
            "/api/auth/login": (200, #"{"accessToken":null,"refreshToken":null,"writeopiaUser":\#(userJson),"enabled":false}"#),
            "/api/auth/email/resend": (200, #"{"success":true,"message":"Confirmation email sent"}"#),
            "/api/auth/email/confirm": (200, #"{"accessToken":"a","refreshToken":"r","writeopiaUser":\#(userJson),"enabled":true}"#),
        ])
        session.chooseOnlineSpace()

        let login = LoginViewModel(session: session)
        login.email = "ana@writeopia.io"
        login.password = "secret"
        await login.logIn()

        #expect(session.phase == .emailConfirmation(email: "ana@writeopia.io"))
        #expect(transport.paths.contains("/api/auth/email/resend"))

        let confirmation = EmailConfirmationViewModel(email: "ana@writeopia.io", session: session)
        confirmation.code = "123456"
        await confirmation.confirm()

        #expect(session.phase == .chooseWorkspace)
    }

    @Test func registerRequiresEmailConfirmation() async {
        let (session, _) = makeSession([
            "/api/auth/register": (201, #"{"writeopiaUser":\#(userJson),"emailConfirmationRequired":true}"#),
        ])
        session.chooseOnlineSpace()

        let viewModel = RegisterViewModel(session: session)
        viewModel.name = "Ana"
        viewModel.username = "ana"
        viewModel.workspaceName = "Ana's team"
        viewModel.email = "ana@writeopia.io"
        viewModel.password = "abcdefg!"
        #expect(viewModel.canRegister)

        await viewModel.register()

        #expect(session.phase == .emailConfirmation(email: "ana@writeopia.io"))
    }

    @Test func passwordRecoveryRunsAllSteps() async {
        let ok = #"{"success":true,"message":"ok"}"#
        let (session, transport) = makeSession([
            "/api/auth/password/forgot": (200, ok),
            "/api/auth/password/verify-code": (200, ok),
            "/api/auth/password/reset-with-code": (200, ok),
        ])

        let viewModel = ForgotPasswordViewModel(authAPI: session.authAPI)
        viewModel.email = "Ana@Writeopia.io"
        #expect(await viewModel.sendCode())
        #expect(viewModel.resendCooldown > 0)

        viewModel.code = "123456"
        #expect(await viewModel.verifyCode())

        viewModel.password = "abcdefg!"
        viewModel.repeatPassword = "abcdefg!"
        #expect(await viewModel.resetPassword())
        #expect(viewModel.didResetPassword)
        #expect(transport.paths == ["/api/auth/password/forgot", "/api/auth/password/verify-code", "/api/auth/password/reset-with-code"])
    }

    @Test func invalidResetCodeShowsError() async {
        let (session, _) = makeSession([
            "/api/auth/password/verify-code": (400, #"{"success":false,"message":"Invalid or expired code"}"#),
        ])
        let viewModel = ForgotPasswordViewModel(authAPI: session.authAPI)
        viewModel.email = "ana@writeopia.io"
        viewModel.code = "0000"

        #expect(await viewModel.verifyCode() == false)
        #expect(viewModel.errorMessage == AuthError.invalidCode.userMessage)
    }

    @Test func workspacesLoadAndCanBeCreated() async {
        let (session, transport) = makeSession(
            [
                "/api/workspace/user": (200, #"[{"id":"w2","userId":"u1","name":"Zeta","role":"USER","documentCount":1},{"id":"w1","userId":"u1","name":"Alpha","role":"ADMIN","documentCount":4}]"#),
                "/api/workspace/create": (201, #"{"message":"Workspace created"}"#),
                "/api/auth/user/current": (200, userJson),
            ],
            tokens: InMemoryTokenStore(accessToken: "a", refreshToken: "r")
        )

        let viewModel = ChooseWorkspaceViewModel(session: session)
        await viewModel.load()
        #expect(viewModel.workspaces.map(\.name) == ["Alpha", "Zeta"])
        #expect(session.user?.name == "Ana")

        #expect(await viewModel.create(name: "x") == false)
        #expect(await viewModel.create(name: "New team"))
        #expect(transport.paths.contains("/api/workspace/create"))

        viewModel.select(viewModel.workspaces[0])
        #expect(session.workspace?.id == "w1")
    }

    @Test func switchingSpaceAndSigningInFromOffline() async {
        let tokens = InMemoryTokenStore(accessToken: "a", refreshToken: "r")
        let (session, _) = makeSession(["/api/auth/logout": (200, "Logged out successfully")], tokens: tokens)
        session.chooseOfflineSpace()

        session.signIn()
        #expect(session.phase == .chooseWorkspace)

        session.select(workspace: Workspace(id: "w1", userId: "u1", name: "Team", role: "ADMIN"))
        session.switchSpace()
        #expect(session.phase == .spaceChoice)

        session.chooseOnlineSpace()
        #expect(session.phase == .ready)

        await session.logout()
        #expect(tokens.accessToken == nil)
        #expect(session.phase == .spaceChoice)
        #expect(session.spaceType == nil)
    }
}

@Suite struct AiProviderTests {
    private func session(online: Bool, appleIntelligence: Bool, defaults: UserDefaults = UserDefaults(suiteName: "tests.\(UUID().uuidString)")!) -> AppSession {
        let session = AppSession(
            tokenStore: InMemoryTokenStore(accessToken: online ? "token" : nil),
            preferences: Preferences(defaults: defaults),
            transport: StubTransport([:]),
            isAppleIntelligenceAvailable: { appleIntelligence }
        )
        if online {
            session.chooseOnlineSpace()
            session.select(workspace: Workspace(id: "w1", userId: "u1", name: "Team", role: "ADMIN"))
        } else {
            session.chooseOfflineSpace()
        }
        return session
    }

    @Test func cloudNeedsAWorkspaceOfTheOpenSpace() {
        let session = AppSession(
            tokenStore: InMemoryTokenStore(accessToken: "token"),
            preferences: Preferences(defaults: UserDefaults(suiteName: "tests.\(UUID().uuidString)")!),
            transport: StubTransport([:]),
            isAppleIntelligenceAvailable: { false }
        )
        session.chooseOnlineSpace()
        session.aiProvider = .cloud
        #expect(session.aiClient == nil)
    }

    @Test func appleIntelligenceIsTheDefaultInBothSpaces() {
        let offline = session(online: false, appleIntelligence: true)
        #expect(offline.aiProvider == .appleIntelligence)
        #expect(offline.aiClient === offline.appleIntelligence)

        let online = session(online: true, appleIntelligence: true)
        #expect(online.aiClient === online.appleIntelligence)
    }

    @Test func cloudIsUsedWhenAppleIntelligenceIsUnavailable() {
        let online = session(online: true, appleIntelligence: false)
        #expect(online.aiClient === online.aiAPI)

        // The private space has no backend: no AI at all.
        #expect(session(online: false, appleIntelligence: false).aiClient == nil)
    }

    @Test func cloudCanBePickedInTheOpenSpaceAndIsRemembered() {
        let defaults = UserDefaults(suiteName: "tests.\(UUID().uuidString)")!
        let online = session(online: true, appleIntelligence: true, defaults: defaults)

        online.aiProvider = .cloud

        #expect(online.aiClient === online.aiAPI)
        #expect(session(online: true, appleIntelligence: true, defaults: defaults).aiProvider == .cloud)
        // Offline the cloud can't answer, so Apple Intelligence does.
        let offline = session(online: false, appleIntelligence: true, defaults: defaults)
        #expect(offline.aiClient === offline.appleIntelligence)
    }
}
