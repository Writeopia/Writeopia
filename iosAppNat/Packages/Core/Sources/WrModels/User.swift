import Foundation

public struct User: Codable, Equatable, Hashable, Sendable {
    public let id: String
    public let email: String
    public let name: String

    public init(id: String, email: String, name: String) {
        self.id = id
        self.email = email
        self.name = name
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
