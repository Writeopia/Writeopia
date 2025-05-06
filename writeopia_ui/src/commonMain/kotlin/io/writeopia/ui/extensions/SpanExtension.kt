package io.writeopia.ui.extensions

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import io.writeopia.sdk.models.span.Span

fun Span.toSpanStyle(isDarkTheme: Boolean): SpanStyle =
    when (this) {
        Span.BOLD -> SpanStyle(fontWeight = FontWeight.Bold)
        Span.ITALIC -> SpanStyle(fontStyle = FontStyle.Italic)
        Span.UNDERLINE -> SpanStyle(textDecoration = TextDecoration.Underline)
        Span.HIGHLIGHT_YELLOW -> if (isDarkTheme) {
            SpanStyle(background = Color(0xFFF9A825))
        } else {
            SpanStyle(background = Color.Yellow)
        }

        Span.HIGHLIGHT_GREEN -> if (isDarkTheme) {
            SpanStyle(background = Color(0xFFEF9A9A))
        } else {
            SpanStyle(background = Color.Green)
        }

        Span.HIGHLIGHT_RED -> if(isDarkTheme) {
            SpanStyle(background = Color(0xFFD32F2F))
        } else {
            SpanStyle(background = Color(0xFFEF9A9A))
        }

        Span.NONE -> SpanStyle()
    }
