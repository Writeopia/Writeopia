package io.writeopia.commonui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The scroll state of the phone screens whose top bar fades away with the scroll, like the
 * YouTube app. Provided by the portrait layout of the phones only: without it the top bars slide
 * away as a whole, the way the other layouts always did.
 */
val LocalScrollAwareState: ProvidableCompositionLocal<ScrollAwareState?> =
    staticCompositionLocalOf { null }

/** Whether the top bars of this screen fade with the scroll and the content scrolls under them. */
@Composable
fun isTopBarOverContent(): Boolean = LocalScrollAwareState.current != null

/**
 * The top bar of a screen, put in the `topBar` slot of its scaffold.
 *
 * With a [LocalScrollAwareState] the bar keeps its size, so the content below never jumps, and it
 * turns transparent as the content scrolls down and back as it scrolls up, while the content
 * scrolls under it, edge to edge. Once it's gone it no longer takes the touches, so the content
 * under it can be tapped. The content must then start below the bar with a padding inside its
 * scroll (a `contentPadding`), not outside, or there would be nothing under the bar.
 *
 * Without it the bar slides away when [visible] is false.
 */
@Composable
fun ScrollAwareTopBar(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val scrollState = LocalScrollAwareState.current

    if (scrollState == null) {
        AnimatedVisibility(
            visible = visible,
            modifier = modifier,
            enter = slideInVertically { -it },
            exit = slideOutVertically { -it }
        ) {
            content()
        }
        return
    }

    val isGone by remember(scrollState) {
        derivedStateOf { scrollState.topBarFade >= GONE_FADE }
    }
    var barHeight by remember { mutableIntStateOf(0) }

    if (isGone && barHeight > 0) {
        // Same height, nothing drawn and nothing to touch.
        Spacer(modifier = modifier.height(with(LocalDensity.current) { barHeight.toDp() }))
    } else {
        Box(
            modifier = modifier
                .onSizeChanged { size -> barHeight = size.height }
                // Read while drawing: the fade changes every frame of a scroll without recomposing.
                .graphicsLayer { alpha = 1f - scrollState.topBarFade }
        ) {
            content()
        }
    }
}

private const val GONE_FADE = 0.98f

/**
 * Splits the padding of a scaffold for a screen whose content may scroll under its top bar and
 * under the system navigation bar, edge to edge: [outer] goes around the content, [contentTop] at
 * the start of what scrolls and [contentBottom] at its end. When the bar doesn't fade
 * ([isTopBarOverContent] false) everything stays outside, as before.
 */
data class TopBarPadding(val outer: PaddingValues, val contentTop: Dp, val contentBottom: Dp)

@Composable
fun PaddingValues.splitForTopBar(overContent: Boolean = isTopBarOverContent()): TopBarPadding {
    if (!overContent) return TopBarPadding(this, 0.dp, 0.dp)

    val layoutDirection = LocalLayoutDirection.current

    return TopBarPadding(
        outer = PaddingValues(
            start = calculateStartPadding(layoutDirection),
            end = calculateEndPadding(layoutDirection),
        ),
        contentTop = calculateTopPadding(),
        contentBottom = calculateBottomPadding()
    )
}
