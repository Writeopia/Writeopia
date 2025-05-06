package io.writeopia.ui.utils

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import io.writeopia.sdk.models.span.SpanInfo
import io.writeopia.ui.extensions.toSpanStyle
import kotlin.math.abs
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

            spans.forEach { spanInfo ->
                addStyle(
                    spanInfo.span.toSpanStyle(isDarkTheme),
                    min(lastPosition, spanInfo.start),
                    min(lastPosition, spanInfo.end)
                )
            }
        }
    }

    fun recalculateSpans(spans: Set<SpanInfo>, position: Int, change: Int): Set<SpanInfo> {
        val toChangeSize = spans.filterTo(mutableSetOf()) { span -> span.isInside(position) }
        val sizeChanged = toChangeSize.mapTo(mutableSetOf()) { span ->
            if (abs(change) > 0) {
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
