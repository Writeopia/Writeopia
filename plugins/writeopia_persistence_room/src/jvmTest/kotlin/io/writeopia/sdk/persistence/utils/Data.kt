package io.writeopia.sdk.persistence.utils

import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.sdk.models.story.StoryTypes

fun imageGroup() =
    mapOf(
        0.0 to StoryStep(
            localId = "1",
            type = StoryTypes.GROUP_IMAGE.type,
            steps = listOf(
                StoryStep(
                    localId = "2",
                    type = StoryTypes.IMAGE.type,
                ),
                StoryStep(
                    localId = "3",
                    type = StoryTypes.IMAGE.type,
                ),
                StoryStep(
                    localId = "4",
                    type = StoryTypes.IMAGE.type,
                )
            )
        ),
    )
