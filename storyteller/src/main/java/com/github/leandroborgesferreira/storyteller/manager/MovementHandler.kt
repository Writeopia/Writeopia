package com.github.leandroborgesferreira.storyteller.manager

import com.github.leandroborgesferreira.storyteller.model.action.Action
import com.github.leandroborgesferreira.storyteller.models.story.StoryStep
import com.github.leandroborgesferreira.storyteller.utils.alias.UnitsNormalizationMap
import com.github.leandroborgesferreira.storyteller.utils.extensions.toEditState

/**
 * Class responsible to handle move requests of Stories. This class handles the logic to move a
 * Story to another position, when a Story is grouped together with another one and when a
 * Story is separated from a group.
 */
class MovementHandler(private val stepsNormalizer: UnitsNormalizationMap) {

    fun merge(stories: Map<Int, StoryStep>, info: Action.Merge): Map<Int, List<StoryStep>> {
        val sender = info.sender
        val receiver = info.receiver
        val positionTo = info.positionTo
        val positionFrom = info.positionFrom

        //This state should be allowed
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
        val mutable = stories.toMutableMap()
        if (mutable[move.positionTo]?.type?.name != "space") throw IllegalStateException(
            "You can only move a story to an empty space"
        )

        val movedStories = mutable[move.positionFrom]?.let { moveStory ->
            mutable[move.positionTo] = moveStory.copy(parentId = null)

            if (move.storyStep.parentId == null) {
                mutable.remove(move.positionFrom)
            } else {
                val fromGroup = mutable[move.positionFrom]
                val newList = fromGroup?.steps?.filter { storyUnit ->
                    storyUnit.localId != move.storyStep.localId
                }

                if (newList != null) {
                    mutable[move.positionFrom] = fromGroup.copy(steps = newList)
                }
            }

            mutable
        } ?: stories

        return stepsNormalizer(movedStories.toEditState())
    }
}