package io.writeopia.ui.icons.all

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

internal val aLargeSmall: ImageVector
    get() {
        if (_aLargeSmall != null) return _aLargeSmall!!

        _aLargeSmall = ImageVector.Builder(
            name = "aLargeSmall",
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
                moveToRelative(15f, 16f)
                lineToRelative(2.536f, -7.328f)
                arcToRelative(1.02f, 1.02f, 1f, false, true, 1.928f, 0f)
                lineTo(22f, 16f)
            }
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(15.697f, 14f)
                horizontalLineToRelative(5.606f)
            }
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveToRelative(2f, 16f)
                lineToRelative(4.039f, -9.69f)
                arcToRelative(0.5f, 0.5f, 0f, false, true, 0.923f, 0f)
                lineTo(11f, 16f)
            }
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(3.304f, 13f)
                horizontalLineToRelative(6.392f)
            }
        }.build()

        return _aLargeSmall!!
    }

private var _aLargeSmall: ImageVector? = null
