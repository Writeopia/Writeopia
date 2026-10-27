import Writeopia
import WrModels

/// The kinds of block that span several consecutive steps and are drawn as one: a code block,
/// a box (`HIGH_LIGHT_BLOCK`) or a card (`CARD_BLOCK`).
nonisolated public enum BlockKind: Equatable, Sendable {
    case code
    case box
    case card

    /// The block `step` belongs to, if any. A code block step is always part of a code block,
    /// whatever its tags.
    public init?(of step: StoryStep) {
        if step.type.number == StoryType.codeBlock.number {
            self = .code
        } else if step.hasTag(BlockTag.box.rawValue) {
            self = .box
        } else if step.hasTag(BlockTag.card.rawValue) {
            self = .card
        } else {
            return nil
        }
    }
}

/// Where a step sits in its block, which decides which edges and corners are drawn around it.
/// Mirrors the tag positions of `StepsModifier` of the Kotlin SDK (-1, 0, 1, 2).
nonisolated public enum BlockPosition: Equatable, Sendable {
    case first
    case middle
    case last
    case single

    public var hasTopEdge: Bool { self == .first || self == .single }
    public var hasBottomEdge: Bool { self == .last || self == .single }
}

/// The block a drawn step is part of.
nonisolated public struct BlockInfo: Equatable, Sendable {
    public let kind: BlockKind
    public let position: BlockPosition
    /// The number of the line inside a code block, starting at 1.
    public let lineNumber: Int
    /// The id of the first step of the block: the same for every step of it, so the editor can
    /// paint the block as one piece.
    public let groupId: String

    public init(kind: BlockKind, position: BlockPosition, lineNumber: Int = 1, groupId: String) {
        self.kind = kind
        self.position = position
        self.lineNumber = lineNumber
        self.groupId = groupId
    }
}

/// A step as it is drawn: the document steps plus the spaces between them that work as drop
/// targets. Mirrors `DrawStory` of the Kotlin SDK.
public struct DrawStory: Identifiable, Equatable {
    public let id: String
    public let storyStep: StoryStep
    /// Position of the step. For spaces, the position of the step drawn before them, which is
    /// where a dropped step goes after.
    public let position: String
    /// The block the step is part of, when it's a code block, box or card step.
    public var block: BlockInfo?
    /// For a space: the block of the steps on both of its sides, when they're in the same one.
    /// The space then paints the block too, so the block reads as one piece.
    public var insideBlock: BlockKind?

    public init(
        id: String,
        storyStep: StoryStep,
        position: String,
        block: BlockInfo? = nil,
        insideBlock: BlockKind? = nil
    ) {
        self.id = id
        self.storyStep = storyStep
        self.position = position
        self.block = block
        self.insideBlock = insideBlock
    }
}

/// Builds the list of drawn steps. Mirrors `StepsModifier` of the Kotlin SDK: a `SPACE` goes
/// before every step (except the title, nothing can be dropped above it), the one under a drag
/// becomes an `ON_DRAG_SPACE`, and a `LAST_SPACE` closes the document. Consecutive steps of the
/// same block (code, box, card) get their position in it, and the spaces between them are marked
/// as inside the block.
public enum StepsModifier {
    /// `extraTypes` are the step types the app draws itself (see `CustomStepDrawers`).
    public static func modify(_ state: StoryState, dragPosition: String?, extraTypes: Set<Int> = []) -> [DrawStory] {
        var result: [DrawStory] = []
        var previousPosition: String?

        for position in state.sortedPositions {
            guard let step = state.stories[position] else { continue }

            if let previousPosition, !step.isTitle {
                result.append(space(id: "space-\(step.id)", after: previousPosition, dragPosition: dragPosition, type: .space))
            }

            if StoryTypes.supported.contains(step.type.number) || extraTypes.contains(step.type.number) {
                result.append(DrawStory(id: step.id, storyStep: step, position: position))
            }
            previousPosition = position
        }

        if let previousPosition {
            result.append(space(id: "last-space", after: previousPosition, dragPosition: dragPosition, type: .lastSpace))
        }

        return markBlocks(result)
    }

    private static func space(id: String, after position: String, dragPosition: String?, type: StoryType) -> DrawStory {
        let isTarget = dragPosition == position
        return DrawStory(
            id: id,
            storyStep: StoryStep(id: id, type: isTarget ? .onDragSpace : type, position: position),
            position: position
        )
    }

    private static func isSpace(_ draw: DrawStory) -> Bool {
        [StoryType.space.number, StoryType.onDragSpace.number, StoryType.lastSpace.number]
            .contains(draw.storyStep.type.number)
    }

    /// Gives every code, box or card step its position in its block, and marks the spaces
    /// between two steps of the same block. Like `addPositionToTags` and
    /// `addPositionToCodeBlocks` of the Kotlin SDK: only the steps next to each other (spaces
    /// aside) make a block.
    static func markBlocks(_ draws: [DrawStory]) -> [DrawStory] {
        var marked = draws
        let contentIndices = draws.indices.filter { !isSpace(draws[$0]) }
        var lineNumber = 1
        var groupId = ""

        for (order, index) in contentIndices.enumerated() {
            guard let kind = BlockKind(of: draws[index].storyStep) else {
                lineNumber = 1
                continue
            }

            let previousIndex = order > 0 ? contentIndices[order - 1] : nil
            let nextIndex = order < contentIndices.count - 1 ? contentIndices[order + 1] : nil
            let joinsPrevious = previousIndex.map { BlockKind(of: draws[$0].storyStep) == kind } ?? false
            let joinsNext = nextIndex.map { BlockKind(of: draws[$0].storyStep) == kind } ?? false

            let position: BlockPosition = switch (joinsPrevious, joinsNext) {
            case (false, false): .single
            case (false, true): .first
            case (true, true): .middle
            case (true, false): .last
            }

            // Only the lines of a code block are numbered.
            lineNumber = kind == .code && joinsPrevious ? lineNumber + 1 : 1
            if !joinsPrevious {
                groupId = draws[index].id
            }
            marked[index].block = BlockInfo(kind: kind, position: position, lineNumber: lineNumber, groupId: groupId)

            if joinsPrevious, let previousIndex {
                for spaceIndex in (previousIndex + 1)..<index {
                    marked[spaceIndex].insideBlock = kind
                }
            }
        }

        return marked
    }
}
