package io.writeopia.sdk.manager

import io.writeopia.sdk.model.action.Action
import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.sdk.utils.extensions.associateWithPosition
import io.writeopia.sdk.utils.extensions.toEditState

/**
 * Class responsible to handle move requests of Stories. This class handles the logic to move a
 * Story to another position, when a Story is grouped together with another one and when a
 * Story is separated from a group.
 */
class MovementHandler {

    fun merge(stories: Map<Int, StoryStep>, info: Action.Merge): Map<Int, List<StoryStep>> {
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

    fun move(stories: Map<Int, StoryStep>, move: Action.Move): Map<Int, StoryStep> {
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

        return moveStories(stories, move.positionFrom.let(::setOf), move.positionTo)
    }

    fun move(stories: Map<Int, StoryStep>, move: Action.BulkMove): Map<Int, StoryStep> = moveStories(
        stories,
        move.positionFrom,
        move.positionTo
    )

    private fun moveStories(stories: Map<Int, StoryStep>, from: Set<Int>, to: Int): Map<Int, StoryStep> {
        val mutable = stories.toMutableMap()
        val storiesToMove = from.mapNotNull { position -> mutable[position] }
        val moveAfter = mutable[to + 1]?.id ?: mutable

        from.forEach(mutable::remove)

        val storiesCollection = mutable.values

        val firstPart = storiesCollection.takeWhile { story -> story.id != moveAfter }
        val lastPart = storiesCollection.drop(firstPart.size)

        val resultAsList = firstPart + storiesToMove + lastPart

        return resultAsList.associateWithPosition()
    }
}

fun Action.Move.fixMove(): Action.Move =
    if (this.positionTo < this.positionFrom) {
        this.copy(positionTo = this.positionTo)
    } else {
        this.copy(positionTo = this.positionTo)
    }
