package io.writeopia.normalization.merge

import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.sdk.models.story.StoryType
import io.writeopia.sdk.models.story.StoryTypes
import io.writeopia.sdk.normalization.merge.MergeNormalization
import io.writeopia.sdk.normalization.merge.StepsMergerCoordinator
import io.writeopia.sdk.normalization.merge.steps.StepToStepMerger
import io.writeopia.utils.MapStoryData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MergeNormalizationTest {

    @Test
    fun `it should be possible to merge a group of images`() {
        val mergeNormalization = MergeNormalization.build {
            addMerger(
                StepsMergerCoordinator(
                    typeOfStep = StoryTypes.IMAGE.type,
                    typeOfGroup = StoryTypes.GROUP_IMAGE.type
                )
            )
        }

        val mergedStep = mergeNormalization.mergeSteps(MapStoryData.imageSimpleGroup())

        assertEquals(1, mergedStep.size)
        assertTrue(mergedStep[0.0]?.isGroup ?: false)
    }

    @Test
    fun `it should be possible to merge a group of images with messages in the list`() {
        val mergeNormalization = MergeNormalization.build {
            addMerger(
                StepsMergerCoordinator(
                    typeOfStep = StoryTypes.IMAGE.type,
                    typeOfGroup = StoryTypes.GROUP_IMAGE.type
                )
            )
        }

        val mergedStep = mergeNormalization.mergeSteps(MapStoryData.stepsList())

        assertEquals(3, mergedStep.size)
        assertTrue(mergedStep[0.0]?.isGroup ?: false)
        assertTrue(mergedStep[1.0] is StoryStep)
        assertTrue(mergedStep[2.0] is StoryStep)
        assertEquals("group_image", mergedStep[0.0]!!.type.name)
    }

    @Test
    fun `it should be possible to merge a group of messages`() {
        val mergeNormalization = MergeNormalization.build {
            addMerger(
                StepsMergerCoordinator(
                    stepMerger = StepToStepMerger(),
                    typeOfStep = StoryTypes.TEXT.type
                )
            )
        }

        val mergedStep =
            mergeNormalization.mergeSteps(MapStoryData.twoGroupsImageList())

        assertEquals(1, mergedStep.size, "2 image groups should have been merged into one")
        assertTrue(
            mergedStep[0.0]?.isGroup ?: false,
            "the first story unit should still be a GroupStep"
        )
        assertEquals(
            StoryTypes.GROUP_IMAGE.type,
            mergedStep[0.0]!!.type,
            "the first story unit should still be a GroupImage"
        )
    }

    @Test
    fun `it should skip messages without mergers`() {
        val mergeNormalization = MergeNormalization.build {
            addMerger(
                StepsMergerCoordinator(
                    typeOfStep = StoryTypes.IMAGE.type,
                    typeOfGroup = StoryTypes.GROUP_IMAGE.type
                )
            )
        }

        val last = listOf(
            StoryStep(
                localId = "6",
                type = StoryType(
                    "unknown",
                    -1
                ),
            )
        )

        val steps = MapStoryData.stepsList()

        val mergedStep = mergeNormalization.mergeSteps(steps + Pair(steps.size.toDouble(), last))
        assertEquals(last.first(), mergedStep[(mergedStep.size - 1).toDouble()])
    }
//
//    @Test
//    @Ignore("Evaluate if the image should actually go to beginning of the group")
//    fun `the image always goes to the beginning of a group message`() {
//        val mergeNormalization = MergeNormalization.build {
//            addMerger(
//                StepsMergerCoordinator(
//                    typeOfStep = StoryTypes.IMAGE.type,
//                    typeOfGroup = StoryTypes.GROUP_IMAGE.type
//                )
//            )
//        }
//
//        val last = StoryStep(
//            localId = "6",
//            type = StoryTypes.IMAGE.type,
//        )
//
//        val story = listOf(
//            StoryStep(
//                localId = "1",
//                type = StoryTypes.GROUP_IMAGE.type,
//                steps = listOf(
//                    StoryStep(
//                        localId = "2",
//                        type = StoryTypes.IMAGE.type,
//                    ),
//                    last
//                )
//            ),
//        ).associateWithPosition()
//
//        val mergedStep = mergeNormalization.mergeSteps(story.toEditState())
//
//        assertEquals(last.localId, mergedStep[0]!!.steps.first().localId)
//    }
}
