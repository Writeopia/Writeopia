package io.writeopia.commonui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Follows the scroll of the screens of the phones: [isVisible] tells whether the bottom bar is
 * shown, and [topBarFade] how far the top bar has faded away (see [ScrollAwareTopBar]).
 */
@Stable
class ScrollAwareState(private val fadeDistancePx: Float = 1f) {
    var isVisible by mutableStateOf(true)
        private set

    /**
     * 0 when the top bar is opaque, 1 when it's gone. It follows the distance the content has
     * scrolled, a pixel at a time, so the bar fades with the finger instead of jumping. It never
     * exceeds the distance scrolled from the top over [fadeDistancePx], so it's 0 at the top.
     */
    var topBarFade by mutableFloatStateOf(0f)
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

        // Only what the content really scrolled counts: dragging a list that can't move (too
        // short, or at its end) leaves the top bar as it is.
        override fun onPostScroll(
            consumed: Offset,
            available: Offset,
            source: NestedScrollSource
        ): Offset {
            if (consumed.y != 0f) {
                topBarFade = (topBarFade - consumed.y / fadeDistancePx).coerceIn(0f, 1f)
            }

            return Offset.Zero
        }
    }

    /** Shows the bars again, as a screen that was just opened starts at its top. */
    fun show() {
        isVisible = true
        topBarFade = 0f
        scrollDelta = 0f
    }

    fun hide() {
        isVisible = false
    }
}

/** The top bar is gone after the content scrolls this far. */
private val TOP_BAR_FADE_DISTANCE = 96.dp

@Composable
fun rememberScrollAwareState(): ScrollAwareState {
    val fadeDistancePx = with(LocalDensity.current) { TOP_BAR_FADE_DISTANCE.toPx() }
    return remember(fadeDistancePx) { ScrollAwareState(fadeDistancePx) }
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
