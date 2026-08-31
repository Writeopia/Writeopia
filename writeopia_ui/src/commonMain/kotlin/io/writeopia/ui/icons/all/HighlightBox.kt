package io.writeopia.ui.icons.all
/*
* Converted using https://composables.com/svgtocompose
*/

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

internal val HighlightBox: ImageVector
    get() {
        if (_HighlightBox != null) {
            return _HighlightBox!!
        }
        _HighlightBox = ImageVector.Builder(
            name = "io.writeopia.ui.icons.all.HighlightBox",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            // Outer box with dashed corners
            path(
                fill = null,
                fillAlpha = 1.0f,
                stroke = SolidColor(Color(0xFF000000)),
                strokeAlpha = 1.0f,
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 1.0f,
                pathFillType = PathFillType.NonZero
            ) {
                // Top-left corner
                moveTo(4f, 8f)
                verticalLineTo(5f)
                curveTo(4f, 4.45f, 4.45f, 4f, 5f, 4f)
                horizontalLineTo(8f)
            }
            path(
                fill = null,
                fillAlpha = 1.0f,
                stroke = SolidColor(Color(0xFF000000)),
                strokeAlpha = 1.0f,
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 1.0f,
                pathFillType = PathFillType.NonZero
            ) {
                // Top-right corner
                moveTo(16f, 4f)
                horizontalLineTo(19f)
                curveTo(19.55f, 4f, 20f, 4.45f, 20f, 5f)
                verticalLineTo(8f)
            }
            path(
                fill = null,
                fillAlpha = 1.0f,
                stroke = SolidColor(Color(0xFF000000)),
                strokeAlpha = 1.0f,
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 1.0f,
                pathFillType = PathFillType.NonZero
            ) {
                // Bottom-right corner
                moveTo(20f, 16f)
                verticalLineTo(19f)
                curveTo(20f, 19.55f, 19.55f, 20f, 19f, 20f)
                horizontalLineTo(16f)
            }
            path(
                fill = null,
                fillAlpha = 1.0f,
                stroke = SolidColor(Color(0xFF000000)),
                strokeAlpha = 1.0f,
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 1.0f,
                pathFillType = PathFillType.NonZero
            ) {
                // Bottom-left corner
                moveTo(8f, 20f)
                horizontalLineTo(5f)
                curveTo(4.45f, 20f, 4f, 19.55f, 4f, 19f)
                verticalLineTo(16f)
            }
        }.build()
        return _HighlightBox!!
    }

private var _HighlightBox: ImageVector? = null
