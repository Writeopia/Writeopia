package io.writeopia.ui.utils

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.PopupPositionProvider

/**
 * Places a popup right above the composable it belongs to, whatever the screen density. A fixed
 * pixel offset lands on top of the text on dense phone screens.
 */
object AboveAnchorPositionProvider : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset = IntOffset(
        x = anchorBounds.left,
        y = (anchorBounds.top - popupContentSize.height).coerceAtLeast(0)
    )
}
