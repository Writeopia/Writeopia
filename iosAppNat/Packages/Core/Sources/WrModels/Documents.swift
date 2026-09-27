import Foundation

public struct Folder: Codable, Identifiable, Equatable, Hashable, Sendable {
    public static let rootId = "root"

    public let id: String
    public var parentId: String
    public var title: String
    public let workspaceId: String
    public var favorite: Bool
    public var itemCount: Int

    public init(
        id: String,
        parentId: String,
        title: String,
        workspaceId: String,
        favorite: Bool = false,
        itemCount: Int = 0
    ) {
        self.id = id
        self.parentId = parentId
        self.title = title
        self.workspaceId = workspaceId
        self.favorite = favorite
        self.itemCount = itemCount
    }

    public init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        id = try container.decode(String.self, forKey: .id)
        parentId = try container.decodeIfPresent(String.self, forKey: .parentId) ?? Folder.rootId
        title = try container.decodeIfPresent(String.self, forKey: .title) ?? ""
        workspaceId = try container.decodeIfPresent(String.self, forKey: .workspaceId) ?? ""
        favorite = try container.decodeIfPresent(Bool.self, forKey: .favorite) ?? false
        itemCount = try container.decodeIfPresent(Int.self, forKey: .itemCount) ?? 0
    }

    public var displayTitle: String { title.isEmpty ? "Untitled folder" : title }
}

public struct WrDocument: Codable, Identifiable, Equatable, Hashable, Sendable {
    public let id: String
    public var title: String
    public let workspaceId: String
    public var content: [StoryStep]
    /// Epoch milliseconds.
    public var createdAt: Int64
    /// Epoch milliseconds.
    public var lastUpdatedAt: Int64
    public var isFavorite: Bool
    public var parentId: String?

    public init(
        id: String,
        title: String,
        workspaceId: String,
        content: [StoryStep] = [],
        createdAt: Int64 = Date.nowMillis,
        lastUpdatedAt: Int64 = Date.nowMillis,
        isFavorite: Bool = false,
        parentId: String? = nil
    ) {
        self.id = id
        self.title = title
        self.workspaceId = workspaceId
        self.content = content
        self.createdAt = createdAt
        self.lastUpdatedAt = lastUpdatedAt
        self.isFavorite = isFavorite
        self.parentId = parentId
    }

    public init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        id = try container.decode(String.self, forKey: .id)
        title = try container.decodeIfPresent(String.self, forKey: .title) ?? ""
        workspaceId = try container.decodeIfPresent(String.self, forKey: .workspaceId) ?? ""
        content = try container.decodeIfPresent([StoryStep].self, forKey: .content) ?? []
        createdAt = try container.decodeIfPresent(Int64.self, forKey: .createdAt) ?? 0
        lastUpdatedAt = try container.decodeIfPresent(Int64.self, forKey: .lastUpdatedAt) ?? 0
        isFavorite = try container.decodeIfPresent(Bool.self, forKey: .isFavorite) ?? false
        parentId = try container.decodeIfPresent(String.self, forKey: .parentId)
    }

    public var displayTitle: String { title.isEmpty ? "Untitled" : title }

    public var lastUpdatedDate: Date {
        Date(timeIntervalSince1970: TimeInterval(lastUpdatedAt) / 1000)
    }

    /// Steps sorted by position, without the title step (which is shown as the navigation title).
    public var bodySteps: [StoryStep] {
        content
            .sorted { $0.position < $1.position }
            .filter { $0.type.number != StoryType.title.number }
    }

    /// Plain text preview of the document body.
    public var preview: String {
        bodySteps
            .compactMap { $0.text?.trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { !$0.isEmpty }
            .prefix(3)
            .joined(separator: " ")
    }
}

public struct FolderContents: Codable, Equatable, Sendable {
    public var folders: [Folder]
    public var documents: [WrDocument]

    public init(folders: [Folder] = [], documents: [WrDocument] = []) {
        self.folders = folders
        self.documents = documents
    }

    public init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        folders = try container.decodeIfPresent([Folder].self, forKey: .folders) ?? []
        documents = try container.decodeIfPresent([WrDocument].self, forKey: .documents) ?? []
    }

    public var isEmpty: Bool { folders.isEmpty && documents.isEmpty }
}

public extension Date {
    static var nowMillis: Int64 { Int64(Date().timeIntervalSince1970 * 1000) }
}
