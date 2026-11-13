package io.writeopia.commonui

import androidx.compose.foundation.clickable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed

/**
 * Calls [onReached] when the element is clicked [count] times in a row, then starts counting
 * again. Meant for hidden features (easter eggs), so there is no ripple.
 */
fun Modifier.repeatedClick(count: Int = 10, onReached: () -> Unit): Modifier = composed {
    var clicks by remember { mutableStateOf(0) }

    clickable(
        interactionSource = null,
        indication = null,
    ) {
        clicks += 1

        if (clicks >= count) {
            clicks = 0
            onReached()
        }
    }
}
