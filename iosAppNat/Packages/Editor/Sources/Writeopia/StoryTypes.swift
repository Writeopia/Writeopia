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
    ]

    /// Every type the editor draws. Other types are kept in the document but not shown.
    public static let supported: Set<Int> = textTypes.union([
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
    var isListLike: Bool {
        type.number == StoryType.checkItem.number || type.number == StoryType.unorderedListItem.number
    }
}
