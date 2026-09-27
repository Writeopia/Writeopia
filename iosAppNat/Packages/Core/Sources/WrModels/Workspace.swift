import Foundation

public struct Workspace: Codable, Identifiable, Equatable, Hashable, Sendable {
    public static let localId = "local"

    public let id: String
    public let userId: String
    public let name: String
    public let role: String
    public let documentCount: Int

    public init(id: String, userId: String, name: String, role: String, documentCount: Int = 0) {
        self.id = id
        self.userId = userId
        self.name = name
        self.role = role
        self.documentCount = documentCount
    }

    public init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        id = try container.decode(String.self, forKey: .id)
        userId = try container.decodeIfPresent(String.self, forKey: .userId) ?? ""
        name = try container.decode(String.self, forKey: .name)
        role = try container.decodeIfPresent(String.self, forKey: .role) ?? ""
        documentCount = try container.decodeIfPresent(Int.self, forKey: .documentCount) ?? 0
    }

    public var isAdmin: Bool { role.uppercased() == "ADMIN" }

    /// The workspace used by the private (offline) space.
    public static let local = Workspace(id: localId, userId: "", name: String(localized: "Private space"), role: "ADMIN")
}

public struct WorkspaceUser: Codable, Identifiable, Equatable, Hashable, Sendable {
    public let id: String
    public let email: String
    public let name: String
    public let role: String

    public init(id: String, email: String, name: String, role: String) {
        self.id = id
        self.email = email
        self.name = name
        self.role = role
    }

    public var isAdmin: Bool { role.uppercased() == "ADMIN" }
}

public enum WorkspaceRole: String, CaseIterable, Identifiable, Sendable {
    case admin = "ADMIN"
    case user = "USER"

    public var id: String { rawValue }

    public var title: String {
        switch self {
        case .admin: String(localized: "Admin")
        case .user: String(localized: "Member")
        }
    }
}
