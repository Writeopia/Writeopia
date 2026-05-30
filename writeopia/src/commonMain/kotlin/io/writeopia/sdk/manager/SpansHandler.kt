package io.writeopia.sdk.manager

import io.writeopia.sdk.models.id.GenerateId
import io.writeopia.sdk.models.span.Intersection
import io.writeopia.sdk.models.span.Span
import io.writeopia.sdk.models.span.SpanInfo
import io.writeopia.sdk.models.story.StoryStep

object SpansHandler {

    fun toggleSpans(spanSet: Set<SpanInfo>, newSpan: SpanInfo): Set<SpanInfo> {
        return when {
            spanSet.contains(newSpan) -> (spanSet - newSpan)

            !spanSet.any { it.span == newSpan.span } -> (spanSet + newSpan)

            else -> {
                val currentSpan = spanSet.first { it.span == newSpan.span }

                val intersection: Intersection = currentSpan.intersection(newSpan)

                return when (intersection) {
                    Intersection.CONTAINING -> {
                        val removed = (spanSet - currentSpan)

                        val currentStart = currentSpan.start
                        val currentEnd = currentSpan.end

                        val newStart = newSpan.start
                        val newEnd = newSpan.end

                        val splitSpans = setOf(
                            SpanInfo.create(
                                currentStart,
                                newStart,
                                currentSpan.span
                            ),
                            SpanInfo.create(
                                newEnd,
                                currentEnd,
                                currentSpan.span
                            ),
                        ).filter { it.size() > 0 }

                        removed + splitSpans
                    }

                    Intersection.INTERSECT -> {
                        val removed = (spanSet - currentSpan)
                        val expandedSpan = (currentSpan + newSpan)
                        removed + expandedSpan
                    }

                    Intersection.OUTSIDE -> spanSet + newSpan

                    Intersection.INSIDE -> {
                        val removed = (spanSet - currentSpan)
                        removed + newSpan
                    }

                    Intersection.MATCH -> (spanSet - currentSpan)
                }
            }
        }
    }

    fun toggleSpansForManyStories(
        storySteps: Map<Double, StoryStep>,
        newSpan: Span
    ): Map<Double, StoryStep> =
        if (storySteps.all { (_, story) -> story.spans.any { it.span == newSpan } }) {
            storySteps.mapValues { (_, story) ->
                val removedSpans = story.spans.filterTo(mutableSetOf()) { it.span != newSpan }
                story.copy(spans = removedSpans, localId = GenerateId.generate())
            }
        } else {
            storySteps.mapValues { (_, story) ->
                val text = story.text
                if (text?.isNotEmpty() == true) {
                    val newSpanInfo = SpanInfo.create(0, text.length, newSpan)
                    story.copy(spans = story.spans + newSpanInfo, localId = GenerateId.generate())
                } else {
                    story
                }
            }
        }
}
