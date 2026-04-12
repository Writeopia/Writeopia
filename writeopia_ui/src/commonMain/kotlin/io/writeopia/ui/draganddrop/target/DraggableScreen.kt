package io.writeopia.ui.draganddrop.target

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.IntSize

@Composable
fun DraggableScreen(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val state = remember { DragTargetInfo() }
    CompositionLocalProvider(LocalDragTargetInfo provides state) {
        Box(modifier = modifier) {
            content()

            if (state.isDragging) {
                var targetSize by remember { mutableStateOf(IntSize.Zero) }
                var localOffset by remember { mutableStateOf(Offset.Zero) }
                Box(
                    modifier = Modifier
                        .onGloballyPositioned { layoutCoordinates ->
                            targetSize = layoutCoordinates.size
                            localOffset = layoutCoordinates.positionInWindow()
                        }
                        .graphicsLayer {
                            val offset =
                                (state.dragPosition + state.dragOffset) - localOffset
//                            scaleX = 0.8f
//                            scaleY = 0.8f
                            alpha = if (targetSize == IntSize.Zero) 0f else .90f
                            translationX = offset.x
                            translationY = offset.y
                        }
                ) {
                    state.draggableComposable?.invoke()
                }
            }
        }
    }
}
