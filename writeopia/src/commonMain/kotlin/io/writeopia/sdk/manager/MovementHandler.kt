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

        // Collect stories to move before removal
        val storiesToMove = from.sorted().mapNotNull { position ->
            mutable[position]?.let { position to it }
        }

        if (storiesToMove.isEmpty()) return stories

        // Step 1: Remove stories from original positions and update neighbors
        for ((position, story) in storiesToMove) {
            val prevPos = story.previousPosition
            val nextPos = story.nextPosition

            mutable.remove(position)

            // Update neighbors to skip this story
            if (prevPos != null) {
                mutable[prevPos]?.let { mutable[prevPos] = it.copy(nextPosition = nextPos) }
            }
            if (nextPos != null) {
                mutable[nextPos]?.let { mutable[nextPos] = it.copy(previousPosition = prevPos) }
            }
        }

        // Step 2: Calculate intermediate positions between 'to' and its next
        val storyAtTo = mutable[to]
        val nextAfterTo = storyAtTo?.nextPosition
        val endPos = nextAfterTo ?: (to + storiesToMove.size + 1)

        // Step 3: Insert stories at intermediate positions
        var prevPos: Double = to
        val newPositions = mutableListOf<Double>()

        for ((index, pair) in storiesToMove.withIndex()) {
            val (_, story) = pair
            // Calculate intermediate position between previous and end
            val newPos = (prevPos + endPos) / 2.0
            newPositions.add(newPos)

            // Next position: either the next moved story's position or nextAfterTo
            val newNextPos = if (index < storiesToMove.size - 1) {
                // Will be updated when we insert the next story
                null
            } else {
                nextAfterTo
            }

            mutable[newPos] = story.copy(
                previousPosition = prevPos,
                nextPosition = newNextPos
            )

            // Update previous story to point to this one
            mutable[prevPos]?.let { mutable[prevPos] = it.copy(nextPosition = newPos) }

            prevPos = newPos
        }

        // Fix nextPosition for moved stories (except the last one)
        for (i in 0 until newPositions.size - 1) {
            val currentPos = newPositions[i]
            val nextPos = newPositions[i + 1]
            mutable[currentPos]?.let { mutable[currentPos] = it.copy(nextPosition = nextPos) }
        }

        // Step 4: Update the story after insertion to point back to the last moved story
        if (nextAfterTo != null && newPositions.isNotEmpty()) {
            mutable[nextAfterTo]?.let { mutable[nextAfterTo] = it.copy(previousPosition = newPositions.last()) }
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
