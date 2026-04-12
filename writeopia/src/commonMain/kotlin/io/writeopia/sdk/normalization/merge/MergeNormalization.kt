package io.writeopia.sdk.normalization.merge

import io.writeopia.sdk.models.story.StoryStep

/**
 *
 */
class MergeNormalization(private val stepMergers: List<StepsMergerCoordinator>) {

    /**
     * When using the Map version of the normalizer, it is necessary to end up with
     * Map<Int, StoryUnit> instead of Map<Int, List<StoryUnit>> so if some list contains more than
     * one item, this means that some incorrect merge was made. No elements that can't be merged
     * should be together. This normalizer removes the elements that could not be merged.
     */
    fun mergeSteps(stories: Map<Int, List<StoryStep>>): Map<Int, StoryStep> =
        stories.mapValues { (_, steps) -> reducePossibleSteps(steps).first() }

    /**
     * Note that it may happen that some elements in the same position may not be able to be
     * merged, that's why this method returns a List<StoryStep]> instead of StoryStep].
     */
    private fun reducePossibleSteps(steps: List<StoryStep>): List<StoryStep> = steps.fold(mutableListOf()) { acc, storyStep ->
        val lastStep = acc.lastOrNull()

        if (lastStep?.type != null &&
            stepMergers.any { it.canMerge(lastStep, storyStep) }
        ) {
            acc.apply {
                acc.removeLast()
                acc.add(
                    stepMergers
                        .first { merger -> merger.canMerge(lastStep, storyStep) }
                        .merge(lastStep, storyStep)
                )
            }
        } else {
            acc.apply { add(storyStep) }
        }
    }

    companion object {
        fun build(buildFunc: Builder.() -> Unit) = Builder().apply(buildFunc).build()
    }

    class Builder internal constructor() {
        private val stepMergers: MutableList<StepsMergerCoordinator> = mutableListOf()

        fun addMerger(stepMerger: StepsMergerCoordinator): Builder = apply {
            stepMergers.add(stepMerger)
        }

        fun build(): MergeNormalization = MergeNormalization(stepMergers)
    }
}
