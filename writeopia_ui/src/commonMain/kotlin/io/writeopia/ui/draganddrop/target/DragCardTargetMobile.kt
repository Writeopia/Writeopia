package io.writeopia.ui.draganddrop.target

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import io.writeopia.ui.icons.WrSdkIcons
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import io.writeopia.sdk.model.draganddrop.DropInfo

@Composable
fun DragCardTargetMobile(
    modifier: Modifier,
    dataToDrop: DropInfo,
    showIcon: Boolean,
    position: String,
    dragIconWidth: Dp,
    iconTintColor: Color,
    iconTintOnHover: Color,
    dragOnLongPress: Boolean,
    content: @Composable BoxScope.() -> Unit
) {
    var currentPosition by remember { mutableStateOf(Offset.Zero) }
    var iconPosition by remember { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current
    var maxSize by remember { mutableStateOf(DpSize(0.dp, 0.dp)) }
    val currentState = LocalDragTargetInfo.current
    val haptic = LocalHapticFeedback.current
    // The gesture detectors outlive recompositions, so they read the latest card.
    val currentDataToDrop by rememberUpdatedState(dataToDrop)
    val currentContent by rememberUpdatedState(content)

    // The drag starts at a point in the window, both from the icon and from a long press on the card.
    val startDrag: (Offset) -> Unit = { start ->
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)

        currentState.dataToDrop = currentDataToDrop
        currentState.isDragging = true
        currentState.dragPosition = start
        currentState.ghostPosition = currentPosition
        currentState.draggableComposable = {
            Box(modifier = Modifier.size(maxSize)) {
                currentContent()
            }
        }
    }
    val dragBy: (PointerInputChange, Offset) -> Unit = { change, dragAmount ->
        change.consume()
        currentState.dragOffset += Offset(dragAmount.x, dragAmount.y)
    }
    val stopDrag: () -> Unit = {
        currentState.isDragging = false
        currentState.dragOffset = Offset.Zero
        currentState.ghostPosition = null
    }

    val longPressModifier = if (dragOnLongPress) {
        Modifier.pointerInput(Unit) {
            detectDragGesturesAfterLongPress(
                // The offset is relative to the card.
                onDragStart = { offset -> startDrag(currentPosition + offset) },
                onDrag = dragBy,
                onDragEnd = stopDrag,
                onDragCancel = stopDrag
            )
        }
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .onGloballyPositioned { layoutCoordinates ->
                currentPosition = layoutCoordinates.positionInWindow()
                // The layout size is in pixels, it must be converted to Dp, otherwise the dragged
                // copy becomes bigger than the original card in screens with density > 1.
                maxSize = with(density) { layoutCoordinates.size.toSize().toDpSize() }
            }
            .then(longPressModifier),
    ) {
        content()

        val isDragSource = currentState.isDragging &&
            position == currentState.dataToDrop?.positionFrom
        // With a long press starting the drag anywhere on the card, the handle is redundant.
        val showDragIcon = !dragOnLongPress && (showIcon || isDragSource)

        Crossfade(
            targetState = showDragIcon,
            label = "iconCrossFade",
            animationSpec = tween(durationMillis = 200)
        ) { show ->
            if (show) {
                Icon(
                    modifier = Modifier
                        .padding(6.dp)
                        .size(20.dp)
                        .width(dragIconWidth)
                        .pointerHoverIcon(PointerIcon.Hand)
                        .onGloballyPositioned { layoutCoordinates ->
                            iconPosition = layoutCoordinates.positionInWindow()
                        }
                        .pointerInput(Unit) {
                            detectDragGestures(
                                // The offset is relative to the drag icon, not to the card.
                                onDragStart = { offset -> startDrag(iconPosition + offset) },
                                onDrag = dragBy,
                                onDragEnd = stopDrag,
                                onDragCancel = stopDrag
                            )
                        }
                        .align(Alignment.TopEnd),
                    imageVector = WrSdkIcons.dragIndicator,
                    contentDescription = "Drag icon",
                    tint = iconTintOnHover
                )
            }
        }
    }
}
