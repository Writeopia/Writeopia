package io.writeopia.sdk.models.span

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

data class SpanInfo private constructor(
    val start: Int,
    val end: Int,
    val span: Span,
    val extra: Map<String, String> = emptyMap()
) {

    operator fun plus(spanInfo: SpanInfo) =
        if (spanInfo.span == spanInfo.span) {
            val minStart = min(this.start, spanInfo.start)
            val maxEnd = max(this.end, spanInfo.end)

            this.copy(start = minStart, end = maxEnd)
        } else {
            throw IllegalArgumentException(
                "The spans being summed are not the same. Span1: ${this.span.label}, Span2: ${spanInfo.span.label}"
            )
        }

    fun changeSize(amount: Int): SpanInfo = this.copy(end = end + amount)

    fun move(amount: Int): SpanInfo = this.copy(start = start + amount, end = end + amount)

    fun isInside(position: Int) = position in start..end

    fun isBefore(position: Int) = position < start

    /**
     *  Serialize the object as a string: "start:end:span"
     */
    fun toText(): String = "$start:$end:${span.toText()}"

    fun size() = abs(end - start)

    fun expandable() =
        when (this.span) {
            Span.LINK -> false
            else -> true
        }

    fun intersection(spanInfo: SpanInfo): Intersection {
        val (smaller, bigger) = orderSpansBySize(this, spanInfo)

        return when {
            smaller.start == bigger.start && smaller.end == bigger.end -> Intersection.MATCH

            isPartiallyInside(smaller, bigger) -> Intersection.INTERSECT

            isInside(smaller, bigger) ->
                if (this == bigger) Intersection.CONTAINING else Intersection.INSIDE

            else -> Intersection.OUTSIDE
        }
    }

    private fun sortedPositions(): Pair<Int, Int> = if (start < end) start to end else end to start

    private fun isPartiallyInside(smaller: SpanInfo, bigger: SpanInfo): Boolean {
        val (smallerStart, smallerEnd) = smaller.sortedPositions()
        val (biggerStart, biggerEnd) = bigger.sortedPositions()

        val insideFromStart =
            smallerStart < biggerStart && smallerEnd in (biggerStart + 1)..<biggerEnd

        val insideFromEnd = smallerEnd > biggerEnd && smallerStart in (biggerStart + 1)..<biggerEnd

        return insideFromStart || insideFromEnd
    }

    private fun isInside(smaller: SpanInfo, bigger: SpanInfo): Boolean {
        val (smallerStart, smallerEnd) = smaller.sortedPositions()
        val (biggerStart, biggerEnd) = bigger.sortedPositions()

        return smallerStart in biggerStart..biggerEnd &&
            smallerEnd in biggerStart..biggerEnd
    }

    companion object {
        fun orderSpansBySize(span1: SpanInfo, span2: SpanInfo): Pair<SpanInfo, SpanInfo> =
            if (span1.size() > span2.size()) {
                span2 to span1
            } else {
                span1 to span2
            }

        fun fromString(serialized: String): SpanInfo {
            val parts = serialized.split(":")
            require(parts.size == 3) { "Invalid serialized format" }

            val start = parts[0].toIntOrNull() ?: error("Invalid start value")
            val end = parts[1].toIntOrNull() ?: error("Invalid end value")
            val span = Span.textFromString(parts[2])

            return SpanInfo(start, end, span)
        }

        fun create(
            start: Int,
            end: Int,
            span: Span,
            extra: Map<String, String> = emptyMap()
        ): SpanInfo {
            val (realStart, realEnd) = if (start <= end) start to end else end to start

            return SpanInfo(realStart, realEnd, span, extra)
        }
    }
}

enum class Intersection {
    INSIDE,
    INTERSECT,
    OUTSIDE,
    CONTAINING,
    MATCH
}

enum class Span(val label: String) {
    BOLD("BOLD"),
    ITALIC("ITALIC"),
    UNDERLINE("UNDERLINE"),
    HIGHLIGHT_YELLOW("HIGHLIGHT"),
    HIGHLIGHT_GREEN("HIGHLIGHT_GREEN"),
    HIGHLIGHT_RED("HIGHLIGHT_RED"),
    LINK("LINK"),
    NONE("");

    fun toText() = this.label

    companion object {
        fun textFromString(label: String): Span = entries.find { option ->
            option.label.equals(label, ignoreCase = true)
        } ?: NONE
    }
}
