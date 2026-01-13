package io.writeopia.editor.ui

// import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.writeopia.common.utils.colors.highlightColors
import io.writeopia.common.utils.icons.WrIcons
import io.writeopia.sdk.models.span.Span
import io.writeopia.theme.WriteopiaTheme
import io.writeopia.ui.model.SelectionMetadata
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

@Composable
internal fun MobileInputScreen(
    modifier: Modifier = Modifier,
    isDarkTheme: Boolean,
    metadataState: Flow<Set<SelectionMetadata>>,
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

    var showHighlightColors by remember {
        mutableStateOf(false)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primary),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(50.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                val clipShape = MaterialTheme.shapes.medium
                val iconPadding = 4.dp

                val metadata by metadataState.collectAsState(emptySet())
                val buttonShape = RoundedCornerShape(6.dp)

                Row(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val boldBgColor = if (metadata.contains(SelectionMetadata.BOLD)) {
                        WriteopiaTheme.colorScheme.optionsSelector
                    } else {
                        Color.Unspecified
                    }

                    Icon(
                        modifier = Modifier
                            .clip(buttonShape)
                            .background(color = boldBgColor, shape = buttonShape)
                            .border(width = 1.dp, boldBgColor, shape = buttonShape)
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

                    val italicBgColor = if (metadata.contains(SelectionMetadata.ITALIC)) {
                        WriteopiaTheme.colorScheme.optionsSelector
                    } else {
                        Color.Unspecified
                    }

                    Icon(
                        modifier = Modifier
                            .clip(buttonShape)
                            .background(color = italicBgColor, shape = buttonShape)
                            .border(width = 1.dp, italicBgColor, shape = buttonShape)
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

                    val underlineBgColor = if (metadata.contains(SelectionMetadata.UNDERLINE)) {
                        WriteopiaTheme.colorScheme.optionsSelector
                    } else {
                        Color.Unspecified
                    }

                    Icon(
                        modifier = Modifier
                            .clip(buttonShape)
                            .background(color = underlineBgColor, shape = buttonShape)
                            .border(width = 1.dp, underlineBgColor, shape = buttonShape)
                            .clickable {
                                onAddSpan(Span.UNDERLINE)
                            }
                            .padding(iconPadding),
                        imageVector = WrIcons.underline,
                        contentDescription = "Underline",
//                    stringResource(R.string.undo),
                        tint = buttonColor
                    )

                    val highlightColor = if (showHighlightColors) {
                        WriteopiaTheme.colorScheme.optionsSelector
                    } else {
                        Color.Unspecified
                    }

                    Spacer(modifier = Modifier.width(15.dp))

                    Icon(
                        modifier = Modifier
                            .clip(buttonShape)
                            .background(color = highlightColor, shape = buttonShape)
                            .border(width = 1.dp, highlightColor, shape = buttonShape)
                            .clickable {
                                showHighlightColors = !showHighlightColors
                            }
                            .padding(iconPadding),
                        imageVector = WrIcons.highlight,
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

        AnimatedVisibility(showHighlightColors) {
            HighlightText(isDarkTheme, spanClick = onAddSpan)
        }
    }
}

@Composable
private fun HighlightText(isDarkTheme: Boolean, spanClick: (Span) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().height(50.dp).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        val colors = highlightColors(isDarkTheme)

        colors.forEach { (span, color) ->
            Box(
                modifier = Modifier.weight(1F)
                    .size(32.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .clickable { spanClick(span) },
            ) {
                Box(
                    modifier = Modifier.size(16.dp)
                        .align(Alignment.Center)
                        .background(color, CircleShape)
                        .clip(CircleShape)
                )
            }
        }
    }
}

