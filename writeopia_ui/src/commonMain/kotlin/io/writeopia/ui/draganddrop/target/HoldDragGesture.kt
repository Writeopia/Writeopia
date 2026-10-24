package io.writeopia.ui.draganddrop.target

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange

/**
 * Claims the pointer events that don't move while [isDragging] is true.
 *
 * The drag of `detectDragGestures` lets them through unconsumed, and a parent drag detector
 * (like the selection box) picks up a gesture from the first event nobody consumed. Holding a
 * card still over a folder used to turn the drag into a selection; this keeps the gesture with
 * the card.
 */
internal fun Modifier.holdDragGesture(isDragging: () -> Boolean): Modifier =
    pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent()
                if (!isDragging()) continue

                event.changes.forEach { change ->
                    val isStill = change.pressed &&
                        change.previousPressed &&
                        change.positionChange() == Offset.Zero

                    if (isStill) change.consume()
                }
            }
        }
    }
