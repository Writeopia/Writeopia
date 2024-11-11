package io.writeopia.sdk.manager

import io.writeopia.sdk.model.action.Action
import io.writeopia.sdk.model.document.DocumentInfo
import io.writeopia.sdk.model.story.LastEdit
import io.writeopia.sdk.model.story.StoryState
import io.writeopia.sdk.models.command.CommandInfo
import io.writeopia.sdk.models.command.TypeInfo
import io.writeopia.sdk.models.id.GenerateId
import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.sdk.models.story.StoryTypes
import io.writeopia.sdk.normalization.builder.StepsMapNormalizationBuilder
import io.writeopia.sdk.utils.alias.UnitsNormalizationMap
import io.writeopia.sdk.utils.extensions.toEditState
import io.writeopia.sdk.utils.iterables.normalizePositions
import kotlinx.datetime.Clock

class WriteopiaManager(
    private val stepsNormalizer: UnitsNormalizationMap =
        StepsMapNormalizationBuilder.reduceNormalizations {
            defaultNormalizers()
        },
    private val movementHandler: MovementHandler = MovementHandler(),
    private val contentHandler: ContentHandler = ContentHandler(
        stepsNormalizer = stepsNormalizer
    ),
    private val focusHandler: FocusHandler = FocusHandler(),
) {

    fun newStory(
        documentId: String = GenerateId.generate(),
        title: String = "",
        parentFolder: String = "root"
    ): Pair<DocumentInfo, StoryState> {
        val firstMessage = StoryStep(
            localId = GenerateId.generate(),
            type = StoryTypes.TITLE.type
        )
        val stories: Map<Int, StoryStep> = mapOf(0 to firstMessage)
        val normalized = stepsNormalizer(stories.toEditState())

        val now = Clock.System.now()

        val info = DocumentInfo(
            id = documentId,
            title = title,
            createdAt = now,
            lastUpdatedAt = now,
            parentId = parentFolder
        )

        val state = StoryState(
            normalized + normalized,
            LastEdit.Nothing,
            focus = 0
        )

        return info to state
    }

    /**
     * Moves the focus to the next available [StoryStep] if it can't find a step to focus, it
     * creates a new [StoryStep] at the end of the document.
     *
     * @param position Int
     * @param storyState [StoryState]
     */
    fun nextFocusOrCreate(position: Int, storyState: StoryState): StoryState {
        val storyMap = storyState.stories
        val nextFocus = focusHandler.findNextFocus(position, storyMap)
        return if (nextFocus != null) {
            val (nextPosition, storyStep) = nextFocus
            val mutable = storyMap.toMutableMap()
            mutable[nextPosition] = storyStep.copy(localId = GenerateId.generate())

            storyState.copy(stories = mutable, focus = nextPosition)
        } else {
            storyState
        }
    }

    /**
     * Merges two [StoryStep] into a group. This can be used to merge two images into a message
     * group or any other kind of group.
     *
     * @param info [Action.Merge]
     */
    fun mergeRequest(info: Action.Merge, storyState: StoryState): StoryState {
        val movedStories = movementHandler.merge(storyState.stories, info)
        return StoryState(
            stories = stepsNormalizer(movedStories).normalizePositions(),
            lastEdit = LastEdit.Whole
        )
    }

    /**
     * A request to move a content to a position.
     *
     * @param move [Action.Move]
     * @param storyState [StoryState]
     */
    fun moveRequest(move: Action.Move, storyState: StoryState) =
        storyState.copy(
            stories = movementHandler.move(storyState.stories, move),
            lastEdit = LastEdit.Whole
        )

    /**
     * A request to move a content to a position.
     *
     * @param move [Action.BulkMove]
     * @param storyState [StoryState]
     */
    fun moveRequest(move: Action.BulkMove, storyState: StoryState) =
        storyState.copy(
            stories = movementHandler.move(storyState.stories, move),
            lastEdit = LastEdit.Whole
        )

    /**
     * Changes the state of a story step based of the stateChange
     *
     * @param stateChange [Action.StoryStateChange] the actual change.
     * @param storyState The current state of the document
     *
     * @return [StoryState] The current state
     */
    fun changeStoryState(
        stateChange: Action.StoryStateChange,
        storyState: StoryState
    ): StoryState? =
        contentHandler.changeStoryStepState(
            storyState.stories,
            stateChange.storyStep,
            stateChange.position
        )

    /**
     * Changes the story type. The type of a messages changes without changing the content of it.
     * Commands normally change the type of a message. From a message to a unordered list item, for
     * example.
     *
     * @param position Int
     * @param storyType [StoryStep]
     * @param commandInfo [CommandInfo]
     */
    fun changeStoryType(
        position: Int,
        typeInfo: TypeInfo,
        commandInfo: CommandInfo?,
        storyState: StoryState
    ): StoryState =
        contentHandler.changeStoryType(
            storyState.stories,
            typeInfo,
            position,
            commandInfo
        )

    /**
     * Creates a line break. When a line break happens, the line it divided into two [StoryStep]s
     * of the same, if possible, or the next line will be a Message.
     *
     * @param lineBreak [Action.LineBreak]
     */
    fun onLineBreak(
        lineBreak: Action.LineBreak,
        storyState: StoryState
    ): Pair<Pair<Int, StoryStep>, StoryState>? =
        contentHandler.onLineBreak(storyState.stories, lineBreak)

    /**
     * Deletes a [StoryStep]
     *
     * @param deleteStory [Action.DeleteStory]
     */
    fun onDelete(deleteStory: Action.DeleteStory, storyState: StoryState): StoryState? =
        contentHandler.deleteStory(deleteStory, storyState.stories)

    /**
     * Erases a [StoryStep]
     *
     * @param deleteStory [Action.DeleteStory]
     */
    fun onErase(deleteStory: Action.EraseStory, storyState: StoryState): StoryState =
        contentHandler.eraseStory(deleteStory, storyState.stories)

    fun previousTextStory(
        storyMap: Map<Int, StoryStep>,
        position: Int,
    ) = contentHandler.previousTextStory(storyMap, position)

    /**
     * Deletes the whole selection. All [StoryStep] in the selection will be deleted.
     */
    fun bulkDelete(
        positions: Iterable<Int>,
        stories: Map<Int, StoryStep>
    ) = contentHandler.bulkDeletion(positions, stories)
}
