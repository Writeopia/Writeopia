package io.writeopia.ui.components.multiselection

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

val LocalDragSelectionInfo = compositionLocalOf { DragSelectionInfo() }

@Composable
fun DragSelectionBox(modifier: Modifier = Modifier, context: @Composable BoxScope.() -> Unit) {
    var initialPosition by remember { mutableStateOf(Offset.Zero) }
    var finalPosition by remember { mutableStateOf(Offset.Zero) }
    var state by remember { mutableStateOf(DragSelectionInfo(isDragging = false)) }
    val density = LocalDensity.current

    CompositionLocalProvider(LocalDragSelectionInfo provides state) {
        Box(
            modifier = modifier.pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        initialPosition = offset
                        state = DragSelectionInfo(
                            isDragging = true,
                            selectionBox = SelectionBox(x = offset.x, y = offset.y)
                        )
                    },
                    onDrag = { pointerChange, dragAmount ->
                        finalPosition = pointerChange.position
                    },
                    onDragEnd = {
                        state = state.copy(isDragging = false)
                        finalPosition = Offset.Zero
                    },
                    onDragCancel = {
                        state = state.copy(isDragging = false)
                        finalPosition = Offset.Zero
                    },
                )
            }
        ) {
            context()

            if (state.isDragging) {
                val shape = MaterialTheme.shapes.medium

                val (x, width) = if (initialPosition.x < finalPosition.x) {
                    initialPosition.x to finalPosition.x - initialPosition.x
                } else {
                    finalPosition.x to initialPosition.x - finalPosition.x
                }

                val (y, height) = if (initialPosition.y < finalPosition.y) {
                    initialPosition.y to finalPosition.y - initialPosition.y
                } else {
                    finalPosition.y to initialPosition.y - finalPosition.y
                }

                state = state.copy(
                    selectionBox = SelectionBox(x, y, width, height)
                )

                Box(
                    modifier = Modifier.offset(
                        x = density.run { x.toDp() },
                        y = density.run { y.toDp() }
                    )
                        .size(
                            width = density.run { width.toDp() },
                            height = density.run { height.toDp() }
                        )
                        .border(width = 1.dp, color = dragBoxColor, shape = shape)
                        .background(
                            color = dragBoxColor.copy(alpha = 0.2F),
                            shape = shape
                        )
                )
            }
        }
    }
}

private val dragBoxColor = Color(0xFF64B5F6)
