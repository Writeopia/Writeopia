package io.writeopia.ui.draganddrop.target

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import com.composables.icons.lucide.Lucide
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.GripVertical
import io.writeopia.sdk.model.draganddrop.DropInfo

@OptIn(ExperimentalComposeUiApi::class)
@Composable
actual fun DragRowTarget(
    modifier: Modifier,
    dataToDrop: DropInfo,
    onClick: () -> Unit,
    showIcon: Boolean,
    position: Int,
    emptySpaceClick: () -> Unit,
    dragIconWidth: Dp,
    iconTintOnHover: Color,
    iconTint: Color,
    onDragStart: () -> Unit,
    onDragStop: () -> Unit,
    isHoldDraggable: Boolean,
    content: @Composable RowScope.() -> Unit
) {
    var currentPosition by remember { mutableStateOf(Offset.Zero) }
    var maxSize by remember { mutableStateOf(DpSize(0.dp, 0.dp)) }
    val currentState = LocalDragTargetInfo.current
    val haptic = LocalHapticFeedback.current

    Row(
        modifier = modifier
            .onGloballyPositioned { layoutCoordinates ->
                // Todo: Offset.Zero Is wrong!
                currentPosition = layoutCoordinates.localToWindow(Offset.Zero)
                maxSize = DpSize(layoutCoordinates.size.width.dp, layoutCoordinates.size.height.dp)
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val showDragIcon = showIcon ||
            currentState.isDragging && position == currentState.dataToDrop?.positionFrom

        var active by remember { mutableStateOf(false) }
        val tintColor by derivedStateOf {
            if (active) iconTintOnHover else iconTint
        }

        Crossfade(
            targetState = showDragIcon,
            label = "iconCrossFade",
            animationSpec = tween(durationMillis = 200)
        ) { show ->
            if (show) {
                Icon(
                    modifier = Modifier
                        .width(dragIconWidth)
                        .pointerHoverIcon(PointerIcon.Hand)
                        .onPointerEvent(PointerEventType.Enter) { active = true }
                        .onPointerEvent(PointerEventType.Exit) { active = false }
                        .clickable(onClick = onClick)
                        .pointerInput(Unit) {
                            detectDragGestures(onDragStart = { offset ->
                                onDragStart()
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)

                                currentState.dataToDrop = dataToDrop
                                currentState.isDragging = true
                                currentState.dragPosition = currentPosition + offset
                                currentState.draggableComposable = {
                                    Row(
                                        modifier = Modifier.size(maxSize)
                                            .clip(MaterialTheme.shapes.medium)
                                            .background(
                                                MaterialTheme.colorScheme.surfaceVariant
                                            ).alpha(0.6F)
                                    ) {
                                        content()
                                    }
                                }
                            }, onDrag = { change, dragAmount ->
                                change.consume()
                                currentState.dragOffset += Offset(dragAmount.x, dragAmount.y)
                            }, onDragEnd = {
                                onDragStop()
                                currentState.isDragging = false
                                currentState.dragOffset = Offset.Zero
                            }, onDragCancel = {
                                onDragStop()
                                currentState.dragOffset = Offset.Zero
                                currentState.isDragging = false
                            })
                        },
                    imageVector = Lucide.GripVertical,
                    contentDescription = "Drag icon",
                    tint = tintColor
                )
            } else {
                Spacer(
                    modifier = Modifier.width(dragIconWidth).clickable(onClick = emptySpaceClick)
                )
            }
        }

        if (currentState.isDragging && position == currentState.dataToDrop?.positionFrom) {
            Row(modifier = Modifier.alpha(0.7F), verticalAlignment = Alignment.CenterVertically) {
                content()
            }
        } else {
            content()
        }
    }
}
