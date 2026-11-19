import Foundation
import Testing
@testable import AuthFeature
import WrModels
import WrNetwork
import WrSession
import WrStorage

private let loginJson = #"{"accessToken":"a","refreshToken":"r","writeopiaUser":{"id":"u1","email":"ana@writeopia.io","name":"Ana","tier":"FREE"},"enabled":true}"#

@Suite struct PaywallTests {
    private let defaults = UserDefaults(suiteName: "tests.\(UUID().uuidString)")!

    private func signedIn(
        subscription: (Int, String),
        tokens: InMemoryTokenStore = InMemoryTokenStore()
    ) async -> (AppSession, StubTransport) {
        let transport = StubTransport([
            "/api/auth/login": (200, loginJson),
            "/api/auth/subscription": subscription,
            "/api/auth/logout": (200, "Logged out successfully"),
        ])
        let session = AppSession(tokenStore: tokens, preferences: Preferences(defaults: defaults), transport: transport)
        session.chooseOnlineSpace()
        let login = LoginViewModel(session: session)
        login.email = "ana@writeopia.io"
        login.password = "secret"
        await login.logIn()
        return (session, transport)
    }

    @Test func aPremiumUserGoesOnAsBefore() async {
        let (session, _) = await signedIn(subscription: (200, premiumJson))

        #expect(session.phase == .chooseWorkspace)
        // The login answered FREE, the subscription route is the source of truth.
        #expect(session.user?.isPremium == true)
    }

    @Test func aUserWithoutSubscriptionSeesThePaywallWithThePrice() async {
        let tokens = InMemoryTokenStore()
        let (session, _) = await signedIn(subscription: (200, freeJson), tokens: tokens)

        #expect(session.phase == .paywall)
        #expect(tokens.accessToken == "a")

        let viewModel = PaywallViewModel(session: session, locale: Locale(identifier: "en_US"))
        #expect(!viewModel.isWaitingForFirstAnswer)
        #expect(viewModel.monthlyPrice == "$5.00")
    }

    @Test func checkingAgainAfterSubscribingGoesToTheWorkspaces() async {
        let (session, transport) = await signedIn(subscription: (200, freeJson))
        let viewModel = PaywallViewModel(session: session)

        await viewModel.check()
        #expect(viewModel.stillNotPremium)
        #expect(session.phase == .paywall)

        transport.routes["/api/auth/subscription"] = (200, premiumJson)
        await viewModel.check()

        #expect(session.phase == .chooseWorkspace)
        #expect(!viewModel.stillNotPremium)
        // Stored as premium: the next launch doesn't ask again.
        let next = AppSession(tokenStore: InMemoryTokenStore(accessToken: "a"), preferences: Preferences(defaults: defaults), transport: StubTransport([:]))
        #expect(next.user?.isPremium == true)
    }

    @Test func anErrorNeverLetsTheUserThrough() async {
        let (session, transport) = await signedIn(subscription: (500, "boom"))

        #expect(session.phase == .paywall)
        #expect(session.subscription == nil)

        let viewModel = PaywallViewModel(session: session)
        #expect(viewModel.isWaitingForFirstAnswer)
        await viewModel.start()
        #expect(viewModel.errorMessage != nil)
        #expect(viewModel.monthlyPrice == nil)
        #expect(session.phase == .paywall)

        transport.routes["/api/auth/subscription"] = (200, premiumJson)
        await viewModel.check()
        #expect(viewModel.errorMessage == nil)
        #expect(session.phase == .chooseWorkspace)
    }

    @Test func signingOutGoesBackToTheSpaceChoice() async {
        let tokens = InMemoryTokenStore()
        let (session, _) = await signedIn(subscription: (200, freeJson), tokens: tokens)

        await PaywallViewModel(session: session).signOut()

        #expect(tokens.accessToken == nil)
        #expect(session.user == nil)
        #expect(session.spaceType == nil)
        #expect(session.phase == .spaceChoice)
    }

    @Test func relaunchingWithoutSubscriptionShowsThePaywallAgain() async {
        let tokens = InMemoryTokenStore()
        let (first, _) = await signedIn(subscription: (200, freeJson), tokens: tokens)
        #expect(first.phase == .paywall)

        // Closed on the paywall: no workspace was chosen, so the paywall comes back.
        let transport = StubTransport(["/api/auth/subscription": (200, freeJson)])
        let next = AppSession(tokenStore: tokens, preferences: Preferences(defaults: defaults), transport: transport)
        #expect(next.phase == .paywall)
        #expect(transport.paths.isEmpty)

        let viewModel = PaywallViewModel(session: next)
        await viewModel.start()
        #expect(transport.paths == ["/api/auth/subscription"])
        #expect(next.phase == .paywall)

        // Subscribed elsewhere: the next check goes on to the workspaces.
        transport.routes["/api/auth/subscription"] = (200, premiumJson)
        await viewModel.check()
        #expect(next.phase == .chooseWorkspace)
        #expect(next.user?.isPremium == true)
    }

    @Test func aUserWithAWorkspaceOpensWithoutAsking() async {
        let tokens = InMemoryTokenStore()
        _ = await signedIn(subscription: (200, freeJson), tokens: tokens)

        // A workspace is only chosen past the paywall (same rule as the Compose app).
        Preferences(defaults: defaults).setCodable(Workspace(id: "w1", userId: "u1", name: "Team", role: "ADMIN"), for: .selectedWorkspace)
        let transport = StubTransport(["/api/auth/subscription": (200, freeJson)])
        let next = AppSession(tokenStore: tokens, preferences: Preferences(defaults: defaults), transport: transport)
        #expect(next.phase == .ready)
        #expect(transport.paths.isEmpty)
    }

    @Test func aPremiumAnswerWithoutAStoredUserStillGoesOn() async {
        let tokens = InMemoryTokenStore(accessToken: "a", refreshToken: "r")
        let transport = StubTransport(["/api/auth/subscription": (200, premiumJson)])
        let session = AppSession(tokenStore: tokens, preferences: Preferences(defaults: defaults), transport: transport)
        session.chooseOnlineSpace()
        #expect(session.phase == .paywall)

        // The user can't be fetched (404 here), but the backend said premium.
        await PaywallViewModel(session: session).start()
        #expect(session.phase == .chooseWorkspace)
    }
}
