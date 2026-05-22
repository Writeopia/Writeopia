package io.writeopia.ui.draganddrop.target

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.writeopia.sdk.model.draganddrop.DropInfo

@Composable
expect fun DragCardTarget(
    modifier: Modifier = Modifier,
    dataToDrop: DropInfo,
    showIcon: Boolean = true,
    position: Double,
    dragIconWidth: Dp = 16.dp,
    iconTintColor: Color = Color.LightGray,
    iconTintOnHover: Color = MaterialTheme.colorScheme.onBackground,
    onIconClick: () -> Unit = {},
    onDragStart: () -> Unit = {},
    onDragStop: () -> Unit = {},
    content: @Composable BoxScope.() -> Unit
)
