package io.writeopia.ui.draganddrop.target

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.writeopia.sdk.model.draganddrop.DropInfo

@Composable
expect fun DragRowTarget(
    modifier: Modifier = Modifier,
    dataToDrop: DropInfo,
    onClick: () -> Unit = {},
    showIcon: Boolean = true,
    position: Double,
    emptySpaceClick: () -> Unit = {},
    dragIconWidth: Dp = 16.dp,
    iconTintOnHover: Color = MaterialTheme.colorScheme.onBackground,
    iconTint: Color = Color.Gray,
    onDragStart: () -> Unit = {},
    onDragStop: () -> Unit = {},
    isHoldDraggable: Boolean,
    content: @Composable RowScope.() -> Unit,
)
