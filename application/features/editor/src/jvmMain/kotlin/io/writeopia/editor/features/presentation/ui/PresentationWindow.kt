package io.writeopia.editor.features.presentation.ui

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import io.writeopia.common.utils.configuration.LocalPlatform
import io.writeopia.common.utils.configuration.PlatformType
import io.writeopia.sdk.models.presentation.Presentation
import io.writeopia.theme.WriteopiaTheme
import io.writeopia.ui.drawer.factory.DefaultDrawersDesktop

/**
 * A presentation in a window of its own, like on the Mac. The arrow keys and the space bar move
 * between the slides.
 */
@Composable
fun PresentationWindow(
    presentation: Presentation,
    isDarkTheme: Boolean,
    onClose: () -> Unit
) {
    var index by remember(presentation.id) { mutableIntStateOf(0) }
    val lastIndex = (presentation.slides.size - 1).coerceAtLeast(0)

    Window(
        onCloseRequest = onClose,
        title = presentation.title.ifBlank { "Presentation" },
        state = rememberWindowState(width = 1100.dp, height = 700.dp, position = WindowPosition(Alignment.Center)),
        onKeyEvent = { event ->
            if (event.type != KeyEventType.KeyDown) return@Window false
            when (event.key) {
                Key.DirectionLeft -> {
                    if (index > 0) index -= 1
                    true
                }
                Key.DirectionRight, Key.Spacebar -> {
                    if (index < lastIndex) index += 1
                    true
                }
                else -> false
            }
        }
    ) {
        WriteopiaTheme(darkTheme = isDarkTheme) {
            CompositionLocalProvider(LocalPlatform provides PlatformType.DESKTOP) {
                Surface {
                    PresentationViewer(
                        presentation = presentation,
                        index = index,
                        onIndexChange = { index = it.coerceIn(0, lastIndex) },
                        isDarkTheme = isDarkTheme,
                        drawersFactory = DefaultDrawersDesktop
                    )
                }
            }
        }
    }
}
