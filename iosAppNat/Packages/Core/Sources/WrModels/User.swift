import Foundation

public struct User: Codable, Equatable, Hashable, Sendable {
    public let id: String
    public let email: String
    public let name: String
    /// "FREE" or "PREMIUM", like `Tier` of the Kotlin models, from the account type of the user.
    public let tier: String?

    public init(id: String, email: String, name: String, tier: String? = nil) {
        self.id = id
        self.email = email
        self.name = name
        self.tier = tier
    }

    public var isPremium: Bool { tier?.uppercased() == "PREMIUM" }

    /// "Premium", "Free", or nil when the backend didn't say (older versions don't send it).
    public var planName: String? {
        switch tier?.uppercased() {
        case "PREMIUM": "Premium"
        case "FREE": "Free"
        default: nil
        }
    }
}

/// Where the user keeps their notes. Mirrors the "Choose your space" screen of the Compose app.
public enum SpaceType: String, Codable, Sendable {
    /// Private space: documents live only on this device.
    case offline
    /// Open space: documents are synced with the Writeopia backend.
    case online
}

public enum ColorTheme: String, Codable, CaseIterable, Identifiable, Sendable {
    case light
    case dark
    case system

    public var id: String { rawValue }

    public var title: String {
        switch self {
        case .light: "Light"
        case .dark: "Dark"
        case .system: "System"
        }
    }
}
