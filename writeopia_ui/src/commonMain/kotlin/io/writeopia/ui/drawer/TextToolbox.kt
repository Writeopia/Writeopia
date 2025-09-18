package io.writeopia.ui.drawer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import io.writeopia.sdk.models.span.Span
import io.writeopia.ui.components.EditionScreenForText

@Composable
fun TextToolbox(
    hasSelection: Boolean,
    onSpanClick: (Span) -> Unit = {},
) {
    Popup(offset = IntOffset(0, -40)) {
        AnimatedVisibility(
            modifier = Modifier,
            visible = hasSelection,
            enter = fadeIn(animationSpec = tween(durationMillis = 150)),
            exit = fadeOut(animationSpec = tween(durationMillis = 150))
        ) {
            EditionScreenForText(
                modifier = Modifier
                    .shadow(elevation = 6.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.primary)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                iconSize = 28.dp,
                onSpanClick = onSpanClick,
            )
        }
    }
}
