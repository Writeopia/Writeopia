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
            selectionSize > 0 -> {
                val insertedSize = newText.length - (oldText.length - selectionSize)
                TextEdit(
                    start = selectionStart,
                    end = selectionEnd,
                    insertedSize = insertedSize.coerceAtLeast(0),
                )
            }

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

        val replacementSpans = if (edit.end > edit.start && edit.insertedSize > 0) {
            spans.filter { span ->
                span.expandable() &&
                    span.start <= edit.start &&
                    span.end >= edit.end
            }
        } else {
            emptyList()
        }

        val afterDeletion = if (edit.end > edit.start) {
            spans.flatMapTo(mutableSetOf()) { span ->
                deleteRange(span, edit.start, edit.end)
            }
        } else {
            spans
        }

        val recalculated = if (edit.insertedSize > 0) {
            afterDeletion.flatMapTo(mutableSetOf()) { span ->
                insertRange(span, edit.start, edit.insertedSize)
            }
        } else {
            afterDeletion
        }

        replacementSpans.forEach { original ->
            if (recalculated.none { span -> span.hasSameIdentity(original) }) {
                recalculated += SpanInfo.create(
                    start = edit.start,
                    end = edit.start + edit.insertedSize,
                    span = original.span,
                    extra = original.extra,
                )
            }
        }

        return recalculated
    }

    private fun deleteRange(
        span: SpanInfo,
        deleteStart: Int,
        deleteEnd: Int,
    ): Set<SpanInfo> {
        val deletedSize = deleteEnd - deleteStart

        if (span.end <= deleteStart) return setOf(span)
        if (span.start >= deleteEnd) return setOf(span.move(-deletedSize))

        val left = if (span.start < deleteStart) {
            SpanInfo.create(
                start = span.start,
                end = minOf(span.end, deleteStart),
                span = span.span,
                extra = span.extra,
            )
        } else {
            null
        }

        val right = if (span.end > deleteEnd) {
            SpanInfo.create(
                start = maxOf(span.start, deleteEnd) - deletedSize,
                end = span.end - deletedSize,
                span = span.span,
                extra = span.extra,
            )
        } else {
            null
        }

        return when {
            left != null && right != null -> setOf(
                SpanInfo.create(
                    start = left.start,
                    end = right.end,
                    span = span.span,
                    extra = span.extra,
                )
            )

            left != null -> setOf(left)
            right != null -> setOf(right)
            else -> emptySet()
        }
    }

    private fun insertRange(
        span: SpanInfo,
        position: Int,
        insertedSize: Int,
    ): Set<SpanInfo> {
        if (position < span.start) return setOf(span.move(insertedSize))
        if (position > span.end) return setOf(span)

        if (span.expandable()) {
            return setOf(span.copy(end = span.end + insertedSize))
        }

        if (position == span.start) return setOf(span.move(insertedSize))
        if (position == span.end) return setOf(span)

        return setOf(
            SpanInfo.create(
                start = span.start,
                end = position,
                span = span.span,
                extra = span.extra,
            ),
            SpanInfo.create(
                start = position + insertedSize,
                end = span.end + insertedSize,
                span = span.span,
                extra = span.extra,
            ),
        )
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
