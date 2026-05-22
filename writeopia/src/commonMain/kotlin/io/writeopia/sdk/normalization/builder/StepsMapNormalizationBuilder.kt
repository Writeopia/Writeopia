package io.writeopia.sdk.normalization.builder

import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.sdk.models.story.StoryTypes
import io.writeopia.sdk.normalization.merge.MergeLogic
import io.writeopia.sdk.normalization.merge.MergeNormalization
import io.writeopia.sdk.normalization.merge.StepsMergerCoordinator
import io.writeopia.sdk.normalization.merge.steps.StepToStepMerger
import io.writeopia.sdk.utils.alias.UnitsMapTransformation
import io.writeopia.sdk.utils.alias.UnitsNormalizationMap

class StepsMapNormalizationBuilder {

    companion object {
        fun reduceNormalizations(
            buildFunc: StepsMapNormalizationBuilder.() -> Unit
        ): UnitsNormalizationMap =
            StepsMapNormalizationBuilder().apply(buildFunc).build()
    }

    private var mergeNormalization: ((Map<Double, List<StoryStep>>) -> Map<Double, StoryStep>)? = null
    private val normalizations: MutableList<UnitsMapTransformation> = mutableListOf()

    /**
     * Adds a normalization.
     */
    fun addNormalization(
        normalization: UnitsMapTransformation
    ): StepsMapNormalizationBuilder = apply {
        normalizations.add(normalization)
    }

    fun addMergeNormalization(merge: (Map<Double, List<StoryStep>>) -> Map<Double, StoryStep>) {
        mergeNormalization = merge
    }

    /**
     * Provides some useful normalizers. Use this to get a plug and play experience.
     */
    fun defaultNormalizers() {
        val mergeNormalization = MergeNormalization.build {
            addMerger(
                StepsMergerCoordinator(
                    typeOfStep = StoryTypes.IMAGE.type,
                    typeOfGroup = StoryTypes.GROUP_IMAGE.type
                )
            )
            addMerger(
                StepsMergerCoordinator(
                    stepMerger = StepToStepMerger(),
                    typeOfStep = StoryTypes.TEXT.type,
                    typeOfGroup = null,
                    mergeLogic = MergeLogic::create
                )
            )
        }

        this.mergeNormalization = mergeNormalization::mergeSteps
    }

    private fun build(): UnitsNormalizationMap = { units ->
        val merged = mergeNormalization!!.invoke(units)

        normalizations.takeIf { it.isNotEmpty() }
            ?.let(::reduceNormalizations)
            ?.invoke(merged)
            ?: merged
    }

    private fun reduceNormalizations(
        normalizations: Iterable<UnitsMapTransformation>
    ): UnitsMapTransformation =
        normalizations.reduce { fn, gn -> { stories -> gn(fn(stories)) } }
}
