package com.github.leandroborgesferreira.storyteller.normalization.merge.steps

import com.github.leandroborgesferreira.storyteller.model.story.StoryTypes
import com.github.leandroborgesferreira.storyteller.models.story.StoryStep
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.UUID

class StepToGroupMergerTest {

    @Test
    fun `a simple merge should be possible`() {
        val merger = StepToGroupMerger()

        val image1 = StoryStep(
            localId = "1",
            type = StoryTypes.IMAGE.type,
        )

        val image2 = StoryStep(
            localId = "2",
            type = StoryTypes.IMAGE.type,
        )

        val result = merger.merge(image1, image2, StoryTypes.GROUP_IMAGE.type)

        assertEquals("group_image", result.type.name)
        assertEquals(2, result.steps.size)
        assertEquals(result.id, result.steps[0].parentId)
        assertEquals(result.id, result.steps[1].parentId)
    }

    @Test
    fun `when merging 2 groups all inner steps should have the same parent ID`() {
        val merger = StepToGroupMerger()

        val parent1Id = UUID.randomUUID().toString()
        val parent2Id = UUID.randomUUID().toString()

        val group1 =
            StoryStep(
                id = parent1Id,
                type = StoryTypes.GROUP_IMAGE.type,
                steps = listOf(
                    StoryStep(
                        localId = "1",
                        type = StoryTypes.IMAGE.type,
                        parentId = parent1Id
                    ),
                    StoryStep(
                        localId = "2",
                        type = StoryTypes.IMAGE.type,
                        parentId = parent1Id
                    ),
                    StoryStep(
                        localId = "3",
                        type = StoryTypes.IMAGE.type,
                        parentId = parent1Id
                    )
                )
            )

        val group2 =
            StoryStep(
                id = parent2Id,
                type = StoryTypes.GROUP_IMAGE.type,
                steps = listOf(
                    StoryStep(
                        id = "11",
                        type = StoryTypes.IMAGE.type,
                        parentId = parent2Id
                    ),
                    StoryStep(
                        id = "22",
                        type = StoryTypes.IMAGE.type,
                        parentId = parent2Id
                    ),
                    StoryStep(
                        id = "33",
                        type = StoryTypes.IMAGE.type,
                        parentId = parent2Id
                    )
                )
            )


        val result = merger.merge(group1, group2, StoryTypes.GROUP_IMAGE.type)

        assertEquals(StoryTypes.GROUP_IMAGE.type, result.type)
        assertEquals(6, result.steps.size)

        result.steps.forEachIndexed { i, storyUnit ->
            assertEquals(
                "The step number $i should have the parent id: $parent1Id",
                parent1Id,
                storyUnit.parentId
            )
        }
    }
}
