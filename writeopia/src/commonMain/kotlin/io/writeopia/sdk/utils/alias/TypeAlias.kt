package io.writeopia.sdk.utils.alias

import io.writeopia.sdk.models.story.StoryStep

typealias DocumentContent = Map<Double, StoryStep>

typealias UnitsNormalizationMap = (Map<Double, List<StoryStep>>) -> Map<Double, StoryStep>

internal typealias UnitsMapTransformation = (Map<Double, StoryStep>) -> Map<Double, StoryStep>
