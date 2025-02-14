package io.writeopia.common.utils.icons.all

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

internal val Command: ImageVector
	get() {
		if (_Command != null) {
			return _Command!!
		}
		_Command = ImageVector.Builder(
            name = "Command",
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
				moveTo(15f, 6f)
				verticalLineToRelative(12f)
				arcToRelative(3f, 3f, 0f, isMoreThanHalf = true, isPositiveArc = false, 3f, -3f)
				horizontalLineTo(6f)
				arcToRelative(3f, 3f, 0f, isMoreThanHalf = true, isPositiveArc = false, 3f, 3f)
				verticalLineTo(6f)
				arcToRelative(3f, 3f, 0f, isMoreThanHalf = true, isPositiveArc = false, -3f, 3f)
				horizontalLineToRelative(12f)
				arcToRelative(3f, 3f, 0f, isMoreThanHalf = true, isPositiveArc = false, -3f, -3f)
			}
		}.build()
		return _Command!!
	}

private var _Command: ImageVector? = null
