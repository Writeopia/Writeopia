package io.writeopia.ui.utils

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.PopupPositionProvider

/**
 * Places a popup right above the composable it belongs to, whatever the screen density. A fixed
 * pixel offset lands on top of the text on dense phone screens. When there is no room above,
 * the popup goes below the anchor instead, and it never leaves the window sideways.
 */
object AboveAnchorPositionProvider : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset {
        val above = anchorBounds.top - popupContentSize.height
        val y = if (above >= 0) above else anchorBounds.bottom
        val maxX = (windowSize.width - popupContentSize.width).coerceAtLeast(0)
        val maxY = (windowSize.height - popupContentSize.height).coerceAtLeast(0)

        return IntOffset(
            x = anchorBounds.left.coerceIn(0, maxX),
            y = y.coerceIn(0, maxY)
        )
    }
}
