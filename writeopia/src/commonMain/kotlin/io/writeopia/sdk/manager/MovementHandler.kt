package io.writeopia.sdk.manager

import io.writeopia.sdk.model.action.Action
import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.sdk.utils.extensions.toEditState

/**
 * Class responsible to handle move requests of Stories. This class handles the logic to move a
 * Story to another position, when a Story is grouped together with another one and when a
 * Story is separated from a group.
 */
class MovementHandler {

    fun merge(stories: Map<Double, StoryStep>, info: Action.Merge): Map<Double, List<StoryStep>> {
        val sender = info.sender
        val receiver = info.receiver
        val positionTo = info.positionTo
        val positionFrom = info.positionFrom

        // This state should be allowed
        if (info.positionFrom == info.positionTo) return stories.toEditState()

        val mutableHistory = stories.toEditState()
        val receiverStepList = mutableHistory[positionTo]
        receiverStepList?.plus(sender.copy(parentId = receiver.parentId))?.let { newList ->
            mutableHistory[positionTo] = newList
        }

        if (sender.parentId == null) {
            mutableHistory.remove(positionFrom)
        } else {
            val fromGroup = mutableHistory[positionFrom]?.first()
            val newList =
                fromGroup?.steps?.filter { storyUnit -> storyUnit.localId != sender.localId }

            if (newList != null) {
                mutableHistory[positionFrom] = listOf(fromGroup.copy(steps = newList))
            }
        }

        return mutableHistory
    }

    fun move(stories: Map<Double, StoryStep>, move: Action.Move): Map<Double, StoryStep> {
//        val mutable = stories.toMutableMap()
//
//        val movedStories = mutable[move.positionFrom]?.let { moveStory ->
//            if (move.storyStep.parentId == null) {
//                mutable.remove(move.positionFrom)
//            } else {
//                val fromGroup = mutable[move.positionFrom]
//                val newList = fromGroup?.steps?.filter { storyUnit ->
//                    storyUnit.localId != move.storyStep.localId
//                }
//
//                if (newList != null) {
//                    mutable[move.positionFrom] = fromGroup.copy(steps = newList)
//                }
//            }
//
//            mutable.addElementInPosition(moveStory.copy(parentId = null), move.positionTo)
//        } ?: stories
//
//        return movedStories

        if (move.positionFrom == move.positionTo) return stories

        if (move.positionFrom == move.positionTo + 1) return stories

        return moveStories(stories, setOf(move.positionFrom), move.positionTo)
    }

    fun move(stories: Map<Double, StoryStep>, move: Action.BulkMove): Map<Double, StoryStep> = moveStories(
        stories,
        move.positionFrom,
        move.positionTo
    )

    private fun moveStories(stories: Map<Double, StoryStep>, from: Set<Double>, to: Double): Map<Double, StoryStep> {
        val mutable = stories.toMutableMap()

        // Collect stories to move and their data before removal
        val storiesToMove = from.sorted().mapNotNull { position ->
            mutable[position]?.let { position to it }
        }

        if (storiesToMove.isEmpty()) return stories

        // Step 1: Remove stories from original positions and update neighbors
        for ((position, story) in storiesToMove) {
            val prevPos = story.previousPosition
            val nextPos = story.nextPosition

            mutable.remove(position)

            // Update the previous story's nextPosition to skip this story
            if (prevPos != null) {
                mutable[prevPos]?.let { prevStory ->
                    mutable[prevPos] = prevStory.copy(nextPosition = nextPos)
                }
            }

            // Update the next story's previousPosition to skip this story
            if (nextPos != null) {
                mutable[nextPos]?.let { nextStory ->
                    mutable[nextPos] = nextStory.copy(previousPosition = prevPos)
                }
            }
        }

        // Step 2: Find insertion point and calculate new positions
        val storyAtTo = mutable[to]
        val nextAfterTo = storyAtTo?.nextPosition

        // Calculate new positions for moved stories between 'to' and 'nextAfterTo'
        val startPos = to
        val endPos = nextAfterTo ?: (to + storiesToMove.size + 1)
        val gap = (endPos - startPos) / (storiesToMove.size + 1)

        // Step 3: Insert stories at new positions with updated references
        var previousPosition: Double = to
        val newPositions = mutableListOf<Double>()

        for ((index, pair) in storiesToMove.withIndex()) {
            val (_, story) = pair
            val newPos = startPos + gap * (index + 1)
            newPositions.add(newPos)

            val newNextPos = if (index < storiesToMove.size - 1) {
                startPos + gap * (index + 2)
            } else {
                nextAfterTo
            }

            mutable[newPos] = story.copy(
                previousPosition = previousPosition,
                nextPosition = newNextPos
            )

            previousPosition = newPos
        }

        // Step 4: Update the story at 'to' to point to the first moved story
        if (storyAtTo != null && newPositions.isNotEmpty()) {
            mutable[to] = storyAtTo.copy(nextPosition = newPositions.first())
        }

        // Step 5: Update the story after insertion to point back to the last moved story
        if (nextAfterTo != null && newPositions.isNotEmpty()) {
            mutable[nextAfterTo]?.let { nextStory ->
                mutable[nextAfterTo] = nextStory.copy(previousPosition = newPositions.last())
            }
        }

        return mutable
    }
}

fun Action.Move.fixMove(): Action.Move =
    if (this.positionTo < this.positionFrom) {
        this.copy(positionTo = this.positionTo)
    } else {
        this.copy(positionTo = this.positionTo)
    }
