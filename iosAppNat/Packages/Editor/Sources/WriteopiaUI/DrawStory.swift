import Writeopia
import WrModels

/// A step as it is drawn: the document steps plus the spaces between them that work as drop
/// targets. Mirrors `DrawStory` of the Kotlin SDK.
public struct DrawStory: Identifiable, Equatable {
    public let id: String
    public let storyStep: StoryStep
    /// Position of the step. For spaces, the position of the step drawn before them, which is
    /// where a dropped step goes after.
    public let position: Double

    public init(id: String, storyStep: StoryStep, position: Double) {
        self.id = id
        self.storyStep = storyStep
        self.position = position
    }
}

/// Builds the list of drawn steps. Mirrors `StepsModifier` of the Kotlin SDK: a `SPACE` goes
/// before every step (except the title, nothing can be dropped above it), the one under a drag
/// becomes an `ON_DRAG_SPACE`, and a `LAST_SPACE` closes the document.
public enum StepsModifier {
    public static func modify(_ state: StoryState, dragPosition: Double?) -> [DrawStory] {
        var result: [DrawStory] = []
        var previousPosition: Double?

        for position in state.sortedPositions {
            guard let step = state.stories[position] else { continue }

            if let previousPosition, !step.isTitle {
                result.append(space(id: "space-\(step.id)", after: previousPosition, dragPosition: dragPosition, type: .space))
            }

            if StoryTypes.supported.contains(step.type.number) {
                result.append(DrawStory(id: step.id, storyStep: step, position: position))
            }
            previousPosition = position
        }

        if let previousPosition {
            result.append(space(id: "last-space", after: previousPosition, dragPosition: dragPosition, type: .lastSpace))
        }

        return result
    }

    private static func space(id: String, after position: Double, dragPosition: Double?, type: StoryType) -> DrawStory {
        let isTarget = dragPosition == position
        return DrawStory(
            id: id,
            storyStep: StoryStep(id: id, type: isTarget ? .onDragSpace : type, position: position),
            position: position
        )
    }
}
