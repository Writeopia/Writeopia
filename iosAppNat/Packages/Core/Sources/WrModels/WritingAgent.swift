import Foundation

/// What the writing agent can offer. Mirrors `WritingSuggestionAction` of the Kotlin app; the
/// raw values are the names the backend sends.
public enum WritingSuggestionAction: String, Codable, CaseIterable, Sendable {
    case codeBlock = "CODE_BLOCK"
    case list = "LIST"
    case checkList = "CHECK_LIST"
    case newDocumentLink = "NEW_DOCUMENT_LINK"
    case image = "IMAGE"
    case drawing = "DRAWING"
    case spreadsheet = "SPREADSHEET"
    case sectionHeading = "SECTION_HEADING"
    case documentTitle = "DOCUMENT_TITLE"
    case callout = "CALLOUT"
    case tldr = "TLDR"
    case conclusion = "CONCLUSION"

    /// Decided by the app from the document itself, never asked to the backend.
    public static let decidedLocally: [WritingSuggestionAction] = [.documentTitle]
}

/// When the backend is asked: about the paragraph being written, or about a document that opened.
public enum WritingSuggestionScope: String, Codable, Sendable {
    case writing = "WRITING"
    case documentOpened = "DOCUMENT_OPENED"
}

public struct WritingSuggestionsRequest: Encodable, Equatable, Sendable {
    public let scope: WritingSuggestionScope
    public let text: String
    public let blockType: String?
    public let documentTitle: String?
    public let documentText: String?

    public init(
        scope: WritingSuggestionScope,
        text: String = "",
        blockType: String? = nil,
        documentTitle: String? = nil,
        documentText: String? = nil
    ) {
        self.scope = scope
        self.text = text
        self.blockType = blockType
        self.documentTitle = documentTitle
        self.documentText = documentText
    }
}

/// One action the backend is confident about. An action this app doesn't know is kept as nil,
/// so a newer backend doesn't break the decoding.
public struct WritingSuggestionDto: Decodable, Equatable, Sendable {
    public let action: WritingSuggestionAction?
    public let probability: Double

    public init(action: WritingSuggestionAction?, probability: Double) {
        self.action = action
        self.probability = probability
    }

    enum CodingKeys: String, CodingKey {
        case action, probability
    }

    public init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        action = WritingSuggestionAction(rawValue: try container.decode(String.self, forKey: .action))
        probability = try container.decodeIfPresent(Double.self, forKey: .probability) ?? 0
    }
}

public struct WritingSuggestionsResponse: Decodable, Equatable, Sendable {
    public let suggestions: [WritingSuggestionDto]
    public let error: String?

    public init(suggestions: [WritingSuggestionDto] = [], error: String? = nil) {
        self.suggestions = suggestions
        self.error = error
    }

    enum CodingKeys: String, CodingKey {
        case suggestions, error
    }

    public init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        suggestions = try container.decodeIfPresent([WritingSuggestionDto].self, forKey: .suggestions) ?? []
        error = try container.decodeIfPresent(String.self, forKey: .error)
    }
}

// MARK: - Folders

/// A folder the document could move to. The name is what the backend reads; the id is for the app.
public struct FolderCandidate: Codable, Equatable, Sendable {
    public let id: String
    public let name: String

    public init(id: String, name: String) {
        self.id = id
        self.name = name
    }
}

/// A document that sits in the root folder, and the folders of the workspace it could belong to.
public struct FolderSuggestionsRequest: Encodable, Equatable, Sendable {
    /// How many folders are asked about at most; the first ones sent are kept.
    public static let maxFolders = 10

    public let documentTitle: String
    public let documentText: String
    public let folders: [FolderCandidate]

    public init(documentTitle: String = "", documentText: String = "", folders: [FolderCandidate] = []) {
        self.documentTitle = documentTitle
        self.documentText = documentText
        self.folders = folders
    }
}

public struct FolderSuggestionDto: Decodable, Equatable, Sendable {
    public let folderId: String
    public let probability: Double

    public init(folderId: String, probability: Double) {
        self.folderId = folderId
        self.probability = probability
    }
}

public struct FolderSuggestionsResponse: Decodable, Equatable, Sendable {
    /// The folders the document belongs to, most likely first. Empty when none fits.
    public let suggestions: [FolderSuggestionDto]
    public let error: String?

    public init(suggestions: [FolderSuggestionDto] = [], error: String? = nil) {
        self.suggestions = suggestions
        self.error = error
    }

    enum CodingKeys: String, CodingKey {
        case suggestions, error
    }

    public init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        suggestions = try container.decodeIfPresent([FolderSuggestionDto].self, forKey: .suggestions) ?? []
        error = try container.decodeIfPresent(String.self, forKey: .error)
    }
}

// MARK: - Tags

/// A tag the document could carry. The name and the explanation are what the backend reads; the
/// id is for the app.
public struct TagCandidate: Codable, Equatable, Sendable {
    public let id: String
    public let name: String
    public let description: String

    public init(id: String, name: String, description: String = "") {
        self.id = id
        self.name = name
        self.description = description
    }
}

/// A document without tags, and the tags of the workspace catalogue it could carry.
public struct TagSuggestionsRequest: Encodable, Equatable, Sendable {
    /// How many tags are asked about at most; the first ones sent are kept.
    public static let maxTags = 30

    public let documentTitle: String
    public let documentText: String
    public let tags: [TagCandidate]

    public init(documentTitle: String = "", documentText: String = "", tags: [TagCandidate] = []) {
        self.documentTitle = documentTitle
        self.documentText = documentText
        self.tags = tags
    }
}

public struct TagSuggestionDto: Decodable, Equatable, Sendable {
    public let tagId: String
    public let probability: Double

    public init(tagId: String, probability: Double) {
        self.tagId = tagId
        self.probability = probability
    }
}

public struct TagSuggestionsResponse: Decodable, Equatable, Sendable {
    /// The tags the document should carry, most likely first. Empty when none fits.
    public let suggestions: [TagSuggestionDto]
    public let error: String?

    public init(suggestions: [TagSuggestionDto] = [], error: String? = nil) {
        self.suggestions = suggestions
        self.error = error
    }

    enum CodingKeys: String, CodingKey {
        case suggestions, error
    }

    public init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        suggestions = try container.decodeIfPresent([TagSuggestionDto].self, forKey: .suggestions) ?? []
        error = try container.decodeIfPresent(String.self, forKey: .error)
    }
}

/// A document for which the cloud AI makes up a new tag, avoiding the names the catalogue has.
public struct TagCreateRequest: Encodable, Equatable, Sendable {
    public let documentTitle: String
    public let documentText: String
    public let existingTagNames: [String]

    public init(documentTitle: String = "", documentText: String = "", existingTagNames: [String] = []) {
        self.documentTitle = documentTitle
        self.documentText = documentText
        self.existingTagNames = existingTagNames
    }
}

public struct TagCreateResponse: Decodable, Equatable, Sendable {
    public let name: String
    public let description: String
    public let error: String?

    public init(name: String = "", description: String = "", error: String? = nil) {
        self.name = name
        self.description = description
        self.error = error
    }

    enum CodingKeys: String, CodingKey {
        case name, description, error
    }

    public init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        name = try container.decodeIfPresent(String.self, forKey: .name) ?? ""
        description = try container.decodeIfPresent(String.self, forKey: .description) ?? ""
        error = try container.decodeIfPresent(String.self, forKey: .error)
    }
}

// MARK: - Images

/// What the backend needs to illustrate a paragraph: the paragraph, and the whole document around
/// it, so the picture matches the text and not only one sentence of it.
public struct AiImageRequest: Encodable, Equatable, Sendable {
    public let paragraph: String
    public let documentTitle: String
    public let documentText: String

    public init(paragraph: String, documentTitle: String = "", documentText: String = "") {
        self.paragraph = paragraph
        self.documentTitle = documentTitle
        self.documentText = documentText
    }
}

/// The generated image, stored with the user's media, or why there is none.
public struct AiImageResponse: Decodable, Equatable, Sendable {
    public let imageUrl: String?
    public let error: String?

    public init(imageUrl: String? = nil, error: String? = nil) {
        self.imageUrl = imageUrl
        self.error = error
    }
}
