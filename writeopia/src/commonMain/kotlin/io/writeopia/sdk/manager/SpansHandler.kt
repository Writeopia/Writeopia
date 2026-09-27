package io.writeopia.sdk.manager

import io.writeopia.sdk.models.id.GenerateId
import io.writeopia.sdk.models.span.Intersection
import io.writeopia.sdk.models.span.Span
import io.writeopia.sdk.models.span.SpanInfo
import io.writeopia.sdk.models.story.StoryStep

object SpansHandler {

    fun toggleSpans(spanSet: Set<SpanInfo>, newSpan: SpanInfo): Set<SpanInfo> {
        return when {
            spanSet.contains(newSpan) -> spanSet - newSpan

            !spanSet.any { it.hasSameIdentity(newSpan) } -> spanSet + newSpan

            else -> {
                val currentSpan = spanSet
                    .filter { it.hasSameIdentity(newSpan) }
                    .firstOrNull { it.intersection(newSpan) != Intersection.OUTSIDE }
                    ?: return spanSet + newSpan

                val intersection: Intersection = currentSpan.intersection(newSpan)

                return when (intersection) {
                    Intersection.CONTAINING -> {
                        val removed = spanSet - currentSpan
                        val splitSpans = setOf(
                            SpanInfo.create(
                                currentSpan.start,
                                newSpan.start,
                                currentSpan.span,
                                currentSpan.extra,
                            ),
                            SpanInfo.create(
                                newSpan.end,
                                currentSpan.end,
                                currentSpan.span,
                                currentSpan.extra,
                            ),
                        ).filter { it.size() > 0 }

                        removed + splitSpans
                    }

                    Intersection.INTERSECT -> {
                        val removed = spanSet - currentSpan
                        removed + (currentSpan + newSpan)
                    }

                    Intersection.OUTSIDE -> spanSet + newSpan

                    Intersection.INSIDE -> {
                        val removed = spanSet - currentSpan
                        removed + newSpan
                    }

                    Intersection.MATCH -> spanSet - currentSpan
                }
            }
        }
    }

    fun toggleSpansForManyStories(
        storySteps: Map<Double, StoryStep>,
        newSpan: Span,
        extra: String? = null,
    ): Map<Double, StoryStep> =
        if (
            storySteps.all { (_, story) ->
                story.spans.any { span -> span.span == newSpan && span.extra == extra }
            }
        ) {
            storySteps.mapValues { (_, story) ->
                val removedSpans = story.spans.filterTo(mutableSetOf()) { span ->
                    span.span != newSpan || span.extra != extra
                }
                story.copy(spans = removedSpans, localId = GenerateId.generate())
            }
        } else {
            storySteps.mapValues { (_, story) ->
                val text = story.text
                if (text?.isNotEmpty() == true) {
                    val newSpanInfo = SpanInfo.create(0, text.length, newSpan, extra)
                    val otherSpans = story.spans.filterTo(mutableSetOf()) { span ->
                        !span.hasSameIdentity(newSpanInfo)
                    }
                    story.copy(
                        spans = otherSpans + newSpanInfo,
                        localId = GenerateId.generate()
                    )
                } else {
                    story
                }
            }
        }
}
