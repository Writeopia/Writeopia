import Foundation

/// What a free command to the AI does with the text, decided by the backend (Jev). Mirrors
/// `AiCommandIntent` of the backend.
public enum AiCommandIntent: String, Codable, Sendable {
    /// The answer takes the place of the selected lines.
    case replace = "REPLACE"
    /// The answer is new content, added to the document, which stays as it is.
    case add = "ADD"
    /// The answer is the whole document sent in the request, rewritten. Used with no selection.
    case rewrite = "REWRITE"
}

/// A free command typed by the writer, with the document it is about and the lines selected
/// when it was typed, if any. Mirrors `AiCommandRequest` of the backend.
public struct AiCommandRequest: Encodable, Equatable, Sendable {
    public var command: String
    /// The lines selected when the command was typed. Empty when nothing was selected.
    public var selectedText: String
    public var documentTitle: String
    /// The text of the document, or the part of it the command is about. For a rewrite it is
    /// Markdown, one paragraph per line, with `[[writeopia-block-N]]` lines for the blocks the
    /// AI does not see.
    public var documentText: String
    /// What to generate. Required by the generation endpoint, ignored by the intent one.
    public var intent: AiCommandIntent?
    public var stream: Bool

    public init(
        command: String,
        selectedText: String = "",
        documentTitle: String = "",
        documentText: String = "",
        intent: AiCommandIntent? = nil,
        stream: Bool = false
    ) {
        self.command = command
        self.selectedText = selectedText
        self.documentTitle = documentTitle
        self.documentText = documentText
        self.intent = intent
        self.stream = stream
    }
}

public struct AiCommandIntentResponse: Decodable, Equatable, Sendable {
    public let intent: AiCommandIntent?
    /// The probability, from 0 to 1, that the writer wants the text changed.
    public let replaceProbability: Double?
    public let error: String?

    public init(intent: AiCommandIntent?, replaceProbability: Double? = nil, error: String? = nil) {
        self.intent = intent
        self.replaceProbability = replaceProbability
        self.error = error
    }
}
