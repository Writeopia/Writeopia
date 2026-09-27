import Foundation

public struct Folder: Codable, Identifiable, Equatable, Hashable, Sendable {
    public static let rootId = "root"

    public let id: String
    public var parentId: String
    public var title: String
    public let workspaceId: String
    public var favorite: Bool
    public var itemCount: Int
    public var icon: IconInfo?
    public var createdAt: Date?
    public var lastUpdatedAt: Date?
    /// When this device last sent or received the folder. Only stored locally.
    public var lastSyncedAt: Date?
    /// Deleted on this device and waiting to be deleted on the backend. Only stored locally.
    public var deleted: Bool

    public init(
        id: String,
        parentId: String,
        title: String,
        workspaceId: String,
        favorite: Bool = false,
        itemCount: Int = 0,
        icon: IconInfo? = nil,
        createdAt: Date? = Date(),
        lastUpdatedAt: Date? = Date(),
        lastSyncedAt: Date? = nil,
        deleted: Bool = false
    ) {
        self.id = id
        self.parentId = parentId
        self.title = title
        self.workspaceId = workspaceId
        self.favorite = favorite
        self.itemCount = itemCount
        self.icon = icon
        self.createdAt = createdAt
        self.lastUpdatedAt = lastUpdatedAt
        self.lastSyncedAt = lastSyncedAt
        self.deleted = deleted
    }

    enum CodingKeys: String, CodingKey {
        case id, parentId, title, workspaceId, favorite, itemCount, icon, createdAt, lastUpdatedAt, lastSyncedAt, deleted
    }

    public init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        id = try container.decode(String.self, forKey: .id)
        parentId = try container.decodeIfPresent(String.self, forKey: .parentId) ?? Folder.rootId
        title = try container.decodeIfPresent(String.self, forKey: .title) ?? ""
        workspaceId = try container.decodeIfPresent(String.self, forKey: .workspaceId) ?? ""
        favorite = try container.decodeIfPresent(Bool.self, forKey: .favorite) ?? false
        itemCount = try container.decodeIfPresent(Int.self, forKey: .itemCount) ?? 0
        icon = try container.decodeIfPresent(IconInfo.self, forKey: .icon)
        createdAt = FlexibleDate.decode(container, .createdAt)
        lastUpdatedAt = FlexibleDate.decode(container, .lastUpdatedAt)
        lastSyncedAt = FlexibleDate.decode(container, .lastSyncedAt)
        deleted = try container.decodeIfPresent(Bool.self, forKey: .deleted) ?? false
    }

    /// Dates are written as ISO-8601 strings, the format of `kotlin.time.Instant` in `FolderApi`.
    public func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        try container.encode(id, forKey: .id)
        try container.encode(parentId, forKey: .parentId)
        try container.encode(title, forKey: .title)
        try container.encode(workspaceId, forKey: .workspaceId)
        try container.encode(favorite, forKey: .favorite)
        try container.encode(itemCount, forKey: .itemCount)
        try container.encodeIfPresent(icon, forKey: .icon)
        try container.encodeIfPresent(createdAt.map(FlexibleDate.iso), forKey: .createdAt)
        try container.encodeIfPresent(lastUpdatedAt.map(FlexibleDate.iso), forKey: .lastUpdatedAt)
        try container.encodeIfPresent(lastSyncedAt.map(FlexibleDate.iso), forKey: .lastSyncedAt)
        try container.encode(deleted, forKey: .deleted)
    }

    /// The folder as the backend expects it (`FolderApi`), without the local only fields.
    public var api: FolderApiBody {
        FolderApiBody(
            id: id,
            parentId: parentId,
            title: title,
            createdAt: FlexibleDate.iso(createdAt ?? Date()),
            lastUpdatedAt: FlexibleDate.iso(lastUpdatedAt ?? Date()),
            workspaceId: workspaceId,
            favorite: favorite,
            icon: icon,
            itemCount: itemCount
        )
    }

    public var displayTitle: String { title.isEmpty ? "Untitled folder" : title }
}

public struct WrDocument: Codable, Identifiable, Equatable, Hashable, Sendable {
    public let id: String
    public var title: String
    public var workspaceId: String
    public var content: [StoryStep]
    /// Epoch milliseconds.
    public var createdAt: Int64
    /// Epoch milliseconds.
    public var lastUpdatedAt: Int64
    public var isFavorite: Bool
    public var parentId: String?
    /// Epoch milliseconds of the last sync with the backend; nil for documents never synced.
    public var lastSyncedAt: Int64?
    public var isLocked: Bool
    public var published: Bool
    public var deleted: Bool
    public var icon: IconInfo?

    public init(
        id: String,
        title: String,
        workspaceId: String,
        content: [StoryStep] = [],
        createdAt: Int64 = Date.nowMillis,
        lastUpdatedAt: Int64 = Date.nowMillis,
        isFavorite: Bool = false,
        parentId: String? = nil,
        lastSyncedAt: Int64? = nil,
        isLocked: Bool = false,
        published: Bool = false,
        deleted: Bool = false,
        icon: IconInfo? = nil
    ) {
        self.id = id
        self.title = title
        self.workspaceId = workspaceId
        self.content = content
        self.createdAt = createdAt
        self.lastUpdatedAt = lastUpdatedAt
        self.isFavorite = isFavorite
        self.parentId = parentId
        self.lastSyncedAt = lastSyncedAt
        self.isLocked = isLocked
        self.published = published
        self.deleted = deleted
        self.icon = icon
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
        lastSyncedAt = try container.decodeIfPresent(Int64.self, forKey: .lastSyncedAt)
        isLocked = try container.decodeIfPresent(Bool.self, forKey: .isLocked) ?? false
        published = try container.decodeIfPresent(Bool.self, forKey: .published) ?? false
        deleted = try container.decodeIfPresent(Bool.self, forKey: .deleted) ?? false
        icon = try container.decodeIfPresent(IconInfo.self, forKey: .icon)
    }

    /// Changed on this device since the last sync, so it has to be sent to the backend.
    public var isOutdated: Bool {
        guard let lastSyncedAt else { return true }
        return lastUpdatedAt > lastSyncedAt
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

    /// Plain text preview of the document body. Only steps that hold readable text are used
    /// (a drawing, for instance, keeps JSON in its text).
    public var preview: String {
        let textTypes: Set<Int> = [
            StoryType.text.number, StoryType.checkItem.number,
            StoryType.unorderedListItem.number, StoryType.aiAnswer.number,
        ]
        return bodySteps
            .filter { textTypes.contains($0.type.number) }
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

    var millis: Int64 { Int64(timeIntervalSince1970 * 1000) }

    init(millis: Int64) {
        self.init(timeIntervalSince1970: TimeInterval(millis) / 1000)
    }
}

/// Icon of a folder or document. Mirrors `IconApi`.
public struct IconInfo: Codable, Equatable, Hashable, Sendable {
    public let label: String
    public let tint: Int?

    public init(label: String, tint: Int? = nil) {
        self.label = label
        self.tint = tint
    }
}

/// Folder in the shape of `FolderApi`, for requests that send folders.
public struct FolderApiBody: Encodable, Equatable, Sendable {
    public let id: String
    public let parentId: String
    public let title: String
    public let createdAt: String
    public let lastUpdatedAt: String
    public let workspaceId: String
    public let favorite: Bool
    public let icon: IconInfo?
    public let itemCount: Int
}

/// Dates that arrive as ISO-8601 strings (`kotlin.time.Instant`) or as epoch milliseconds.
enum FlexibleDate {
    static func decode<Key: CodingKey>(_ container: KeyedDecodingContainer<Key>, _ key: Key) -> Date? {
        if let text = try? container.decodeIfPresent(String.self, forKey: key) {
            return parse(text)
        }
        if let millis = try? container.decodeIfPresent(Double.self, forKey: key) {
            return Date(timeIntervalSince1970: millis / 1000)
        }
        return nil
    }

    static func parse(_ text: String) -> Date? {
        withFractional.date(from: text) ?? withoutFractional.date(from: text)
    }

    static func iso(_ date: Date) -> String {
        withFractional.string(from: date)
    }

    nonisolated(unsafe) private static let withFractional: ISO8601DateFormatter = {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        return formatter
    }()

    nonisolated(unsafe) private static let withoutFractional = ISO8601DateFormatter()
}
