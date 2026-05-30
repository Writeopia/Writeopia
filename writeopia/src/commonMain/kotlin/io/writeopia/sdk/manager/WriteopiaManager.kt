@file:OptIn(ExperimentalTime::class)

package io.writeopia.sdk.manager

import io.writeopia.sdk.ai.AiClient
import io.writeopia.sdk.model.action.Action
import io.writeopia.sdk.model.document.DocumentInfo
import io.writeopia.sdk.model.story.LastEdit
import io.writeopia.sdk.model.story.Selection
import io.writeopia.sdk.model.story.StoryState
import io.writeopia.sdk.models.command.CommandInfo
import io.writeopia.sdk.models.command.TypeInfo
import io.writeopia.sdk.models.id.GenerateId
import io.writeopia.sdk.models.span.Span
import io.writeopia.sdk.models.span.SpanInfo
import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.sdk.models.story.StoryType
import io.writeopia.sdk.models.story.StoryTypes
import io.writeopia.sdk.models.story.Tag
import io.writeopia.sdk.models.story.TagInfo
import io.writeopia.sdk.models.utils.ResultData
import io.writeopia.sdk.normalization.builder.StepsMapNormalizationBuilder
import io.writeopia.sdk.utils.alias.UnitsNormalizationMap
import io.writeopia.sdk.utils.extensions.toEditState
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

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
    private val spanHandler: SpansHandler = SpansHandler,
    private val aiClient: AiClient? = null
) {

    fun newDocument(
        documentId: String = GenerateId.generate(),
        userId: String = "",
        title: String = "",
        parentFolder: String = "root",
    ): Pair<DocumentInfo, StoryState> {
        val firstMessage = StoryStep(
            localId = GenerateId.generate(),
            type = StoryTypes.TITLE.type
        )
        val stories: Map<Double, StoryStep> = mapOf(0.0 to firstMessage)
        val normalized = stepsNormalizer(stories.toEditState())

        val now = Clock.System.now()

        val info = DocumentInfo(
            id = documentId,
            title = title,
            createdAt = now,
            lastUpdatedAt = now,
            parentId = parentFolder,
            isLocked = false,
            isFavorite = false,
            lastSyncedAt = null,
            userId = userId,
            companyId = null
        )

        val state = StoryState(
            normalized + normalized,
            LastEdit.Nothing,
            focus = 0.0
        )

        return info to state
    }

    /**
     * Moves the focus to the next available [StoryStep] if it can't find a step to focus, it
     * creates a new [StoryStep] at the end of the document.
     *
     * @param position Double
     * @param storyState [StoryState]
     */
    fun nextFocus(position: Double, cursor: Int, storyState: StoryState): StoryState {
        val storyMap = storyState.stories
        val nextPosition = focusHandler.findNextFocus(position, storyMap)

        return if (nextPosition != null) {
            storyState.copy(
                focus = nextPosition,
                selection = Selection.fromPosition(cursor, nextPosition),
            )
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
        val oldStories = storyState.stories
        val movedStories = movementHandler.merge(oldStories, info)
        val newStories = stepsNormalizer(movedStories)

        // Find all changed stories
        val changedSteps = newStories.filter { (position, story) ->
            oldStories[position] != story
        }.map { (position, story) -> position to story }

        return StoryState(
            stories = newStories,
            lastEdit = LastEdit.BulkEdition(changedSteps)
        )
    }

    /**
     * A request to move a content to a position.
     *
     * @param move [Action.Move]
     * @param storyState [StoryState]
     */
    fun moveRequest(move: Action.Move, storyState: StoryState): StoryState {
        val oldStories = storyState.stories
        val newStories = movementHandler.move(oldStories, move)

        // Find all changed stories
        val changedSteps = newStories.filter { (position, story) ->
            oldStories[position] != story
        }.map { (position, story) -> position to story }

        return storyState.copy(
            stories = newStories,
            lastEdit = LastEdit.BulkEdition(changedSteps)
        )
    }

    /**
     * A request to move a content to a position.
     *
     * @param move [Action.BulkMove]
     * @param storyState [StoryState]
     */
    fun moveRequest(move: Action.BulkMove, storyState: StoryState): StoryState {
        val oldStories = storyState.stories
        val newStories = movementHandler.move(oldStories, move)

        // Find all changed stories
        val changedSteps = newStories.filter { (position, story) ->
            oldStories[position] != story
        }.map { (position, story) -> position to story }

        return storyState.copy(
            stories = newStories,
            lastEdit = LastEdit.BulkEdition(changedSteps)
        )
    }

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
    ): StoryState =
        contentHandler.changeStoryStepState(
            storyState.stories,
            stateChange.storyStep,
            stateChange.position
        ) ?: storyState

    fun bulkChangeStoryState(
        storyState: StoryState,
        stateChange: Iterable<Action.StoryStateChange>,
    ): StoryState {
        val mutable = storyState.stories.toMutableMap()
        val changedSteps = mutableListOf<Pair<Double, StoryStep>>()

        stateChange.forEach { change ->
            mutable[change.position] = change.storyStep
            changedSteps.add(change.position to change.storyStep)
        }

        return StoryState(mutable, lastEdit = LastEdit.BulkEdition(changedSteps))
    }

    /**
     * Changes the story type. The type of a messages changes without changing the content of it.
     * Commands normally change the type of a message. From a message to a unordered list item, for
     * example.
     *
     * @param position Double
     * @param storyType [StoryStep]
     * @param commandInfo [CommandInfo]
     */
    fun changeStoryType(
        position: Double,
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

    fun bulkChangeStoryType(
        storyState: StoryState,
        change: Iterable<Pair<Double, TypeInfo>>
    ): StoryState = contentHandler.bulkChangeStoryType(storyState.stories, change)

    /**
     * Removes all tags from a story step
     */
    fun removeTags(position: Double, storyState: StoryState): StoryState =
        contentHandler.removeTags(storyState.stories, position)

    /**
     * Creates a line break. When a line break happens, the line it divided into two [StoryStep]s
     * of the same, if possible, or the next line will be a Message.
     *
     * @param lineBreak [Action.LineBreak]
     */
    fun onLineBreak(
        lineBreak: Action.LineBreak,
        storyState: StoryState
    ): Pair<Int, StoryState> =
        contentHandler.onLineBreak(storyState.stories, lineBreak)

    /**
     * Deletes a [StoryStep]
     *
     * @param deleteStory [Action.DeleteStory]
     * @param documentId The ID of the document containing the story step
     */
    fun onDelete(
        deleteStory: Action.DeleteStory,
        storyState: StoryState,
        documentId: String
    ): StoryState? =
        contentHandler.deleteStory(deleteStory, storyState.stories, documentId)

    /**
     * Erases a [StoryStep]
     *
     * @param deleteStory [Action.DeleteStory]
     */
    fun onErase(deleteStory: Action.EraseStory, storyState: StoryState): StoryState =
        contentHandler.eraseStory(deleteStory, storyState.stories)

    fun previousTextStory(
        storyMap: Map<Double, StoryStep>,
        position: Double,
    ): Pair<StoryStep, Double>? = contentHandler.previousTextStory(storyMap, position)

    /**
     * Deletes the whole selection. All [StoryStep] in the selection will be deleted.
     */
    fun bulkDelete(
        positions: Iterable<Double>,
        stories: Map<Double, StoryStep>
    ) = contentHandler.bulkDeletion(positions, stories)

    fun collapseItem(storyState: StoryState, position: Double) =
        contentHandler.collapseItem(storyState.stories, position)

    fun expandItem(storyState: StoryState, position: Double): StoryState =
        contentHandler.expandItem(storyState.stories, position)

    fun addSpanToStories(storyState: StoryState, positions: Set<Double>, span: Span): StoryState {
        val newMap = positions.mapNotNull {
            val story = storyState.stories[it]

            if (story != null) it to story else null
        }.toMap()
            .let {
                spanHandler.toggleSpansForManyStories(it, span)
            }

        val newStories = storyState.stories + newMap
        val changedSteps = newMap.map { (pos, story) -> pos to story }

        return storyState.copy(
            stories = newStories,
            lastEdit = LastEdit.BulkEdition(changedSteps)
        )
    }

    /**
     * Adds a Span like, Bold, Italic, Underline to a story.
     */
    fun addSpan(storyState: StoryState, position: Double, spanInfo: SpanInfo): StoryState =
        storyState.stories[position]?.let { story ->
            val spans = story.spans
            val newStory = story.copy(
                localId = GenerateId.generate(),
                spans = spanHandler.toggleSpans(spans, spanInfo)
            )

            val newStories = storyState.stories + (position to newStory)

            storyState.copy(
                stories = newStories,
                lastEdit = LastEdit.LineEdition(
                    position = position,
                    storyStep = newStory
                )
            )
        } ?: storyState

    /**
     * Adds a story in a position. Useful to add stories that were not created by the end user, but
     * by an API call or different event.
     */
    fun addAtPosition(storyState: StoryState, storyStep: StoryStep, position: Double): StoryState {
        val newStory = contentHandler.addNewContent(storyState.stories, storyStep, position)

        return storyState.copy(
            stories = newStory,
            lastEdit = LastEdit.LineEdition(position, storyStep)
        )
    }

    fun removeAtPosition(storyState: StoryState, position: Double): StoryState {
        val newStory = contentHandler.removeContent(storyState.stories, position)
        return storyState.copy(stories = newStory)
    }

    fun removeBy(storyState: StoryState, predicate: (StoryStep) -> Boolean): StoryState {
        val newStory = contentHandler.removeBy(storyState.stories, predicate)
        return storyState.copy(stories = newStory)
    }

    fun addDocumentLink(
        storyState: StoryState,
        position: Double,
        documentId: String,
        text: String
    ): StoryState {
        val newStories =
            contentHandler.addPage(
                storyState.stories,
                position = position,
                documentId = documentId,
                text = text
            )

        return storyState.copy(
            stories = newStories,
            lastEdit = LastEdit.LineEdition(position, newStories[position]!!)
        )
    }

    suspend fun generateSuggestionsList(
        storyState: () -> StoryState,
        storyType: StoryType,
        position: Double,
        context: String,
        userId: String,
    ): StoryState {
        val model = aiClient?.getSelectedModel(userId) ?: return storyState()
        val url = aiClient.getConfiguredUrl(userId) ?: return storyState()

        val suggestionsResult =
            aiClient.generateListItems(model = model, context = context, url = url)

        return if (suggestionsResult is ResultData.Complete) {
            val suggestions = suggestionsResult.data
            val originalState = storyState()
            val originalStories = originalState.stories

            val finalState = suggestions.mapIndexed { i, suggestion ->
                val tags = if (i == 0) {
                    setOf(TagInfo(Tag.AI_SUGGESTION), TagInfo(Tag.FIRST_AI_SUGGESTION))
                } else {
                    setOf(TagInfo(Tag.AI_SUGGESTION))
                }
                StoryStep(text = suggestion, type = storyType, tags = tags)
            }.reversed()
                .fold(originalState) { state, story ->
                    addAtPosition(
                        state,
                        story.copy(ephemeral = true),
                        position
                    )
                }

            // Find all changed stories
            val changedSteps = finalState.stories.filter { (pos, story) ->
                originalStories[pos] != story
            }.map { (pos, story) -> pos to story }

            finalState.copy(lastEdit = LastEdit.BulkEdition(changedSteps))
        } else {
            storyState()
        }
    }
}
