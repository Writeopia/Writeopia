import WrModels

/// The state of the document in the editor: every step by its position, which step has the
/// focus and where the cursor is. Mirrors `StoryState` of the Kotlin SDK.
public struct StoryState: Equatable, Sendable {
    public var stories: [Int: StoryStep]
    public var lastEdit: LastEdit
    public var focus: Int?
    public var selection: Selection

    public init(
        stories: [Int: StoryStep],
        lastEdit: LastEdit = .nothing,
        focus: Int? = nil,
        selection: Selection? = nil
    ) {
        self.stories = stories
        self.lastEdit = lastEdit
        self.focus = focus
        self.selection = selection ?? Selection(position: focus ?? 0, start: 0, end: 0)
    }

    public static let empty = StoryState(stories: [:])

    /// Positions in document order.
    public var sortedPositions: [Int] { stories.keys.sorted() }

    /// Steps in document order.
    public var sortedStories: [StoryStep] { sortedPositions.compactMap { stories[$0] } }

    public subscript(position: Int) -> StoryStep? { stories[position] }
}

/// Cursor or selected range inside the step at `position`.
public struct Selection: Equatable, Sendable {
    public var position: Int
    public var start: Int
    public var end: Int

    public init(position: Int, start: Int, end: Int) {
        self.position = position
        self.start = start
        self.end = end
    }

    public static func cursor(_ cursor: Int, at position: Int) -> Selection {
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
    case lineEdition(position: Int, storyStep: StoryStep)
}
