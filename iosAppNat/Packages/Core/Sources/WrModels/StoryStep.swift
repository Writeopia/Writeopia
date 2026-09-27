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
        documentLink: DocumentLink? = nil
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
    public static let image = StoryType(name: "image", number: 2)
    public static let space = StoryType(name: "space", number: 7)
    public static let checkItem = StoryType(name: "check_item", number: 10)
    public static let title = StoryType(name: "title", number: 11)
    public static let unorderedListItem = StoryType(name: "unordered_list_item", number: 16)
    public static let documentLink = StoryType(name: "document_link", number: 20)
    public static let divider = StoryType(name: "divider", number: 21)
    public static let codeBlock = StoryType(name: "code_block", number: 23)
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

public struct DocumentLink: Codable, Equatable, Hashable, Sendable {
    public let id: String
    public let title: String?

    public init(id: String, title: String? = nil) {
        self.id = id
        self.title = title
    }
}
