package io.writeopia.sdk.manager

import io.writeopia.sdk.models.id.GenerateId
import io.writeopia.sdk.models.span.Intersection
import io.writeopia.sdk.models.span.Span
import io.writeopia.sdk.models.span.SpanInfo
import io.writeopia.sdk.models.story.StoryStep

object SpansHandler {

    fun toggleSpans(spanSet: Set<SpanInfo>, newSpan: SpanInfo): Set<SpanInfo> {
        return when {
            spanSet.contains(newSpan) -> normalizeSpans(spanSet - newSpan)

            !spanSet.any { it.hasSameIdentity(newSpan) } -> normalizeSpans(spanSet + newSpan)

            else -> {
                val currentSpan = spanSet
                    .filter { it.hasSameIdentity(newSpan) }
                    .firstOrNull { it.intersection(newSpan) != Intersection.OUTSIDE }
                    ?: return normalizeSpans(spanSet + newSpan)

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
                                currentSpan.span,
                                currentSpan.extra,
                            ),
                            SpanInfo.create(
                                newEnd,
                                currentEnd,
                                currentSpan.span,
                                currentSpan.extra,
                            ),
                        ).filter { it.size() > 0 }

                        normalizeSpans(removed + splitSpans)
                    }

                    Intersection.INTERSECT -> {
                        val removed = (spanSet - currentSpan)
                        val expandedSpan = (currentSpan + newSpan)
                        normalizeSpans(removed + expandedSpan)
                    }

                    Intersection.OUTSIDE -> normalizeSpans(spanSet + newSpan)

                    Intersection.INSIDE -> {
                        val removed = (spanSet - currentSpan)
                        normalizeSpans(removed + newSpan)
                    }

                    Intersection.MATCH -> normalizeSpans(spanSet - currentSpan)
                }
            }
        }
    }

    internal fun normalizeSpans(spanSet: Set<SpanInfo>): Set<SpanInfo> =
        spanSet
            .groupBy { span -> span.span to span.extra }
            .values
            .flatMapTo(mutableSetOf()) { group ->
                val normalized = mutableListOf<SpanInfo>()

                group.sortedBy { span -> span.start }.forEach { span ->
                    val previous = normalized.lastOrNull()
                    if (previous != null && span.start <= previous.end) {
                        normalized[normalized.lastIndex] = previous + span
                    } else {
                        normalized.add(span)
                    }
                }

                normalized
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
                    story.copy(spans = story.spans + newSpanInfo, localId = GenerateId.generate())
                } else {
                    story
                }
            }
        }
}
