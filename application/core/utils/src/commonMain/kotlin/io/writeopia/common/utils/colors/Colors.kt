package io.writeopia.common.utils.colors

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import io.writeopia.sdk.models.span.Span

val colors = listOf(
    Color.Blue.toArgb(),
    Color.Yellow.toArgb(),
    Color.DarkGray.toArgb(),
    Color.Red.toArgb(),
    Color.Magenta.toArgb(),
    Color.Gray.toArgb(),
    Color.Green.toArgb(),
    Color.Cyan.toArgb(),
    Color.Black.toArgb(),
    Color.White.toArgb(),
)

fun highlightColors(isDarkTheme: Boolean) =
    if (isDarkTheme) {
        listOf(
            Span.HIGHLIGHT_RED to Color(0xFFD32F2F),
            Span.HIGHLIGHT_GREEN to Color(0xFF2E7D32),
            Span.HIGHLIGHT_YELLOW to Color(0xFFF9A825)
        )
    } else {
        listOf(
            Span.HIGHLIGHT_RED to Color(0xFFE57373),
            Span.HIGHLIGHT_GREEN to Color.Green,
            Span.HIGHLIGHT_YELLOW to Color.Yellow
        )
    }
