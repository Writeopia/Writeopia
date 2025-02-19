package io.writeopia.editor.input

// import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.writeopia.common.utils.icons.WrIcons
import io.writeopia.sdk.models.span.Span
import kotlinx.coroutines.flow.StateFlow

@Composable
internal fun InputScreen(
    modifier: Modifier = Modifier,
    onAddSpan: (Span) -> Unit,
    onBackPress: () -> Unit = {},
    onForwardPress: () -> Unit = {},
    canUndoState: StateFlow<Boolean>,
    canRedoState: StateFlow<Boolean>,
) {
    val canUndo by canUndoState.collectAsState()
    val canRedo by canRedoState.collectAsState()

    val buttonColor = MaterialTheme.colorScheme.onPrimary
    val disabledColor = Color.LightGray

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp)
            .background(MaterialTheme.colorScheme.primary),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            val clipShape = MaterialTheme.shapes.medium
            val iconPadding = 4.dp

            Row(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    modifier = Modifier
                        .clip(clipShape)
                        .clickable {
                            onAddSpan(Span.BOLD)
                        }
                        .padding(iconPadding),
                    imageVector = WrIcons.bold,
                    contentDescription = "Bold",
//                    stringResource(R.string.undo),
                    tint = buttonColor
                )

                Spacer(modifier = Modifier.width(15.dp))

                Icon(
                    modifier = Modifier
                        .clip(clipShape)
                        .clickable {
                            onAddSpan(Span.ITALIC)
                        }
                        .padding(iconPadding),
                    imageVector = WrIcons.italic,
                    contentDescription = "Italic",
//                    stringResource(R.string.undo),
                    tint = buttonColor
                )

                Spacer(modifier = Modifier.width(15.dp))

                Icon(
                    modifier = Modifier
                        .clip(clipShape)
                        .clickable {
                            onAddSpan(Span.UNDERLINE)
                        }
                        .padding(iconPadding),
                    imageVector = WrIcons.underline,
                    contentDescription = "Underline",
//                    stringResource(R.string.undo),
                    tint = buttonColor
                )

                Spacer(modifier = Modifier.weight(1F))

                Icon(
                    modifier = Modifier
                        .clip(clipShape)
                        .clickable {
                            if (canUndo) {
                                onBackPress()
                            }
                        }
                        .padding(iconPadding),
                    imageVector = WrIcons.undo,
                    contentDescription = "",
//                    stringResource(R.string.undo),
                    tint = if (canUndo) buttonColor else disabledColor
                )

                Spacer(modifier = Modifier.width(10.dp))

                Icon(
                    modifier = Modifier
                        .clip(clipShape)
                        .clickable {
                            if (canRedo) {
                                onForwardPress()
                            }
                        }
                        .padding(iconPadding),
                    imageVector = WrIcons.redo,
                    contentDescription = "",
//                    stringResource(R.string.redo),
                    tint = if (canRedo) buttonColor else disabledColor
                )
            }
        }
    }
}

// @Preview
// @Composable
// private fun InputComposablePreview() {
//    InputScreen(
//        canUndoState = MutableStateFlow(true),
//        canRedoState = MutableStateFlow(true)
//    )
// }
