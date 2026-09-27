import Foundation

/// A block of content inside a document. See `CLAUDE_CODE_WRITEOPIA_MANUAL.md` for the format.
public struct StoryStep: Codable, Identifiable, Equatable, Hashable, Sendable {
    public let id: String
    public var type: StoryType
    public var text: String?
    public var checked: Bool?
    public var url: String?
    public var path: String?
    public var steps: [StoryStep]
    public var tags: [TagInfo]
    public var spans: [SpanInfo]
    public var position: Double
    public var documentLink: DocumentLink?
    /// Group this step belongs to, if any.
    public var parentId: String?
    public var decoration: Decoration?
    /// Epoch milliseconds of the last change of this step, used to merge copies of a document.
    public var lastUpdatedAt: Int64?

    public init(
        id: String = UUID().uuidString,
        type: StoryType,
        text: String? = nil,
        checked: Bool? = nil,
        url: String? = nil,
        path: String? = nil,
        steps: [StoryStep] = [],
        tags: [TagInfo] = [],
        spans: [SpanInfo] = [],
        position: Double,
        documentLink: DocumentLink? = nil,
        parentId: String? = nil,
        decoration: Decoration? = nil,
        lastUpdatedAt: Int64? = nil
    ) {
        self.id = id
        self.type = type
        self.text = text
        self.checked = checked
        self.url = url
        self.path = path
        self.steps = steps
        self.tags = tags
        self.spans = spans
        self.position = position
        self.documentLink = documentLink
        self.parentId = parentId
        self.decoration = decoration
        self.lastUpdatedAt = lastUpdatedAt
    }

    public init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        id = try container.decodeIfPresent(String.self, forKey: .id) ?? UUID().uuidString
        type = try container.decodeIfPresent(StoryType.self, forKey: .type) ?? .message
        text = try container.decodeIfPresent(String.self, forKey: .text)
        checked = try container.decodeIfPresent(Bool.self, forKey: .checked)
        url = try container.decodeIfPresent(String.self, forKey: .url)
        path = try container.decodeIfPresent(String.self, forKey: .path)
        steps = try container.decodeIfPresent([StoryStep].self, forKey: .steps) ?? []
        tags = try container.decodeIfPresent([TagInfo].self, forKey: .tags) ?? []
        spans = try container.decodeIfPresent([SpanInfo].self, forKey: .spans) ?? []
        position = try container.decodeIfPresent(Double.self, forKey: .position) ?? 0
        documentLink = try container.decodeIfPresent(DocumentLink.self, forKey: .documentLink)
        parentId = try container.decodeIfPresent(String.self, forKey: .parentId)
        decoration = try container.decodeIfPresent(Decoration.self, forKey: .decoration)
        lastUpdatedAt = try container.decodeIfPresent(Int64.self, forKey: .lastUpdatedAt)
    }

    /// Same content, ignoring the position and the timestamp. Used to find the steps changed by
    /// an edit.
    public func hasSameContent(as other: StoryStep) -> Bool {
        var this = self
        var that = other
        this.position = 0
        that.position = 0
        this.lastUpdatedAt = nil
        that.lastUpdatedAt = nil
        return this == that
    }

    public func hasTag(_ tag: String) -> Bool {
        tags.contains { $0.tag == tag }
    }

    /// Heading level (1...4) when the step is tagged as a heading.
    public var headingLevel: Int? {
        for level in 1...4 where hasTag("H\(level)") {
            return level
        }
        return nil
    }
}

public struct StoryType: Codable, Equatable, Hashable, Sendable {
    public let name: String
    public let number: Int

    public init(name: String, number: Int) {
        self.name = name
        self.number = number
    }

    public static let message = StoryType(name: "message", number: 0)
    /// Same as `message`, named like `StoryTypes.TEXT` in the SDK.
    public static let text = message
    public static let image = StoryType(name: "image", number: 2)
    public static let space = StoryType(name: "space", number: 7)
    public static let lastSpace = StoryType(name: "last_space", number: 8)
    public static let checkItem = StoryType(name: "check_item", number: 10)
    public static let title = StoryType(name: "title", number: 11)
    public static let unorderedListItem = StoryType(name: "unordered_list_item", number: 16)
    public static let onDragSpace = StoryType(name: "on_drag_space", number: 17)
    public static let aiAnswer = StoryType(name: "ai_answer", number: 18)
    public static let loading = StoryType(name: "loading", number: 19)
    public static let documentLink = StoryType(name: "document_link", number: 20)
    public static let divider = StoryType(name: "divider", number: 21)
    public static let codeBlock = StoryType(name: "code_block", number: 23)
    /// Free drawing; the strokes are stored as JSON in the text of the step.
    public static let drawing = StoryType(name: "drawing", number: 100)
}

public struct TagInfo: Codable, Equatable, Hashable, Sendable {
    public let tag: String
    public let position: Int

    public init(tag: String, position: Int = 0) {
        self.tag = tag
        self.position = position
    }
}

public struct SpanInfo: Codable, Equatable, Hashable, Sendable {
    public let start: Int
    public let end: Int
    public let span: String
    public let extra: String?

    public init(start: Int, end: Int, span: String, extra: String? = nil) {
        self.start = start
        self.end = end
        self.span = span
        self.extra = extra
    }
}

public struct Decoration: Codable, Equatable, Hashable, Sendable {
    public let backgroundColor: Int?

    public init(backgroundColor: Int? = nil) {
        self.backgroundColor = backgroundColor
    }
}

public struct DocumentLink: Codable, Equatable, Hashable, Sendable {
    public let id: String
    public let title: String?

    public init(id: String, title: String? = nil) {
        self.id = id
        self.title = title
    }
}
