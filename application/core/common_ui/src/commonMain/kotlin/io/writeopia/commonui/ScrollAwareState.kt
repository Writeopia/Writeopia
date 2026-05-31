package io.writeopia.commonui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Stable
class ScrollAwareState {
    var isVisible by mutableStateOf(true)
        private set

    private var scrollDelta = 0f
    private val scrollThreshold = 50f

    val nestedScrollConnection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            val delta = available.y
            scrollDelta += delta

            if (scrollDelta > scrollThreshold) {
                // Scrolling up - show bars
                isVisible = true
                scrollDelta = 0f
            } else if (scrollDelta < -scrollThreshold) {
                // Scrolling down - hide bars
                isVisible = false
                scrollDelta = 0f
            }

            return Offset.Zero
        }
    }

    fun show() {
        isVisible = true
    }

    fun hide() {
        isVisible = false
    }
}

@Composable
fun rememberScrollAwareState(): ScrollAwareState {
    return remember { ScrollAwareState() }
}

@Composable
fun animateBarOffset(
    isVisible: Boolean,
    barHeight: Dp,
    animationDuration: Int = 300
): Dp {
    val offset by animateDpAsState(
        targetValue = if (isVisible) 0.dp else barHeight + 40.dp,
        animationSpec = tween(durationMillis = animationDuration),
        label = "barOffset"
    )
    return offset
}
