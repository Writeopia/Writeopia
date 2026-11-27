package io.writeopia.ui.draganddrop.target

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import io.writeopia.sdk.model.draganddrop.DropInfo

internal val LocalDragTargetInfo = compositionLocalOf { DragTargetInfo() }

/**
 * Starts a drag of [dataToDrop] with a press and hold anywhere on [content], on every platform.
 * The floating copy keeps the size of the content and starts right over it.
 *
 * [onDragStart] and [onDragStop] tell the editor that a drag is in progress, so it can show
 * where the step would be dropped.
 */
@Composable
fun DragTarget(
    modifier: Modifier = Modifier,
    dataToDrop: DropInfo,
    onDragStart: () -> Unit = {},
    onDragStop: () -> Unit = {},
    content: @Composable () -> Unit
) {
    var currentPosition by remember { mutableStateOf(Offset.Zero) }
    var maxSize by remember { mutableStateOf(DpSize(0.dp, 0.dp)) }
    val density = LocalDensity.current
    val currentState = LocalDragTargetInfo.current
    val haptic = LocalHapticFeedback.current
    // The gesture detector outlives recompositions, so it reads the latest values.
    val currentDataToDrop by rememberUpdatedState(dataToDrop)
    val currentContent by rememberUpdatedState(content)
    val currentOnDragStart by rememberUpdatedState(onDragStart)
    val currentOnDragStop by rememberUpdatedState(onDragStop)

    Box(
        modifier = modifier
            .onGloballyPositioned { layoutCoordinates ->
                currentPosition = layoutCoordinates.positionInWindow()
                // The layout size is in pixels, it must be converted to Dp, otherwise the dragged
                // copy becomes bigger than the original in screens with density > 1.
                maxSize = with(density) { layoutCoordinates.size.toSize().toDpSize() }
            }
            .holdDragGesture { currentState.isDragging }
            .pointerInput(Unit) {
                detectDragGesturesAfterLongPress(onDragStart = { offset ->
                    currentOnDragStart()
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)

                    currentState.dataToDrop = currentDataToDrop
                    currentState.isDragging = true
                    // The offset is relative to the content.
                    currentState.dragPosition = currentPosition + offset
                    currentState.ghostPosition = currentPosition
                    currentState.draggableComposable = {
                        Box(modifier = Modifier.size(maxSize)) {
                            currentContent()
                        }
                    }
                }, onDrag = { change, dragAmount ->
                    change.consume()
                    currentState.dragOffset += dragAmount
                }, onDragEnd = {
                    currentOnDragStop()
                    currentState.isDragging = false
                    currentState.dragOffset = Offset.Zero
                    currentState.ghostPosition = null
                }, onDragCancel = {
                    currentOnDragStop()
                    currentState.isDragging = false
                    currentState.dragOffset = Offset.Zero
                    currentState.ghostPosition = null
                })
            }
    ) {
        content()
    }
}
