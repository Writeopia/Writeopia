package io.writeopia.ui.icons.all

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

internal val spotlight: ImageVector
    get() {
        if (_spotlight != null) return _spotlight!!

        _spotlight = ImageVector.Builder(
            name = "spotlight",
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
                moveTo(15.295f, 19.562f)
                lineTo(16f, 22f)
            }
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveToRelative(17f, 16f)
                lineToRelative(3.758f, 2.098f)
            }
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveToRelative(19f, 12.5f)
                lineToRelative(3.026f, -0.598f)
            }
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(7.61f, 6.3f)
                arcToRelative(3f, 3f, 0f, false, false, -3.92f, 1.3f)
                lineToRelative(-1.38f, 2.79f)
                arcToRelative(3f, 3f, 0f, false, false, 1.3f, 3.91f)
                lineToRelative(6.89f, 3.597f)
                arcToRelative(1f, 1f, 0f, false, false, 1.342f, -0.447f)
                lineToRelative(3.106f, -6.211f)
                arcToRelative(1f, 1f, 0f, false, false, -0.447f, -1.341f)
                close()
            }
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(8f, 9f)
                verticalLineTo(2f)
            }
        }.build()

        return _spotlight!!
    }

private var _spotlight: ImageVector? = null

