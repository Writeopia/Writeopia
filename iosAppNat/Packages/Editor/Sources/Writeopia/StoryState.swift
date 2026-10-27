import WrModels

/// The state of the document in the editor: every step by its position, which step has the
/// focus and where the cursor is. Mirrors `StoryState` of the Kotlin SDK.
public struct StoryState: Equatable, Sendable {
    public var stories: [String: StoryStep]
    public var lastEdit: LastEdit
    public var focus: String?
    public var selection: Selection

    public init(
        stories: [String: StoryStep],
        lastEdit: LastEdit = .nothing,
        focus: String? = nil,
        selection: Selection? = nil
    ) {
        self.stories = stories
        self.lastEdit = lastEdit
        self.focus = focus
        self.selection = selection ?? Selection(position: focus ?? Rank.none, start: 0, end: 0)
    }

    public static let empty = StoryState(stories: [:])

    /// Positions in document order.
    public var sortedPositions: [String] { stories.keys.sorted() }

    /// Steps in document order.
    public var sortedStories: [StoryStep] { sortedPositions.compactMap { stories[$0] } }

    public subscript(position: String) -> StoryStep? { stories[position] }
}

/// Cursor or selected range inside the step at `position`.
public struct Selection: Equatable, Sendable {
    public var position: String
    public var start: Int
    public var end: Int

    public init(position: String, start: Int, end: Int) {
        self.position = position
        self.start = start
        self.end = end
    }

    public static func cursor(_ cursor: Int, at position: String) -> Selection {
        Selection(position: position, start: cursor, end: cursor)
    }
}

/// How the last edition changed the document. Lets observers react to a single line edit
/// differently from a structural change.
public enum LastEdit: Equatable, Sendable {
    case nothing
    /// Structural change: steps were added, removed or moved.
    case whole
    /// Only the content of one line changed.
    case lineEdition(position: String, storyStep: StoryStep)
}
