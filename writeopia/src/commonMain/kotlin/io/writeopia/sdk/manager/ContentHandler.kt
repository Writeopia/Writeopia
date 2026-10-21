package io.writeopia.sdk.manager

import io.writeopia.sdk.model.action.Action
import io.writeopia.sdk.model.story.LastEdit
import io.writeopia.sdk.model.story.Selection
import io.writeopia.sdk.model.story.StoryState
import io.writeopia.sdk.models.command.CommandInfo
import io.writeopia.sdk.models.command.CommandTrigger
import io.writeopia.sdk.models.command.TypeInfo
import io.writeopia.sdk.models.id.GenerateId
import io.writeopia.sdk.models.link.DocumentLink
import io.writeopia.sdk.models.span.SpanInfo
import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.sdk.models.story.StoryType
import io.writeopia.sdk.models.story.StoryTypes
import io.writeopia.sdk.models.story.Tag
import io.writeopia.sdk.models.story.TagInfo
import io.writeopia.sdk.utils.NextPositionCalculator
import io.writeopia.sdk.utils.alias.UnitsNormalizationMap
import io.writeopia.sdk.utils.collections.toSortedMutableMap
import io.writeopia.sdk.utils.extensions.toEditState
import io.writeopia.sdk.utils.iterables.Insertion
import io.writeopia.sdk.utils.iterables.insertAfter
import io.writeopia.sdk.utils.iterables.insertAll
import io.writeopia.sdk.utils.iterables.insertBefore
import io.writeopia.sdk.utils.iterables.nextKey
import io.writeopia.sdk.utils.iterables.previousKey
import io.writeopia.sdk.utils.iterables.removeBy
import io.writeopia.sdk.utils.iterables.removeElementInPosition

/**
 * Class dedicated to handle adding, deleting or changing StorySteps
 */
class ContentHandler(
    private val focusableTypes: Set<Int> = setOf(
        StoryTypes.TITLE.type.number,
        StoryTypes.TEXT.type.number,
        StoryTypes.CHECK_ITEM.type.number,
        StoryTypes.UNORDERED_LIST_ITEM.type.number,
    ),
    private val stepsNormalizer: UnitsNormalizationMap,
    private val lineBreakMap: (StoryType) -> StoryType = ::defaultLineBreakMap,
    private val isTextStory: (StoryStep) -> Boolean = { story ->
        focusableTypes.contains(story.type.number)
    },
    private val focusHandler: FocusHandler = FocusHandler { typeNumber ->
        focusableTypes.contains(typeNumber)
    },
    private val spreadsheetHandler: SpreadsheetHandler = SpreadsheetHandler()
) {

    fun changeStoryStepState(
        currentStory: Map<Long, StoryStep>,
        newState: StoryStep,
        position: Long
    ): StoryState? = if (currentStory[position] != null) {
        val newMap = currentStory.toSortedMutableMap()
        newMap[position] = newState
        StoryState(newMap, LastEdit.LineEdition(position, newState), position)
    } else {
        null
    }

    /**
     * Removes tags from a StoryStep at a certain position
     */
    fun removeTags(currentStory: Map<Long, StoryStep>, position: Long): StoryState {
        val newMap = currentStory.toSortedMutableMap()
        val storyStep = newMap[position]

        val newStory = storyStep?.copy(
            localId = GenerateId.generate(),
            tags = emptySet()
        )

        return if (newStory != null) {
            newMap[position] = newStory

            StoryState(newMap, LastEdit.LineEdition(position, newStory), position)
        } else {
            StoryState(newMap, LastEdit.Nothing, position)
        }
    }

    fun changeStoryType(
        currentStory: Map<Long, StoryStep>,
        typeInfo: TypeInfo,
        position: Long,
        commandInfo: CommandInfo?
    ): StoryState {
        val newMap = changeType(currentStory, typeInfo, position, commandInfo)
        val changedStep = newMap[position]
        val lastEdit = if (changedStep != null) {
            LastEdit.LineEdition(position, changedStep)
        } else {
            LastEdit.Nothing
        }
        return StoryState(newMap, lastEdit, position)
    }

    fun bulkChangeStoryType(
        currentStory: Map<Long, StoryStep>,
        change: Iterable<Pair<Long, TypeInfo>>
    ): StoryState {
        val positions = change.map { it.first }.toSet()
        val newMap = change.fold(currentStory) { acc, (position, typeInfo) ->
            changeType(
                currentStory = acc,
                typeInfo = typeInfo,
                position = position,
                commandInfo = null
            )
        }

        // Only the changed steps, at their positions
        val changedSteps = positions.mapNotNull { position ->
            newMap[position]?.let { step -> position to step }
        }

        val lastEdit = if (changedSteps.isNotEmpty()) {
            LastEdit.BulkEdition(changedSteps)
        } else {
            LastEdit.Nothing
        }

        return StoryState(newMap, lastEdit)
    }

    private fun changeType(
        currentStory: Map<Long, StoryStep>,
        typeInfo: TypeInfo,
        position: Long,
        commandInfo: CommandInfo?
    ): Map<Long, StoryStep> {
        val newMap = currentStory.toSortedMutableMap()
        val storyStep = newMap[position]
        val commandTrigger = commandInfo?.commandTrigger
        val commandText = commandInfo?.command?.commandText?.trim() ?: ""

        return if (storyStep != null) {
            val storyText = storyStep.text
            val newText = if (
                commandTrigger == CommandTrigger.WRITTEN &&
                storyText?.startsWith(commandText) == true
            ) {
                // The command leaves the line with the space that triggered it ("## " of
                // "## Heading"), so the text does not start with a space.
                storyText.removePrefix(commandText).removePrefix(" ")
            } else {
                storyStep.text
            }

            val decoration = typeInfo.decoration
            val tags = storyStep.tags.merge(commandInfo?.tags ?: emptySet())
            val newCheck = if (decoration != null) {
                storyStep.copy(
                    localId = GenerateId.generate(),
                    type = typeInfo.storyType,
                    text = newText,
                    decoration = decoration,
                    tags = tags
                )
            } else {
                storyStep.copy(
                    localId = GenerateId.generate(),
                    type = typeInfo.storyType,
                    text = newText,
                    tags = tags
                )
            }

            newMap[position] = newCheck
            newMap
        } else {
            currentStory
        }
    }

    private fun Set<TagInfo>.merge(tagInfo: Set<TagInfo>): Set<TagInfo> =
        if (tagInfo.any { it.tag.isTitle() }) {
            this.filterNot { it.tag.isTitle() }.toSet() + tagInfo
        } else {
            this + tagInfo
        }

    /**
     * Adds [newStoryUnit] at [position], or right before the step already there. The steps
     * after are spread out first when there is no room for it (see [Insertion.moved]). The
     * links of the steps touched are updated.
     */
    fun addNewContent(
        currentStory: Map<Long, StoryStep>,
        newStoryUnit: StoryStep,
        position: Long
    ): Insertion<StoryStep> = currentStory.insertBefore(newStoryUnit, position).relinked()

    /**
     * Adds [newStoryUnit] right after the step at [position]. The steps after are spread out
     * first when there is no room for it (see [Insertion.moved]). The links of the steps
     * touched are updated.
     */
    fun addNewContentAfter(
        currentStory: Map<Long, StoryStep>,
        newStoryUnit: StoryStep,
        position: Long
    ): Insertion<StoryStep> = currentStory.insertAfter(newStoryUnit, position).relinked()

    /**
     * Adds [newStoryUnits], in order, at [position] or right before the step already there.
     * The steps after are spread out first when there is no room (see [Insertion.moved]). The
     * links of the steps touched are updated.
     */
    fun addNewContents(
        currentStory: Map<Long, StoryStep>,
        newStoryUnits: List<StoryStep>,
        position: Long
    ): Insertion<StoryStep> = currentStory.insertBefore(newStoryUnits, position).relinked()

    /**
     * Adds [newStoryUnits], in order, right after the step at [position]. The steps after are
     * spread out first when there is no room (see [Insertion.moved]). The links of the steps
     * touched are updated.
     */
    fun addNewContentsAfter(
        currentStory: Map<Long, StoryStep>,
        newStoryUnits: List<StoryStep>,
        position: Long
    ): Insertion<StoryStep> = currentStory.insertAfter(newStoryUnits, position).relinked()

    /** The links of the steps added and moved, and of their neighbours, updated. */
    private fun Insertion<StoryStep>.relinked(): Insertion<StoryStep> {
        (keys + moved).forEach { key -> NextPositionCalculator.relinkAround(map, key) }
        return this
    }

    /** The steps at [keys], as an edition saves them. */
    private fun Insertion<StoryStep>.movedSteps(): List<Pair<Long, StoryStep>> =
        moved.map { key -> key to map.getValue(key) }

    /** Removes the step at [position]. No other step moves. The links of the neighbours are updated. */
    fun removeContent(
        currentStory: Map<Long, StoryStep>,
        position: Long
    ): Map<Long, StoryStep> =
        NextPositionCalculator.relinkAround(currentStory.removeElementInPosition(position), position)

    /** Removes the steps matching [predicate]. No other step moves. The links are updated. */
    fun removeBy(
        currentStory: Map<Long, StoryStep>,
        predicate: (StoryStep) -> Boolean
    ): Map<Long, StoryStep> = NextPositionCalculator.calculate(currentStory.removeBy(predicate))

    /**
     * Adds a link to a new document inside a document
     */
    fun addPage(
        currentStory: Map<Long, StoryStep>,
        position: Long,
        documentId: String,
        text: String
    ): Map<Long, StoryStep> {
        val mutable = currentStory.toSortedMutableMap()
        mutable[position] =
            StoryStep(
                type = StoryTypes.DOCUMENT_LINK.type,
                documentLink = DocumentLink(documentId, text),
            )

        return mutable
    }

    /**
     * Turns the text step at [position] into a link card for [url]. A card can not be typed in,
     * so the cursor goes to the text line below it, which is added when there is none. The
     * description of the page stays empty: the app asks the backend for it.
     *
     * When a line is added, the result carries a [LastEdit.LineBreakEdition] with the card and
     * the new line, so both are saved and synced together.
     *
     * @return the new state, or null when there is no step at [position].
     */
    fun createLinkCard(
        currentStory: Map<Long, StoryStep>,
        position: Long,
        url: String
    ): StoryState? {
        val step = currentStory[position] ?: return null
        val linkCard = step.copy(
            localId = GenerateId.generate(),
            type = StoryTypes.LINK_CARD.type,
            url = url,
            text = null,
            tags = emptySet(),
            spans = emptySet()
        )

        val next = currentStory.nextKey(position)
        val nextStory = next?.let { currentStory[it] }

        if (next != null && nextStory?.type?.number == StoryTypes.TEXT.type.number) {
            val stories = currentStory.toSortedMutableMap().apply { this[position] = linkCard }

            return StoryState(
                stories = stories,
                lastEdit = LastEdit.LineEdition(position, linkCard),
                focus = next,
                selection = Selection.fromPosition(cursorPosition = 0, stepPosition = next)
            )
        }

        // An empty line break of the text line adds an empty text line below it; then the
        // line becomes the card.
        val (_, broken) = onLineBreak(
            currentStory,
            Action.LineBreak(step.copy(text = "\n", spans = emptySet()), position)
        )
        val cardWithLinks = linkCard.copy(nextPosition = broken.stories[position]?.nextPosition)
        val stories = broken.stories.toSortedMutableMap().apply { this[position] = cardWithLinks }
        val lastEdit = (broken.lastEdit as? LastEdit.LineBreakEdition)
            ?.copy(originalStep = position to cardWithLinks)
            ?: LastEdit.LineEdition(position, cardWithLinks)

        return broken.copy(stories = stories, lastEdit = lastEdit, selection = Selection.start())
    }

    /**
     * Adds the steps of [newStory], each at its position or right before the step already
     * there. The steps after are spread out when there is no room (see [Insertion.moved]).
     * The links of the steps touched are updated.
     */
    fun addNewContentBulk(
        currentStory: Map<Long, StoryStep>,
        newStory: Map<Long, StoryStep>,
    ): Insertion<StoryStep> = currentStory.insertAll(newStory).relinked()

    /**
     * Splits the step at [Action.LineBreak.position] at the line breaks of its text. The first
     * line keeps the position, the others go right after it, before the next step: the steps
     * after are spread out first when there is no room for them (and saved with the lines).
     *
     * @return the position of the last line, as an Int, and the new state.
     */
    fun onLineBreak(
        currentStory: Map<Long, StoryStep>,
        lineBreakInfo: Action.LineBreak
    ): Pair<Int, StoryState> {
        val storyStep = lineBreakInfo.storyStep
        val position = lineBreakInfo.position
        val carryOverTags = storyStep.tags.filterTo(mutableSetOf()) { it.tag.mustCarryOver() }
        val split = storyStep.text?.split("\n")
        val splitSpans = split
            ?.let { lines -> splitSpansByLines(lines, storyStep.spans) }
            ?: listOf(storyStep.spans)

        // The original line keeps the first part of the split text
        val updatedOriginalStep = if (split?.isNotEmpty() == true) {
            storyStep.copy(
                text = split[0],
                spans = splitSpans.firstOrNull() ?: emptySet(),
                localId = GenerateId.generate()
            )
        } else {
            storyStep
        }

        val newLines = split?.drop(1) ?: emptyList()
        if (newLines.isEmpty()) {
            val stories = currentStory.toSortedMutableMap().apply { this[position] = updatedOriginalStep }
            return position.toInt() to StoryState(
                stories = stories,
                lastEdit = LastEdit.LineEdition(position, stories.getValue(position)),
                focus = position
            )
        }

        val newStories = newLines.mapIndexed { index, text ->
            StoryStep(
                localId = GenerateId.generate(),
                type = lineBreakMap(storyStep.type),
                text = text,
                tags = carryOverTags,
                spans = splitSpans.getOrElse(index + 1) { emptySet() }
            )
        }

        val withOriginal = currentStory.toSortedMutableMap().apply { this[position] = updatedOriginalStep }
        val insertion = withOriginal.insertAfter(newStories, position).relinked()
        NextPositionCalculator.relinkAround(insertion.map, position)
        val stories = insertion.map
        val lastPosition = insertion.keys.last()

        val original = position to stories.getValue(position)
        val added = insertion.keys.map { key -> key to stories.getValue(key) }
        val lastEdit = if (added.size == 1 && insertion.moved.isEmpty()) {
            LastEdit.LineBreakEdition(originalStep = original, newStep = added.single())
        } else {
            LastEdit.BulkEdition(listOf(original) + added + insertion.movedSteps())
        }

        return lastPosition.toInt() to StoryState(
            stories = stories,
            lastEdit = lastEdit,
            focus = lastPosition
        )
    }

    private fun splitSpansByLines(
        lines: List<String>,
        spans: Set<SpanInfo>
    ): List<Set<SpanInfo>> {
        var lineStart = 0

        return lines.map { line ->
            val lineEnd = lineStart + line.length
            val lineSpans = spans.mapNotNullTo(mutableSetOf()) { span ->
                val overlapStart = maxOf(span.start, lineStart)
                val overlapEnd = minOf(span.end, lineEnd)

                if (overlapEnd > overlapStart) {
                    SpanInfo.create(
                        start = overlapStart - lineStart,
                        end = overlapEnd - lineStart,
                        span = span.span,
                        extra = span.extra,
                    )
                } else {
                    null
                }
            }

            lineStart = lineEnd + 1
            lineSpans
        }
    }

    /**
     * Deletes one story steps.
     */
    fun deleteStory(
        deleteInfo: Action.DeleteStory,
        history: Map<Long, StoryStep>,
        documentId: String
    ): StoryState? {
        val step = deleteInfo.storyStep
        val parentId = step.parentId
        val mutableSteps = history.toSortedMutableMap()

        return if (parentId == null) {
            // The neighbours come from the map, the links of the step may be older than the map
            val prevPos = history.previousKey(deleteInfo.position)
            val nextPos = history.nextKey(deleteInfo.position)

            mutableSteps.remove(deleteInfo.position)

            // Update the previous story's nextPosition to skip the deleted story
            if (prevPos != null) {
                mutableSteps[prevPos]?.let { prevStory ->
                    mutableSteps[prevPos] = prevStory.copy(nextPosition = nextPos)
                }
            }

            // Update the next story's previousPosition to skip the deleted story
            if (nextPos != null) {
                mutableSteps[nextPos]?.let { nextStory ->
                    mutableSteps[nextPos] = nextStory.copy(previousPosition = prevPos)
                }
            }

            val previousFocus: Long? =
                focusHandler.findPreviousFocus(deleteInfo.position, mutableSteps)

            val normalized = stepsNormalizer(mutableSteps.toEditState())
            StoryState(
                normalized,
                lastEdit = LastEdit.DeleteEdition(deletedId = step.id, documentId = documentId),
                focus = previousFocus
            )
        } else {
            mutableSteps[deleteInfo.position]?.let { group ->
                val newSteps = group.steps.filter { storyUnit ->
                    storyUnit.localId != step.localId
                }

                val newStoryUnit = if (newSteps.size == 1) {
                    newSteps.first()
                } else {
                    group.copy(steps = newSteps)
                }

                mutableSteps[deleteInfo.position] = newStoryUnit.copy(parentId = null)
                val normalized = stepsNormalizer(mutableSteps.toEditState())
                // For group deletion, use the group's id
                StoryState(
                    normalized,
                    lastEdit = LastEdit.DeleteEdition(deletedId = step.id, documentId = documentId)
                )
            }
        }
    }

    fun eraseStory(deleteInfo: Action.EraseStory, history: Map<Long, StoryStep>): StoryState {
        val mutableSteps = history.toSortedMutableMap()
        val deletedStep = deleteInfo.storyStep

        // The neighbours come from the map, the links of the step may be older than the map
        val prevPos = history.previousKey(deleteInfo.position)
        val nextPos = history.nextKey(deleteInfo.position)

        mutableSteps.remove(deleteInfo.position)

        // Update the next story's previousPosition to skip the deleted story
        if (nextPos != null) {
            mutableSteps[nextPos]?.let { nextStory ->
                mutableSteps[nextPos] = nextStory.copy(previousPosition = prevPos)
            }
        }

        val previousFocus: Long? =
            focusHandler.findPreviousFocus(deleteInfo.position, mutableSteps)

        var updatedPrevious: StoryStep? = null

        if (previousFocus != null) {
            mutableSteps[previousFocus]?.let { previous ->
                // Update the previous story's nextPosition to skip the deleted story
                // and merge the text from the deleted story
                val previousText = previous.text.orEmpty()
                val deletedText = deleteInfo.storyStep.text.orEmpty()
                val shiftedSpans = deleteInfo.storyStep.spans
                    .mapTo(mutableSetOf()) { span -> span.move(previousText.length) }

                val updated = previous.copy(
                    text = previousText + deletedText,
                    spans = previous.spans + shiftedSpans,
                    localId = GenerateId.generate(),
                    nextPosition = nextPos
                )
                mutableSteps[previousFocus] = updated
                updatedPrevious = updated
            }
        }

        val normalized = stepsNormalizer(mutableSteps.toEditState())

        val lastEdit = if (updatedPrevious != null && previousFocus != null) {
            LastEdit.EraseEdition(
                deletedId = deletedStep.id,
                updatedStep = previousFocus to updatedPrevious
            )
        } else {
            // No previous text story found, just delete
            LastEdit.DeleteEdition(deletedId = deletedStep.id, documentId = "")
        }

        return StoryState(normalized, lastEdit = lastEdit, focus = previousFocus)
    }

    /**
     * Delete story steps in bulk. Returns a pair with first the new state of stories and
     * the deleted stories.
     */
    fun bulkDeletion(
        positions: Iterable<Long>,
        stories: Map<Long, StoryStep>
    ): Pair<Map<Long, StoryStep>, Map<Long, StoryStep>> {
        val deleted = mutableMapOf<Long, StoryStep>()
        val newState = stories.toSortedMutableMap()

        positions.forEach { position ->
            newState.remove(position)?.let { deletedStory ->
                deleted[position] = deletedStory
            }
        }

        // The steps around the deleted ones link to each other now
        deleted.keys.forEach { position -> NextPositionCalculator.relinkAround(newState, position) }
        return newState to deleted
    }

    fun previousTextStory(
        storyMap: Map<Long, StoryStep>,
        position: Long
    ): Pair<StoryStep, Long>? {
        val prevPosition = focusHandler.findPreviousFocus(position, storyMap) ?: return null
        val prevStory = storyMap[prevPosition] ?: return null
        return prevStory.copy(localId = GenerateId.generate()) to prevPosition
    }

    fun collapseItem(
        storyMap: Map<Long, StoryStep>,
        position: Long
    ): StoryState {
        val mutable = storyMap.toSortedMutableMap()
        storyMap[position]?.let { step ->
            val tagInfo = setOf(TagInfo(tag = Tag.COLLAPSED))
            mutable[position] = step.copy(tags = step.tags.merge(tagInfo))
        }

        // Walks to the next header, one neighbour at a time
        var pos = storyMap.nextKey(position)
        while (pos != null) {
            val step = storyMap[pos] ?: break
            if (step.tags.any { it.tag.isTitle() }) break

            val tagInfo = setOf(TagInfo(tag = Tag.HIDDEN_HX))
            mutable[pos] = step.copy(tags = step.tags.merge(tagInfo))
            pos = storyMap.nextKey(pos)
        }

        return StoryState(
            mutable,
            lastEdit = LastEdit.LineEdition(position, storyMap[position]!!),
            focus = position
        )
    }

    /**
     * This method expands a section of the document like a subtitle.
     */
    fun expandItem(
        storyMap: Map<Long, StoryStep>,
        position: Long
    ): StoryState {
        val mutable = storyMap.toSortedMutableMap()
        storyMap[position]?.let { step ->
            mutable[position] =
                step.copy(tags = step.tags.filterNotTo(mutableSetOf()) { it.tag == Tag.COLLAPSED })
        }

        // Walks to the next header, one neighbour at a time
        var pos = storyMap.nextKey(position)
        while (pos != null) {
            val step = storyMap[pos] ?: break
            if (step.tags.any { it.tag.isTitle() }) break

            val tagInfo = step.tags.filterNotTo(mutableSetOf()) { it.tag == Tag.HIDDEN_HX }
            mutable[pos] = step.copy(tags = tagInfo)
            pos = storyMap.nextKey(pos)
        }

        return StoryState(
            mutable,
            lastEdit = LastEdit.LineEdition(position, storyMap[position]!!),
            focus = position
        )
    }

    fun createSpreadsheet(
        currentStory: Map<Long, StoryStep>,
        position: Long,
        columnCount: Int,
        rowCount: Int = 3,
        insertMode: Boolean = false
    ): Insertion<StoryStep> = spreadsheetHandler.createSpreadsheet(
        currentStory,
        position,
        columnCount,
        rowCount,
        insertMode
    ).relinked()

    fun updateSpreadsheetCell(
        currentStory: Map<Long, StoryStep>,
        spreadsheetId: String,
        rowIndex: Int,
        cellIndex: Int,
        newText: String
    ): Map<Long, StoryStep>? = spreadsheetHandler.updateSpreadsheetCell(
        currentStory,
        spreadsheetId,
        rowIndex,
        cellIndex,
        newText
    )

    fun addSpreadsheetRow(
        currentStory: Map<Long, StoryStep>,
        spreadsheetId: String
    ): Map<Long, StoryStep>? = spreadsheetHandler.addSpreadsheetRow(currentStory, spreadsheetId)

    fun addSpreadsheetColumn(
        currentStory: Map<Long, StoryStep>,
        spreadsheetId: String
    ): Map<Long, StoryStep>? = spreadsheetHandler.addSpreadsheetColumn(currentStory, spreadsheetId)

    fun updateSpreadsheetColumnWidth(
        currentStory: Map<Long, StoryStep>,
        spreadsheetId: String,
        columnIndex: Int,
        newWidth: Int
    ): Map<Long, StoryStep>? = spreadsheetHandler.updateSpreadsheetColumnWidth(
        currentStory,
        spreadsheetId,
        columnIndex,
        newWidth
    )

    fun deleteSpreadsheetRow(
        currentStory: Map<Long, StoryStep>,
        spreadsheetId: String,
        rowIndex: Int
    ): Map<Long, StoryStep>? = spreadsheetHandler.deleteSpreadsheetRow(
        currentStory,
        spreadsheetId,
        rowIndex
    )

    fun deleteSpreadsheetColumn(
        currentStory: Map<Long, StoryStep>,
        spreadsheetId: String,
        columnIndex: Int
    ): Map<Long, StoryStep>? = spreadsheetHandler.deleteSpreadsheetColumn(
        currentStory,
        spreadsheetId,
        columnIndex
    )

    fun addSpreadsheetRowAt(
        currentStory: Map<Long, StoryStep>,
        spreadsheetId: String,
        rowIndex: Int
    ): Map<Long, StoryStep>? = spreadsheetHandler.addSpreadsheetRowAt(
        currentStory,
        spreadsheetId,
        rowIndex
    )

    fun addSpreadsheetColumnAt(
        currentStory: Map<Long, StoryStep>,
        spreadsheetId: String,
        columnIndex: Int
    ): Map<Long, StoryStep>? = spreadsheetHandler.addSpreadsheetColumnAt(
        currentStory,
        spreadsheetId,
        columnIndex
    )

    fun moveSpreadsheetRow(
        currentStory: Map<Long, StoryStep>,
        spreadsheetId: String,
        fromIndex: Int,
        toIndex: Int
    ): Map<Long, StoryStep>? = spreadsheetHandler.moveSpreadsheetRow(
        currentStory,
        spreadsheetId,
        fromIndex,
        toIndex
    )

    fun moveSpreadsheetColumn(
        currentStory: Map<Long, StoryStep>,
        spreadsheetId: String,
        fromIndex: Int,
        toIndex: Int
    ): Map<Long, StoryStep>? = spreadsheetHandler.moveSpreadsheetColumn(
        currentStory,
        spreadsheetId,
        fromIndex,
        toIndex
    )
}

private fun defaultLineBreakMap(storyType: StoryType): StoryType =
    when (storyType) {
        StoryTypes.TITLE.type -> StoryTypes.TEXT.type

        else -> storyType
    }
