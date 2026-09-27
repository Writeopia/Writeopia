import WrModels

/// The step types the editor knows how to draw and edit.
public enum StoryTypes {
    /// Steps that hold editable text and can receive the focus.
    public static let textTypes: Set<Int> = [
        StoryType.title.number,
        StoryType.text.number,
        StoryType.checkItem.number,
        StoryType.unorderedListItem.number,
        StoryType.aiAnswer.number,
        StoryType.codeBlock.number,
    ]

    /// Every type the editor draws. Other types are kept in the document but not shown.
    public static let supported: Set<Int> = textTypes.union([
        StoryType.image.number,
        StoryType.divider.number,
        StoryType.documentLink.number,
        StoryType.loading.number,
        StoryType.space.number,
        StoryType.onDragSpace.number,
        StoryType.lastSpace.number,
    ])

    /// Steps that only exist while editing and are never part of the document.
    public static let ephemeral: Set<Int> = [
        StoryType.loading.number,
        StoryType.space.number,
        StoryType.onDragSpace.number,
        StoryType.lastSpace.number,
    ]
}

public extension StoryStep {
    var isTextStep: Bool { StoryTypes.textTypes.contains(type.number) }
    var isTitle: Bool { type.number == StoryType.title.number }
    /// Steps that go back to a regular paragraph on backspace at the start, or on Return when empty.
    var isListLike: Bool {
        type.number == StoryType.checkItem.number ||
            type.number == StoryType.unorderedListItem.number ||
            type.number == StoryType.codeBlock.number
    }
}

/// Block tags that can be toggled from the selection menu. Mirrors `Tag` of the Kotlin models.
public enum BlockTag: String, CaseIterable, Sendable {
    case box = "HIGH_LIGHT_BLOCK"
    case card = "CARD_BLOCK"
    case h1 = "H1"
    case h2 = "H2"
    case h3 = "H3"
    case h4 = "H4"

    public static let headings: [BlockTag] = [.h1, .h2, .h3, .h4]

    public var isHeading: Bool { Self.headings.contains(self) }
}
