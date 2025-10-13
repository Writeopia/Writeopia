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

        return spans - toChangeSize + sizeChanged - toMove + moved
    }
}
