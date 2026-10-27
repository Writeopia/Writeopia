package io.writeopia.sdk.utils.alias

import io.writeopia.sdk.models.story.StoryStep

typealias DocumentContent = Map<String, StoryStep>

typealias UnitsNormalizationMap = (Map<String, List<StoryStep>>) -> Map<String, StoryStep>

internal typealias UnitsMapTransformation = (Map<String, StoryStep>) -> Map<String, StoryStep>
