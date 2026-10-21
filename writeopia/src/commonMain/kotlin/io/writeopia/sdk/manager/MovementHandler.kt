package io.writeopia.sdk.manager

import io.writeopia.sdk.model.action.Action
import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.sdk.utils.NextPositionCalculator
import io.writeopia.sdk.utils.collections.toSortedMutableMap
import io.writeopia.sdk.utils.extensions.toEditState
import io.writeopia.sdk.utils.iterables.insertAfter
import io.writeopia.sdk.utils.iterables.nextKey

/**
 * Class responsible to handle move requests of Stories. This class handles the logic to move a
 * Story to another position, when a Story is grouped together with another one and when a
 * Story is separated from a group.
 *
 * A moved story lands between the story at the target position and the one after it, at an
 * intermediate position. No other story changes position.
 */
class MovementHandler {

    fun merge(stories: Map<Long, StoryStep>, info: Action.Merge): Map<Long, List<StoryStep>> {
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

    /**
     * Moves the story at [Action.Move.positionFrom] to right after [Action.Move.positionTo].
     * Nothing changes when the story is already there.
     */
    fun move(stories: Map<Long, StoryStep>, move: Action.Move): Map<Long, StoryStep> {
        if (move.positionFrom == move.positionTo) return stories

        // Already right after the target
        if (stories.nextKey(move.positionTo) == move.positionFrom) return stories

        return moveStories(stories, setOf(move.positionFrom), move.positionTo)
    }

    fun move(stories: Map<Long, StoryStep>, move: Action.BulkMove): Map<Long, StoryStep> = moveStories(
        stories,
        move.positionFrom,
        move.positionTo
    )

    private fun moveStories(stories: Map<Long, StoryStep>, from: Set<Long>, to: Long): Map<Long, StoryStep> {
        val mutable = stories.toSortedMutableMap()

        // Collect stories to move before removal
        val storiesToMove = from.sorted().mapNotNull { position ->
            mutable[position]?.let { position to it }
        }

        if (storiesToMove.isEmpty()) return stories

        storiesToMove.forEach { (position, _) -> mutable.remove(position) }

        // The moved stories go right after 'to', before whatever follows it once they are out;
        // the stories after are spread out first when there is no room for them.
        val insertion = mutable.insertAfter(storiesToMove.map { (_, story) -> story }, to)

        // Only the stories moved, spread out, and their old and new neighbours change links
        val touched = storiesToMove.map { (position, _) -> position } + insertion.keys + insertion.moved
        touched.forEach { position -> NextPositionCalculator.relinkAround(insertion.map, position) }
        return insertion.map
    }
}

fun Action.Move.fixMove(): Action.Move =
    if (this.positionTo < this.positionFrom) {
        this.copy(positionTo = this.positionTo)
    } else {
        this.copy(positionTo = this.positionTo)
    }
