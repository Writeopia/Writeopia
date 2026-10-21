package io.writeopia.ui.utils

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified

/**
 * A title over a colored or pictured header, outlined so it reads on any background. The outline
 * is the opposite of the text: dark on a dark theme, where the text is light, and light on a
 * light one. A stroked copy of [text] sits behind [content], which draws the text itself, as a
 * text or as a text field, with the same [style] so both line up.
 */
@Composable
fun OutlinedTitle(
    text: AnnotatedString,
    style: TextStyle,
    isDarkTheme: Boolean,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    content: @Composable () -> Unit,
) {
    val strokeWidth = with(LocalDensity.current) {
        // Half of the stroke shows outside the glyphs, the text covers the other half.
        if (style.fontSize.isSpecified) style.fontSize.toPx() * 0.14f else 4.dp.toPx()
    }

    Box(modifier = modifier) {
        Text(
            text = text,
            style = style.copy(
                color = titleOutlineColor(isDarkTheme),
                drawStyle = Stroke(
                    width = strokeWidth,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                ),
            ),
            maxLines = maxLines,
            overflow = overflow,
        )

        content()
    }
}

/** The outline of a title: the opposite of the text, which is light on a dark theme. */
fun titleOutlineColor(isDarkTheme: Boolean): Color = if (isDarkTheme) Color.Black else Color.White
