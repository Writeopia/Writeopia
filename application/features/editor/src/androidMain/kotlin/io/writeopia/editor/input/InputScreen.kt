package io.writeopia.editor.input

// import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import io.writeopia.common.utils.icons.WrIcons
import io.writeopia.editor.ui.MobileInputScreen
import io.writeopia.sdk.models.span.Span
import io.writeopia.ui.model.SelectionMetadata
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

@Composable
internal fun InputScreen(
    modifier: Modifier = Modifier,
    isDarkTheme: Boolean,
    metadataState: Flow<Set<SelectionMetadata>>,
    onAddSpan: (Span) -> Unit,
    onBackPress: () -> Unit = {},
    onForwardPress: () -> Unit = {},
    canUndoState: StateFlow<Boolean>,
    canRedoState: StateFlow<Boolean>,
    onDrawingClick: () -> Unit = {},
    onSummarizeClick: () -> Unit = {},
) {
    MobileInputScreen(
        modifier = modifier,
        isDarkTheme = isDarkTheme,
        metadataState = metadataState,
        onAddSpan = onAddSpan,
        onBackPress = onBackPress,
        onForwardPress = onForwardPress,
        canUndoState = canUndoState,
        canRedoState = canRedoState,
        onDrawingClick = onDrawingClick,
        platformActionsSlot = {
            val buttonShape = RoundedCornerShape(6.dp)
            val iconPadding = 4.dp
            val buttonColor = MaterialTheme.colorScheme.onPrimary

            Spacer(modifier = Modifier.width(15.dp))
            Icon(
                modifier = Modifier
                    .clip(buttonShape)
                    .clickable { onSummarizeClick() }
                    .padding(iconPadding),
                imageVector = WrIcons.ai,
                contentDescription = "Summarize",
                tint = buttonColor
            )
        }
    )
}
