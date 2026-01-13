package io.writeopia.common.utils.icons.all

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

internal val highlighter: ImageVector
    get() {
        if (_highlighter != null) return _highlighter!!

        _highlighter = ImageVector.Builder(
            name = "highlighter",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveToRelative(9f, 11f)
                lineToRelative(-6f, 6f)
                verticalLineToRelative(3f)
                horizontalLineToRelative(9f)
                lineToRelative(3f, -3f)
            }
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveToRelative(22f, 12f)
                lineToRelative(-4.6f, 4.6f)
                arcToRelative(2f, 2f, 0f, false, true, -2.8f, 0f)
                lineToRelative(-5.2f, -5.2f)
                arcToRelative(2f, 2f, 0f, false, true, 0f, -2.8f)
                lineTo(14f, 4f)
            }
        }.build()

        return _highlighter!!
    }

private var _highlighter: ImageVector? = null

