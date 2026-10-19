package io.writeopia.sdk.serialization.extensions

import io.writeopia.sdk.models.story.StoryStepTombstone
import io.writeopia.sdk.serialization.data.StoryStepTombstoneApi

fun StoryStepTombstone.toApi(): StoryStepTombstoneApi =
    StoryStepTombstoneApi(stepId = stepId, documentId = documentId)
