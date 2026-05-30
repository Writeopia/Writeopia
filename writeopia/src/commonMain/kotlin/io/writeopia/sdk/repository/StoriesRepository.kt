package io.writeopia.sdk.repository

import io.writeopia.sdk.models.story.StoryStep

interface StoriesRepository {

    suspend fun history(): Map<Double, StoryStep>
}
