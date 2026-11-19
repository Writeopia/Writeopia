import Foundation
import Testing
import WrData
import WrModels
@testable import WrNetwork
import WrStorage

@Suite struct SubscriptionTests {
    private let baseURL = URL(string: "https://example.com")!

    @Test func theStatusIsReadFromTheSubscriptionRoute() async throws {
        let transport = StubTransport([{ _ in
            (200, #"{"premium":false,"tier":"FREE","monthlyPriceCents":500,"currency":"USD","trialDays":7}"#)
        }])
        let tokens = InMemoryTokenStore(accessToken: "abc")
        let api = AuthAPI(client: APIClient(transport: transport, tokenStore: tokens, baseURL: baseURL), tokenStore: tokens)

        let status = try await api.subscription()

        #expect(status == SubscriptionStatus(premium: false, tier: "FREE", monthlyPriceCents: 500, currency: "USD"))
        #expect(transport.requests[0].url?.path() == "/api/auth/subscription")
        #expect(transport.requests[0].httpMethod == "GET")
        #expect(transport.requests[0].value(forHTTPHeaderField: "Authorization") == "Bearer abc")
    }

    @Test func thePriceAndTheCurrencyAreOptional() throws {
        let status = try JSONDecoder().decode(SubscriptionStatus.self, from: Data(#"{"premium":true}"#.utf8))

        #expect(status.premium)
        #expect(status.formattedMonthlyPrice() == nil)
    }

    @Test func theTierAloneTellsPremium() throws {
        let status = try JSONDecoder().decode(SubscriptionStatus.self, from: Data(#"{"tier":"PREMIUM"}"#.utf8))
        #expect(status.premium)

        #expect(throws: DecodingError.self) {
            try JSONDecoder().decode(SubscriptionStatus.self, from: Data(#"{"currency":"USD"}"#.utf8))
        }
    }

    @Test func thePriceIsWrittenInTheLocaleOfTheUser() {
        let status = SubscriptionStatus(premium: false, tier: "FREE", monthlyPriceCents: 500, currency: "USD")

        #expect(status.formattedMonthlyPrice(locale: Locale(identifier: "en_US")) == "$5.00")
        let brazilian = status.formattedMonthlyPrice(locale: Locale(identifier: "pt_BR"))
        #expect(brazilian?.contains("5,00") == true)
        #expect(brazilian?.contains("US$") == true)

        let euros = SubscriptionStatus(premium: false, monthlyPriceCents: 1299, currency: "eur")
        #expect(euros.formattedMonthlyPrice(locale: Locale(identifier: "de_DE"))?.contains("12,99") == true)
    }

    @Test func aBadCurrencyShowsNoPrice() {
        #expect(SubscriptionStatus(premium: false, monthlyPriceCents: 500, currency: nil).formattedMonthlyPrice() == nil)
        #expect(SubscriptionStatus(premium: false, monthlyPriceCents: 500, currency: "DOLLAR").formattedMonthlyPrice() == nil)
        #expect(SubscriptionStatus(premium: false, monthlyPriceCents: -1, currency: "USD").formattedMonthlyPrice() == nil)
    }
}
