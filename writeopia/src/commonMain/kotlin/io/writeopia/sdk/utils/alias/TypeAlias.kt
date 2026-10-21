package io.writeopia.sdk.utils.alias

import io.writeopia.sdk.models.story.StoryStep

typealias DocumentContent = Map<Long, StoryStep>

typealias UnitsNormalizationMap = (Map<Long, List<StoryStep>>) -> Map<Long, StoryStep>

internal typealias UnitsMapTransformation = (Map<Long, StoryStep>) -> Map<Long, StoryStep>
