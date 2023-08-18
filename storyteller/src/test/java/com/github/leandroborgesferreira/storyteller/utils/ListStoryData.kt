package com.github.leandroborgesferreira.storyteller.utils

import com.github.leandroborgesferreira.storyteller.models.story.StoryStep
import com.github.leandroborgesferreira.storyteller.model.story.StoryTypes

object ListStoryData {

    fun imageGroup(): List<StoryStep> = buildList {
        add(
            StoryStep(
                localId = "0",
                type = StoryTypes.GROUP_IMAGE.type,
                steps = listOf(
                    StoryStep(
                        localId = "1",
                        type = StoryTypes.IMAGE.type,
                        parentId = "0"
                    ),

                    StoryStep(
                        localId = "2",
                        type = StoryTypes.IMAGE.type,
                        parentId = "0"
                    ),

                    StoryStep(
                        localId = "3",
                        type = StoryTypes.IMAGE.type,
                        parentId = "0"
                    )
                )
            )
        )
        add(
            StoryStep(
                localId = "4",
                type = StoryTypes.IMAGE.type,
            )
        )
    }

    fun spaces(): List<StoryStep> = buildList {
        add(
            StoryStep(
                localId = "1",
                type = StoryTypes.SPACE.type,
            )
        )
        add(
            StoryStep(
                localId = "2",
                type = StoryTypes.SPACE.type,
            )
        )
        add(
            StoryStep(
                localId = "2",
                type = StoryTypes.SPACE.type,
            )
        )
    }

    fun spacedImageStepsList(): List<StoryStep> = buildList {
        add(
            StoryStep(
                localId = "1",
                type = StoryTypes.IMAGE.type,
            )
        )
        add(
            StoryStep(
                localId = "pamsdplams",
                type = StoryTypes.SPACE.type,
            )
        )
        add(
            StoryStep(
                localId = "3",
                type = StoryTypes.IMAGE.type,
            )
        )
        add(
            StoryStep(
                localId = "askndpalsd",
                type = StoryTypes.SPACE.type,
            )
        )
        add(
            StoryStep(
                localId = "5",
                type = StoryTypes.IMAGE.type,
            )
        )
    }
}
