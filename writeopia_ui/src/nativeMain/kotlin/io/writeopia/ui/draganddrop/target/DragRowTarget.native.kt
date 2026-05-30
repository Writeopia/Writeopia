package io.writeopia.ui.draganddrop.target

import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import io.writeopia.sdk.model.draganddrop.DropInfo

@Composable
actual fun DragRowTarget(
    modifier: Modifier,
    dataToDrop: DropInfo,
    onClick: () -> Unit,
    showIcon: Boolean,
    position: Double,
    emptySpaceClick: () -> Unit,
    dragIconWidth: Dp,
    iconTintOnHover: Color,
    iconTint: Color,
    onDragStart: () -> Unit,
    onDragStop: () -> Unit,
    isHoldDraggable: Boolean,
    content: @Composable RowScope.() -> Unit
) {
    DragRowTargetMobile(
        modifier,
        dataToDrop,
        showIcon,
        position,
        emptySpaceClick,
        dragIconWidth,
        iconTintOnHover,
        iconTint,
        onDragStart,
        onDragStop,
        isHoldDraggable,
        content
    )
}
