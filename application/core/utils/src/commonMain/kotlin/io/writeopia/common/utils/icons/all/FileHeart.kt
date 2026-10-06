package io.writeopia.common.utils.icons.all
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

internal val FileHeart: ImageVector
    get() {
        if (_FileHeart != null) {
            return _FileHeart!!
        }
        _FileHeart = ImageVector.Builder(
            name = "io.writeopia.common.utils.icons.getFileHeart",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
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
                moveTo(4f, 22f)
                horizontalLineToRelative(14f)
                arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = false, 2f, -2f)
                verticalLineTo(7f)
                lineToRelative(-5f, -5f)
                horizontalLineTo(6f)
                arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = false, -2f, 2f)
                verticalLineToRelative(2f)
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
                moveTo(14f, 2f)
                verticalLineToRelative(4f)
                arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = false, 2f, 2f)
                horizontalLineToRelative(4f)
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
                moveTo(10.29f, 10.7f)
                arcToRelative(2.43f, 2.43f, 0f, isMoreThanHalf = false, isPositiveArc = false, -2.66f, -0.52f)
                curveToRelative(-0.29f, 0.12f, -0.56f, 0.3f, -0.78f, 0.53f)
                lineToRelative(-0.35f, 0.34f)
                lineToRelative(-0.35f, -0.34f)
                arcToRelative(2.43f, 2.43f, 0f, isMoreThanHalf = false, isPositiveArc = false, -2.65f, -0.53f)
                curveToRelative(-0.3f, 0.12f, -0.56f, 0.3f, -0.79f, 0.53f)
                curveToRelative(-0.95f, 0.94f, -1f, 2.53f, 0.2f, 3.74f)
                lineTo(6.5f, 18f)
                lineToRelative(3.6f, -3.55f)
                curveToRelative(1.2f, -1.21f, 1.14f, -2.8f, 0.19f, -3.74f)
                close()
            }
        }.build()
        return _FileHeart!!
    }

private var _FileHeart: ImageVector? = null
