import Foundation

/// Whether the signed in user pays for the Writeopia cloud, from `GET /api/auth/subscription`,
/// like `SubscriptionStatus` of the Kotlin models. The cloud account (sync, the cloud AI,
/// teams) needs it; without it the app offers the private (offline) space instead.
public struct SubscriptionStatus: Decodable, Equatable, Sendable {
    public let premium: Bool
    /// "FREE" or "PREMIUM", like `User.tier`.
    public let tier: String?
    /// The price of the monthly subscription in the smallest unit of `currency` over 100
    /// (cents); nil when the backend didn't send it.
    public let monthlyPriceCents: Int?
    /// ISO 4217 code, e.g. "USD".
    public let currency: String?

    enum CodingKeys: String, CodingKey {
        case premium, tier, monthlyPriceCents, currency
    }

    public init(premium: Bool, tier: String? = nil, monthlyPriceCents: Int? = nil, currency: String? = nil) {
        self.premium = premium
        self.tier = tier
        self.monthlyPriceCents = monthlyPriceCents
        self.currency = currency
    }

    /// Unknown keys are ignored and every key but one telling the tier is optional, so a newer
    /// backend can add fields without breaking older apps.
    public init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        tier = try container.decodeIfPresent(String.self, forKey: .tier)
        if let premium = try container.decodeIfPresent(Bool.self, forKey: .premium) {
            self.premium = premium
        } else if let tier {
            premium = tier.uppercased() == "PREMIUM"
        } else {
            throw DecodingError.keyNotFound(
                CodingKeys.premium,
                DecodingError.Context(codingPath: container.codingPath, debugDescription: "Neither premium nor tier")
            )
        }
        monthlyPriceCents = try? container.decodeIfPresent(Int.self, forKey: .monthlyPriceCents)
        currency = try? container.decodeIfPresent(String.self, forKey: .currency)
    }

    /// The monthly price in the currency of the backend, written the way `locale` writes money
    /// (e.g. "$5.00", "5,00 US$"); nil when the price or the currency is missing, so no
    /// wrong number is ever shown.
    public func formattedMonthlyPrice(locale: Locale = .current) -> String? {
        guard let monthlyPriceCents, monthlyPriceCents >= 0,
              let code = currency?.trimmingCharacters(in: .whitespaces).uppercased(),
              code.count == 3, code.allSatisfy({ $0.isASCII && $0.isLetter })
        else { return nil }
        let amount = Decimal(monthlyPriceCents) / 100
        return amount.formatted(.currency(code: code).locale(locale))
    }
}
