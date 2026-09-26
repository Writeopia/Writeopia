package io.writeopia.ui.utils

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import io.writeopia.sdk.models.span.Span
import io.writeopia.sdk.models.span.SpanInfo
import io.writeopia.ui.extensions.toSpanStyle
import kotlin.math.min

object Spans {
    fun createStringWithSpans(
        text: String?,
        spans: Iterable<SpanInfo>,
        isDarkTheme: Boolean,
    ): AnnotatedString {
        val lastPosition = text?.length ?: 0

        return buildAnnotatedString {
            append(text.takeIf { it?.isNotEmpty() == true } ?: "")

            spans.filter { spanInfo -> spanInfo.span != Span.LINK }
                .forEach { spanInfo ->
                    addStyle(
                        spanInfo.span.toSpanStyle(isDarkTheme),
                        min(lastPosition, spanInfo.start),
                        min(lastPosition, spanInfo.end)
                    )
                }

            spans.filter { spanInfo -> spanInfo.span == Span.LINK }
                .forEach { spanInfo ->
                    val style = if (isDarkTheme) {
                        SpanStyle(
                            color = Color(0xFF9E9E9E),
                            textDecoration = TextDecoration.Underline
                        )
                    } else {
                        SpanStyle(
                            color = Color(0xFF9E9E9E),
                            textDecoration = TextDecoration.Underline
                        )
                    }

                    addStyle(
                        style,
                        min(lastPosition, spanInfo.start),
                        min(lastPosition, spanInfo.end)
                    )
                }
        }
    }

    fun recalculateSpans(
        spans: Set<SpanInfo>,
        oldText: String,
        newText: String,
        oldSelectionStart: Int,
        oldSelectionEnd: Int,
        newSelectionStart: Int,
    ): Set<SpanInfo> {
        if (oldText == newText) return spans

        val selectionStart = minOf(oldSelectionStart, oldSelectionEnd)
        val selectionEnd = maxOf(oldSelectionStart, oldSelectionEnd)
        val selectionSize = selectionEnd - selectionStart
        val sizeDifference = newText.length - oldText.length

        val edit = when {
            selectionSize > 0 -> TextEdit(
                start = selectionStart,
                end = selectionEnd,
                insertedSize =
                    (newText.length - (oldText.length - selectionSize)).coerceAtLeast(0),
            )

            sizeDifference > 0 -> TextEdit(
                start = selectionStart,
                end = selectionStart,
                insertedSize = sizeDifference,
            )

            sizeDifference < 0 -> {
                val removedSize = -sizeDifference
                val deleteStart = if (newSelectionStart < selectionStart) {
                    (selectionStart - removedSize).coerceAtLeast(0)
                } else {
                    selectionStart
                }

                TextEdit(
                    start = deleteStart,
                    end = (deleteStart + removedSize).coerceAtMost(oldText.length),
                    insertedSize = 0,
                )
            }

            else -> changedRange(oldText, newText)
        }

        return spans.flatMapTo(mutableSetOf()) { span ->
            recalculateSpan(span, edit)
        }
    }

    private fun recalculateSpan(
        span: SpanInfo,
        edit: TextEdit,
    ): Set<SpanInfo> {
        val deletedSize = edit.end - edit.start

        if (deletedSize == 0) {
            if (
                edit.start == span.end &&
                span.expandable() &&
                span.span != Span.COMMENT
            ) {
                return setOf(span.copy(end = span.end + edit.insertedSize))
            }

            if (edit.start <= span.start) return setOf(span.move(edit.insertedSize))
            if (edit.start >= span.end) return setOf(span)

            if (span.expandable()) {
                return setOf(span.copy(end = span.end + edit.insertedSize))
            }

            return setOf(
                SpanInfo.create(
                    span.start,
                    edit.start,
                    span.span,
                    span.extra,
                ),
                SpanInfo.create(
                    edit.start + edit.insertedSize,
                    span.end + edit.insertedSize,
                    span.span,
                    span.extra,
                ),
            ).filterTo(mutableSetOf()) { fragment -> fragment.end > fragment.start }
        }

        val delta = edit.insertedSize - deletedSize

        if (span.end <= edit.start) return setOf(span)
        if (span.start >= edit.end) return setOf(span.move(delta))

        val coversEdit = span.start <= edit.start && span.end >= edit.end
        if (coversEdit && (edit.insertedSize == 0 || span.expandable())) {
            val resized = span.copy(end = span.end + delta)
            return if (resized.end > resized.start) setOf(resized) else emptySet()
        }

        val fragments = mutableSetOf<SpanInfo>()

        if (span.start < edit.start) {
            fragments += SpanInfo.create(
                span.start,
                minOf(span.end, edit.start),
                span.span,
                span.extra,
            )
        }

        if (span.end > edit.end) {
            val rightStart = maxOf(span.start, edit.end) + delta
            val rightEnd = span.end + delta
            if (rightEnd > rightStart) {
                fragments += SpanInfo.create(
                    rightStart,
                    rightEnd,
                    span.span,
                    span.extra,
                )
            }
        }

        return fragments
    }

    private fun changedRange(oldText: String, newText: String): TextEdit {
        val prefix = oldText.commonPrefixWith(newText).length
        val maxSuffix = minOf(oldText.length - prefix, newText.length - prefix)
        var suffix = 0

        while (
            suffix < maxSuffix &&
            oldText[oldText.lastIndex - suffix] == newText[newText.lastIndex - suffix]
        ) {
            suffix++
        }

        return TextEdit(
            start = prefix,
            end = oldText.length - suffix,
            insertedSize = newText.length - prefix - suffix,
        )
    }

    private data class TextEdit(
        val start: Int,
        val end: Int,
        val insertedSize: Int,
    )

    fun recalculateSpans(spans: Set<SpanInfo>, position: Int, change: Int): Set<SpanInfo> {
        val toChangeSize = spans
            .filterTo(mutableSetOf()) { span ->
                span.isInside(position)
            }
        val sizeChanged = toChangeSize.mapTo(mutableSetOf()) { span ->
            if (change > 0 && span.expandable() || change < 0) {
                span.changeSize(change)
            } else {
                span
            }
        }

        val toMove = spans.filterTo(mutableSetOf()) { span -> span.isBefore(position) }
        val moved = toMove.map { span -> span.move(change) }

        return (spans - toChangeSize + sizeChanged - toMove + moved)
            .filterTo(mutableSetOf()) { span -> span.end > span.start }
    }
}
