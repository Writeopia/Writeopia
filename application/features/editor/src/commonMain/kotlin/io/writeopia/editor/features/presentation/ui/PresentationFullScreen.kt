package io.writeopia.editor.features.presentation.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.writeopia.common.utils.icons.WrIcons
import io.writeopia.sdk.models.presentation.Presentation
import io.writeopia.ui.drawer.factory.DrawersFactory

/**
 * A presentation over the whole screen, for the phones and tablets, where a window of its own
 * isn't an option. A close button sits in the corner.
 */
@Composable
fun PresentationFullScreen(
    presentation: Presentation,
    isDarkTheme: Boolean,
    drawersFactory: DrawersFactory,
    onClose: () -> Unit
) {
    var index by remember(presentation.id) { mutableIntStateOf(0) }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxSize()) {
                PresentationViewer(
                    presentation = presentation,
                    index = index,
                    onIndexChange = { index = it },
                    isDarkTheme = isDarkTheme,
                    drawersFactory = drawersFactory
                )

                Icon(
                    imageVector = WrIcons.close,
                    contentDescription = "Close presentation",
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .statusBarsPadding()
                        .padding(12.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onClose)
                        .padding(8.dp)
                        .size(24.dp)
                )
            }
        }
    }
}
