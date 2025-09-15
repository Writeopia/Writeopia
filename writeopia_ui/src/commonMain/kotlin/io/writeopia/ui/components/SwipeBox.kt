package io.writeopia.ui.components

import androidx.compose.animation.animateColor
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.absoluteValue
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Composable to create a swipe effect. The more far away for the original position the user is, the
 * harder it will be to keep moving, which mimics an elastic effect. The recomended usage of this
 * compose is to create a swipe to edit functionality.
 *
 * @param modifier [Modifier]
 * @param isOnEditState Boolean
 * @param defaultColor [Color] The color of the container in the idle state
 * @param activeColor [Color] The color of the container in the active state
 * @param cornersShape [Shape] The shape of the container
 * @param swipeListener Listener of the swipe action
 * @param content The inner Composable
 */
@Composable
fun SwipeBox(
    modifier: Modifier = Modifier,
    isOnEditState: Boolean,
    paddingValues: PaddingValues = PaddingValues(0.dp),
    defaultColor: Color = MaterialTheme.colorScheme.background,
    activeColor: Color,
    activeBorderColor: Color = MaterialTheme.colorScheme.primary,
    borderWidth: Dp = 1.dp,
    cornersShape: Shape = MaterialTheme.shapes.medium,
    swipeListener: (Boolean) -> Unit,
    content: @Composable BoxScope.() -> Unit
) {
    val haptic = LocalHapticFeedback.current

    var isOnEditMode by remember { mutableStateOf(isOnEditState) }
    var swipeOffset by remember { mutableFloatStateOf(0F) }
    var dragging by remember { mutableStateOf(false) }

    val transition = updateTransition(
        targetState = isOnEditMode,
        label = "editionMultipleAnimation",
    )

    val colorAnimated by transition.animateColor(label = "colorAnimation") { isEdit ->
        if (isEdit) activeColor else defaultColor
    }

    val colorBorderAnimated by transition.animateColor(label = "colorAnimation") { isEdit ->
        if (isEdit) activeBorderColor else defaultColor
    }

    val animatedOffset by animateIntOffsetAsState(
        targetValue = IntOffset(swipeOffset.roundToInt(), 0),
        animationSpec =
            spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                visibilityThreshold = IntOffset(1, 1)
            ),
        label = "offsetAnimation",
        finishedListener = {
            swipeListener(isOnEditMode)
        }
    )

    Box(
        modifier = modifier
            .offset {
                if (dragging) {
                    IntOffset(swipeOffset.roundToInt(), 0)
                } else {
                    animatedOffset
                }
            }
            .background(
                colorAnimated,
                shape = cornersShape
            )
            .border(
                width = borderWidth,
                color = colorBorderAnimated,
                shape = cornersShape
            )
            .padding(paddingValues)
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { _ -> dragging = true },
                    onHorizontalDrag = { _, dragAmount ->
                        val maxDistance = 80
                        val correction = (maxDistance - swipeOffset.absoluteValue) / maxDistance

                        swipeOffset += dragAmount * correction.pow(3)
                    },
                    onDragCancel = {
                        swipeOffset = 0F
                        dragging = false
                    },
                    onDragEnd = {
                        dragging = false

                        if (swipeOffset.absoluteValue > 40) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            isOnEditMode = !isOnEditMode
                        }

                        swipeOffset = 0F
                    }
                )
            }
    ) {
        content()
    }
}
