import Foundation
import WrModels

public struct WritingSuggestion: Equatable, Identifiable, Sendable {
    public let action: WritingSuggestionAction
    public let probability: Double

    public var id: String { action.rawValue }

    public init(action: WritingSuggestionAction, probability: Double) {
        self.action = action
        self.probability = probability
    }
}

/// A folder the document belongs to, by the backend's judgment.
public struct FolderSuggestion: Equatable, Identifiable, Sendable {
    public let folderId: String
    public let name: String
    public let probability: Double

    public var id: String { folderId }

    public init(folderId: String, name: String, probability: Double) {
        self.folderId = folderId
        self.name = name
        self.probability = probability
    }
}

/// The dialog that files a document sitting in the root folder, opened from the box. It shows a
/// spinner while the backend judges the folders, then the ones that fit to pick from, and the
/// option of a new folder named by the AI.
public struct FolderDialogState: Equatable, Sendable {
    /// True while the backend judges the folders.
    public var isLoading = false
    /// The folders above the threshold, best first. Empty when none fits, the workspace has
    /// none, or the backend failed.
    public var options: [FolderSuggestion] = []
    /// True while the AI names and creates the new folder.
    public var isCreating = false

    public init(isLoading: Bool = false, options: [FolderSuggestion] = [], isCreating: Bool = false) {
        self.isLoading = isLoading
        self.options = options
        self.isCreating = isCreating
    }
}

/// A tag of the workspace catalogue the document should carry, by the backend's judgment.
public struct TagSuggestion: Equatable, Identifiable, Sendable {
    public let tagId: String
    public let name: String
    public let description: String
    public let probability: Double

    public var id: String { tagId }

    public init(tagId: String, name: String, description: String, probability: Double) {
        self.tagId = tagId
        self.name = name
        self.description = description
        self.probability = probability
    }
}

/// The dialog that tags a document without tags, opened from the box. It shows a spinner while the
/// backend judges the catalogue, then the tags that fit, selected, to add at once, and the option
/// of a new tag made up by the AI.
public struct TagDialogState: Equatable, Sendable {
    /// True while the backend judges the catalogue.
    public var isLoading = false
    /// The tags above the threshold, best first. Empty when none fits, the catalogue is empty, or the backend failed.
    public var options: [TagSuggestion] = []
    /// The ids of the options the user keeps selected; all of them to start with.
    public var selected: Set<String> = []
    /// True while the AI makes up and adds the new tag.
    public var isCreating = false
    /// True when the workspace has no tag to offer at all: the dialog only offers a new one.
    public var catalogueIsEmpty = false

    public init(
        isLoading: Bool = false,
        options: [TagSuggestion] = [],
        selected: Set<String> = [],
        isCreating: Bool = false,
        catalogueIsEmpty: Bool = false
    ) {
        self.isLoading = isLoading
        self.options = options
        self.selected = selected
        self.isCreating = isCreating
        self.catalogueIsEmpty = catalogueIsEmpty
    }
}

/// What the writing agent offers right now. `stepId` is the block the suggestions are about: the
/// paragraph being written, or the title when they are about the whole document. The id is kept
/// instead of the position because the actions insert blocks, which moves every position.
public struct WritingSuggestionsState: Equatable, Sendable {
    public var stepId: String?
    public var scope: WritingSuggestionScope?
    public var suggestions: [WritingSuggestion] = []
    public var isLoading = false
    /// True when the box offers to move the document, which sits in the root, into a folder.
    public var offerFolder = false
    /// The dialog filing the document, open after the user took the folder offer; nil otherwise.
    public var folderDialog: FolderDialogState?
    /// True when the box offers to tag the document, which has no tags yet.
    public var offerTags = false
    /// The dialog tagging the document, open after the user took the tag offer; nil otherwise.
    public var tagDialog: TagDialogState?

    public init(
        stepId: String? = nil,
        scope: WritingSuggestionScope? = nil,
        suggestions: [WritingSuggestion] = [],
        isLoading: Bool = false,
        offerFolder: Bool = false,
        folderDialog: FolderDialogState? = nil,
        offerTags: Bool = false,
        tagDialog: TagDialogState? = nil
    ) {
        self.stepId = stepId
        self.scope = scope
        self.suggestions = suggestions
        self.isLoading = isLoading
        self.offerFolder = offerFolder
        self.folderDialog = folderDialog
        self.offerTags = offerTags
        self.tagDialog = tagDialog
    }

    public static let empty = WritingSuggestionsState()

    /// True when the box has something to show. The dialogs are shown apart from the box.
    public var hasSuggestions: Bool { !suggestions.isEmpty || offerFolder || offerTags }

    /// How many rows the box offers: the badge of the collapsed box shows this.
    public var count: Int { suggestions.count + (offerFolder ? 1 : 0) + (offerTags ? 1 : 0) }
}

/// Counts the sentence ends of a text (".", "!", "?" and ":"): the agent asks again when the
/// count changes. The colon counts because it announces what comes next (a list, a snippet, a
/// quote), which is exactly when a suggestion helps.
public enum SentenceEnds {
    public static func count(_ text: String) -> Int {
        text.reduce(into: 0) { count, character in
            if character == "." || character == "!" || character == "?" || character == ":" { count += 1 }
        }
    }
}

/// The code of the first fenced block of an AI answer, without the fence and the language, or
/// the whole answer when it has no fence. A block closes only on a line that is a fence of the
/// same character as the opener (backticks or tildes), at least as long, with nothing but
/// whitespace after it, like Markdown says; backticks inside a line of code stay code.
public enum MarkdownCode {
    private struct Fence {
        let character: Character
        let length: Int

        init?(opening line: String) {
            let trimmed = line.drop { $0 == " " || $0 == "\t" }
            guard let first = trimmed.first, first == "`" || first == "~" else { return nil }
            let length = trimmed.prefix { $0 == first }.count
            guard length >= 3 else { return nil }
            character = first
            self.length = length
        }

        func closes(_ line: String) -> Bool {
            let trimmed = line.trimmingCharacters(in: .whitespaces)
            return trimmed.count >= length && trimmed.allSatisfy { $0 == character }
        }
    }

    public static func parse(_ markdown: String) -> String {
        let lines = markdown.components(separatedBy: .newlines)
        guard let open = lines.firstIndex(where: { Fence(opening: $0) != nil }), let fence = Fence(opening: lines[open]) else {
            return markdown.trimmingCharacters(in: .whitespacesAndNewlines)
        }
        let rest = lines[(open + 1)...]
        let close = rest.firstIndex(where: fence.closes) ?? rest.endIndex
        return rest[..<close].joined(separator: "\n").trimmingCharacters(in: .newlines)
    }
}
