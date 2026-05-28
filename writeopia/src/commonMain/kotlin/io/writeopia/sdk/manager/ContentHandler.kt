package io.writeopia.sdk.manager

import io.writeopia.sdk.model.action.Action
import io.writeopia.sdk.model.story.LastEdit
import io.writeopia.sdk.model.story.StoryState
import io.writeopia.sdk.models.command.CommandInfo
import io.writeopia.sdk.models.command.CommandTrigger
import io.writeopia.sdk.models.command.TypeInfo
import io.writeopia.sdk.models.id.GenerateId
import io.writeopia.sdk.models.link.DocumentLink
import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.sdk.models.story.StoryType
import io.writeopia.sdk.models.story.StoryTypes
import io.writeopia.sdk.models.story.Tag
import io.writeopia.sdk.models.story.TagInfo
import io.writeopia.sdk.utils.alias.UnitsNormalizationMap
import io.writeopia.sdk.utils.extensions.toEditState
import io.writeopia.sdk.utils.iterables.addElementInPosition
import io.writeopia.sdk.utils.iterables.addElementsInPosition
import io.writeopia.sdk.utils.iterables.mergeSortedMaps
import io.writeopia.sdk.utils.iterables.normalizePositions
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
    }
) {

    fun changeStoryStepState(
        currentStory: Map<Double, StoryStep>,
        newState: StoryStep,
        position: Double
    ): StoryState? = if (currentStory[position] != null) {
        val newMap = currentStory.toMutableMap()
        newMap[position] = newState
        StoryState(newMap, LastEdit.LineEdition(position, newState), position)
    } else {
        null
    }

    /**
     * Removes tags from a StoryStep at a certain position
     */
    fun removeTags(currentStory: Map<Double, StoryStep>, position: Double): StoryState {
        val newMap = currentStory.toMutableMap()
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
        currentStory: Map<Double, StoryStep>,
        typeInfo: TypeInfo,
        position: Double,
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
        currentStory: Map<Double, StoryStep>,
        change: Iterable<Pair<Double, TypeInfo>>
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

        // Collect only the changed steps with their db positions
        val changedSteps = positions.mapNotNull { position ->
            newMap[position]?.let { step ->
                val dbPos = step.dbPosition ?: position
                dbPos to step
            }
        }

        val lastEdit = if (changedSteps.isNotEmpty()) {
            LastEdit.BulkEdition(changedSteps)
        } else {
            LastEdit.Nothing
        }

        return StoryState(newMap, lastEdit)
    }

    private fun changeType(
        currentStory: Map<Double, StoryStep>,
        typeInfo: TypeInfo,
        position: Double,
        commandInfo: CommandInfo?
    ): Map<Double, StoryStep> {
        val newMap = currentStory.toMutableMap()
        val storyStep = newMap[position]
        val commandTrigger = commandInfo?.commandTrigger
        val commandText = commandInfo?.command?.commandText?.trim() ?: ""

        return if (storyStep != null) {
            val storyText = storyStep.text
            val newText = if (
                commandTrigger == CommandTrigger.WRITTEN &&
                storyText?.startsWith(commandText) == true
            ) {
                storyText.subSequence(commandText.length, storyText.length).toString()
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

    // Todo: Add unit test
    fun addNewContent(
        currentStory: Map<Double, StoryStep>,
        newStoryUnit: StoryStep,
        position: Double
    ): Map<Double, StoryStep> = currentStory.addElementInPosition(newStoryUnit, position)

    fun removeContent(
        currentStory: Map<Double, StoryStep>,
        position: Double
    ): Map<Double, StoryStep> = currentStory.removeElementInPosition(position)

    fun removeBy(
        currentStory: Map<Double, StoryStep>,
        predicate: (StoryStep) -> Boolean
    ): Map<Double, StoryStep> = currentStory.removeBy(predicate)

    /**
     * Adds a link to a new document inside a document
     */
    fun addPage(
        currentStory: Map<Double, StoryStep>,
        position: Double,
        documentId: String,
        text: String
    ): Map<Double, StoryStep> {
        val mutable = currentStory.toMutableMap()
        mutable[position] =
            StoryStep(
                type = StoryTypes.DOCUMENT_LINK.type,
                documentLink = DocumentLink(documentId, text),
            )

        return mutable
    }

    fun addNewContentBulk(
        currentStory: Map<Double, StoryStep>,
        newStory: Map<Double, StoryStep>,
    ): Map<Double, StoryStep> = currentStory.mergeSortedMaps(newStory)

    fun onLineBreak(
        currentStory: Map<Double, StoryStep>,
        lineBreakInfo: Action.LineBreak
    ): Pair<Int, StoryState> {
        val storyStep = lineBreakInfo.storyStep
        val position = lineBreakInfo.position
        val carryOverTags = storyStep.tags.filterTo(mutableSetOf()) { it.tag.mustCarryOver() }
        val mutable = currentStory.toMutableMap()
        val split = storyStep.text?.split("\n")

        val next = storyStep.nextPosition
        val nextPosition = if (next != null) (next + position) / 2 else position + 1

        println("online break. next $next, position: $position")

        // Update the original line with the first part of the split text
        // The original step now points to the new step as its next
        val updatedOriginalStep = if (split?.isNotEmpty() == true) {
            lineBreakInfo.storyStep.copy(
                text = split[0],
                localId = GenerateId.generate(),
                nextPosition = nextPosition
            )
        } else {
            storyStep.copy(nextPosition = nextPosition)
        }

        mutable[position] = updatedOriginalStep

        if (split?.size == 2) {
            // The new story has:
            // - previousPosition pointing to the original
            // - nextPosition pointing to the original's old next
            val newStory = StoryStep(
                localId = GenerateId.generate(),
                type = lineBreakMap(storyStep.type),
                text = split[1],
                tags = carryOverTags,
                dbPosition = nextPosition,
                previousPosition = position,
                nextPosition = next
            )

            mutable[nextPosition] = newStory

            // Update the story that was after the original to point back to the new story
            if (next != null) {
                mutable[next]?.let { nextStory ->
                    mutable[next] = nextStory.copy(previousPosition = nextPosition)
                }
            }

            println("next focus: $nextPosition")

            return nextPosition.toInt() to StoryState(
                stories = mutable,
                lastEdit = LastEdit.LineBreakEdition(
                    originalStep = position to updatedOriginalStep,
                    newStep = nextPosition to newStory
                ),
                focus = nextPosition
            )
        }

        // For multiple line breaks (pasting text with multiple newlines)
        val newLines = split?.drop(1) ?: emptyList()
        if (newLines.isEmpty()) {
            return position.toInt() to StoryState(
                stories = mutable,
                lastEdit = LastEdit.LineEdition(position, updatedOriginalStep),
                focus = position
            )
        }

        // Calculate intermediate positions for each new line
        val endPos = next ?: (position + newLines.size + 1)
        val changedSteps = mutableListOf<Pair<Double, StoryStep>>()

        // Add the updated original step
        changedSteps.add(position to updatedOriginalStep)

        var prevPos = position
        var lastNewPosition = position

        for ((index, text) in newLines.withIndex()) {
            val newPos = (prevPos + endPos) / 2.0
            lastNewPosition = newPos

            val newNextPos = if (index < newLines.size - 1) {
                // Will be calculated in next iteration
                null
            } else {
                next
            }

            val newStory = StoryStep(
                localId = GenerateId.generate(),
                type = lineBreakMap(storyStep.type),
                text = text,
                tags = carryOverTags,
                dbPosition = newPos,
                previousPosition = prevPos,
                nextPosition = newNextPos
            )

            mutable[newPos] = newStory
            changedSteps.add(newPos to newStory)

            // Update previous story to point to this one
            mutable[prevPos]?.let { mutable[prevPos] = it.copy(nextPosition = newPos) }
            // Update changedSteps with the corrected previous story
            val prevIndex = changedSteps.indexOfFirst { it.first == prevPos }
            if (prevIndex >= 0) {
                changedSteps[prevIndex] = prevPos to mutable[prevPos]!!
            }

            prevPos = newPos
        }

        // Fix nextPosition for new stories (except the last one)
        val newPositions = changedSteps.drop(1).map { it.first }
        for (i in 0 until newPositions.size - 1) {
            val currentPos = newPositions[i]
            val nextPos = newPositions[i + 1]
            mutable[currentPos]?.let { mutable[currentPos] = it.copy(nextPosition = nextPos) }
            val idx = changedSteps.indexOfFirst { it.first == currentPos }
            if (idx >= 0) {
                changedSteps[idx] = currentPos to mutable[currentPos]!!
            }
        }

        // Update the story that was after the original to point back to the last new story
        if (next != null) {
            mutable[next]?.let { nextStory ->
                val updatedNextStory = nextStory.copy(previousPosition = lastNewPosition)
                mutable[next] = updatedNextStory
                changedSteps.add(next to updatedNextStory)
            }
        }

        return lastNewPosition.toInt() to StoryState(
            stories = mutable,
            lastEdit = LastEdit.BulkEdition(changedSteps),
            focus = lastNewPosition
        )
    }

    /**
     * Deletes one story steps.
     */
    fun deleteStory(
        deleteInfo: Action.DeleteStory,
        history: Map<Double, StoryStep>,
        documentId: String
    ): StoryState? {
        val step = deleteInfo.storyStep
        val parentId = step.parentId
        val mutableSteps = history.toMutableMap()

        return if (parentId == null) {
            // Update position references before removing
            val prevPos = step.previousPosition
            val nextPos = step.nextPosition

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

            val previousFocus: Double? =
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

    fun eraseStory(deleteInfo: Action.EraseStory, history: Map<Double, StoryStep>): StoryState {
        val mutableSteps = history.toMutableMap()
        val deletedStep = deleteInfo.storyStep

        // Get position references before removing
        val prevPos = deletedStep.previousPosition
        val nextPos = deletedStep.nextPosition

        mutableSteps.remove(deleteInfo.position)

        // Update the next story's previousPosition to skip the deleted story
        if (nextPos != null) {
            mutableSteps[nextPos]?.let { nextStory ->
                mutableSteps[nextPos] = nextStory.copy(previousPosition = prevPos)
            }
        }

        val previousFocus: Double? =
            focusHandler.findPreviousFocus(deleteInfo.position, mutableSteps)

        var updatedPrevious: StoryStep? = null

        if (previousFocus != null) {
            mutableSteps[previousFocus]?.let { previous ->
                // Update the previous story's nextPosition to skip the deleted story
                // and merge the text from the deleted story
                val updated = previous.copy(
                    text = previous.text + deleteInfo.storyStep.text,
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
        positions: Iterable<Double>,
        stories: Map<Double, StoryStep>
    ): Pair<Map<Double, StoryStep>, Map<Double, StoryStep>> {
        val deleted = mutableMapOf<Double, StoryStep>()
        val newState = stories.toMutableMap()

        positions.forEach { position ->
            newState.remove(position)?.let { deletedStory ->
                deleted[position] = deletedStory
            }
        }

        return newState.normalizePositions() to deleted
    }

    fun previousTextStory(
        storyMap: Map<Double, StoryStep>,
        position: Double
    ): Pair<StoryStep, Double>? {
        val prevPosition = focusHandler.findPreviousFocus(position, storyMap) ?: return null
        val prevStory = storyMap[prevPosition] ?: return null
        return prevStory.copy(localId = GenerateId.generate()) to prevPosition
    }

    fun collapseItem(
        storyMap: Map<Double, StoryStep>,
        position: Double
    ): StoryState {
        val mutable = storyMap.toMutableMap()
        storyMap[position]?.let { step ->
            val tagInfo = setOf(TagInfo(tag = Tag.COLLAPSED))
            mutable[position] = step.copy(tags = step.tags.merge(tagInfo))
        }

        val sortedPositions = storyMap.keys.sorted().filter { it > position }
        var nextHeaderFound = false

        for (pos in sortedPositions) {
            if (nextHeaderFound) break
            val step = storyMap[pos]

            if (step != null) {
                if (step.tags.any { it.tag.isTitle() }) {
                    nextHeaderFound = true
                } else {
                    val tagInfo = setOf(TagInfo(tag = Tag.HIDDEN_HX))
                    mutable[pos] = step.copy(tags = step.tags.merge(tagInfo))
                }
            }
        }

        return StoryState(
            mutable.toMap(),
            lastEdit = LastEdit.LineEdition(position, storyMap[position]!!),
            focus = position
        )
    }

    /**
     * This method expands a section of the document like a subtitle.
     */
    fun expandItem(
        storyMap: Map<Double, StoryStep>,
        position: Double
    ): StoryState {
        val mutable = storyMap.toMutableMap()
        storyMap[position]?.let { step ->
            mutable[position] =
                step.copy(tags = step.tags.filterNotTo(mutableSetOf()) { it.tag == Tag.COLLAPSED })
        }

        val sortedPositions = storyMap.keys.sorted().filter { it > position }
        var nextHeaderFound = false

        for (pos in sortedPositions) {
            if (nextHeaderFound) break
            val step = storyMap[pos]

            if (step != null) {
                if (step.tags.any { it.tag.isTitle() }) {
                    nextHeaderFound = true
                } else {
                    val tagInfo = step.tags.filterNotTo(mutableSetOf()) { it.tag == Tag.HIDDEN_HX }
                    mutable[pos] = step.copy(tags = tagInfo)
                }
            }
        }

        return StoryState(
            mutable.toMap(),
            lastEdit = LastEdit.LineEdition(position, storyMap[position]!!),
            focus = position
        )
    }
}

private fun defaultLineBreakMap(storyType: StoryType): StoryType =
    when (storyType) {
        StoryTypes.TITLE.type -> StoryTypes.TEXT.type

        else -> storyType
    }
