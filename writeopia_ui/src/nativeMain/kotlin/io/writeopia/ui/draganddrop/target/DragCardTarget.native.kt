package io.writeopia.ui.draganddrop.target

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import io.writeopia.sdk.model.draganddrop.DropInfo

@Composable
actual fun DragCardTarget(
    modifier: Modifier,
    dataToDrop: DropInfo,
    showIcon: Boolean,
    position: Int,
    dragIconWidth: Dp,
    iconTintColor: Color,
    iconTintOnHover: Color,
    onIconClick: () -> Unit,
    content: @Composable BoxScope.() -> Unit
) {
    DragCardTargetMobile(
        modifier,
        dataToDrop,
        showIcon,
        position,
        dragIconWidth,
        iconTintColor,
        iconTintOnHover,
        content
    )
}
