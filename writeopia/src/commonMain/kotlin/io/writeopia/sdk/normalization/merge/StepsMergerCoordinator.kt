package io.writeopia.sdk.normalization.merge

import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.sdk.models.story.StoryType
import io.writeopia.sdk.normalization.merge.steps.StepToGroupMerger

typealias MergeLogicFunction = (StoryStep, StoryStep, Int, Int?) -> Boolean

class StepsMergerCoordinator(
    private val stepMerger: StepMerger = StepToGroupMerger(),
    private val typeOfStep: StoryType,
    private val typeOfGroup: StoryType? = null,
    private val mergeLogic: MergeLogicFunction = MergeLogic::create
) {

    fun canMerge(step1: StoryStep, step2: StoryStep): Boolean =
        mergeLogic(step1, step2, typeOfStep.number, typeOfGroup?.number)

    fun merge(step1: StoryStep, step2: StoryStep): StoryStep =
        stepMerger.merge(step1, step2, typeOfGroup ?: typeOfStep)
}

object MergeLogic {

    fun create(
        step1: StoryStep,
        step2: StoryStep,
        typeOfStep: Int,
        typeOfGroup: Int?
    ): Boolean =
        (
            step1.type.number == typeOfStep &&
                step2.type.number == typeOfStep ||
                step1.type.number == typeOfGroup &&
                step2.type.number == typeOfStep ||
                step1.type.number == typeOfStep &&
                step2.type.number == typeOfGroup
        )
}
