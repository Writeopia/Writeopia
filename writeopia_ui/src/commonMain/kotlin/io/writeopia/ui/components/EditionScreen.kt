package io.writeopia.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.outlined.FormatBold
import androidx.compose.material.icons.outlined.FormatItalic
import androidx.compose.material.icons.outlined.FormatUnderlined
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import io.writeopia.sdk.models.span.Span
import io.writeopia.sdk.models.story.Tag
import io.writeopia.ui.icons.WrSdkIcons
import io.writeopia.ui.model.SelectionMetadata
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.jetbrains.compose.ui.tooling.preview.Preview

// Here
// This screen could live in a module for extra Composables. In the future there will be more
// buttons here
@Composable
fun EditionScreen(
    modifier: Modifier = Modifier,
    iconSize: Dp = 36.dp,
    highlightButtonColor: Color = Color.Unspecified,
    metadataState: Flow<Set<SelectionMetadata>>,
    onSpanClick: (Span) -> Unit = {},
    checkboxClick: () -> Unit = {},
    listItemClick: () -> Unit = {},
    onHighlight: () -> Unit = {},
    onDelete: () -> Unit = {},
    onCopy: () -> Unit = {},
    onCut: () -> Unit = {},
    onAddPage: () -> Unit = {},
    onClose: () -> Unit = {},
    titleClick: (Tag) -> Unit = {}
) {
    val iconPadding = PaddingValues(vertical = 4.dp)
    val clipShape = MaterialTheme.shapes.medium
    val spaceWidth = 8.dp

    var showFontOptions by remember {
        mutableStateOf(false)
    }

    Column(
        modifier = modifier
            .padding(4.dp)
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primary),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            val tint = MaterialTheme.colorScheme.onPrimary

            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()).weight(1F),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    modifier = Modifier
                        .clip(clipShape)
                        .clickable {
                            onSpanClick(Span.BOLD)
                        }
                        .size(iconSize)
                        .padding(iconPadding),
                    imageVector = Icons.Outlined.FormatBold,
                    contentDescription = "BOLD",
//            contentDescription = stringResource(R.string.delete),
                    tint = tint
                )

                Icon(
                    modifier = Modifier
                        .clip(clipShape)
                        .clickable {
                            onSpanClick(Span.ITALIC)
                        }
                        .size(iconSize)
                        .padding(iconPadding),
                    imageVector = Icons.Outlined.FormatItalic,
                    contentDescription = "ITALIC",
//            contentDescription = stringResource(R.string.delete),
                    tint = tint
                )

                Spacer(modifier = Modifier.width(spaceWidth))

                Icon(
                    modifier = Modifier
                        .clip(clipShape)
                        .clickable {
                            onSpanClick(Span.UNDERLINE)
                        }
                        .size(iconSize)
                        .padding(iconPadding),
                    imageVector = Icons.Outlined.FormatUnderlined,
                    contentDescription = "UNDERLINE",
//            contentDescription = stringResource(R.string.delete),
                    tint = tint
                )

                Spacer(modifier = Modifier.width(spaceWidth))

                Icon(
                    modifier = Modifier
                        .clip(clipShape)
                        .clickable(onClick = checkboxClick)
                        .size(iconSize)
                        .padding(iconPadding),
                    imageVector = WrSdkIcons.checkbox,
                    contentDescription = "Checkbox",
//            contentDescription = stringResource(R.string.delete),
                    tint = tint
                )

                Spacer(modifier = Modifier.width(spaceWidth))

                Icon(
                    modifier = Modifier
                        .clip(clipShape)
                        .clickable(onClick = listItemClick)
                        .size(iconSize)
                        .padding(iconPadding),
                    imageVector = WrSdkIcons.list,
                    contentDescription = "List item",
//            contentDescription = stringResource(R.string.delete),
                    tint = tint
                )

                Spacer(modifier = Modifier.width(spaceWidth))

                Icon(
                    modifier = Modifier
                        .clip(clipShape)
                        .clickable(onClick = onHighlight)
                        .size(iconSize)
                        .padding(iconPadding),
                    imageVector = WrSdkIcons.highlight,
                    contentDescription = "List item",
//            contentDescription = stringResource(R.string.delete),
                    tint = tint
                )

                Spacer(modifier = Modifier.width(spaceWidth))

                val highlightColor = if (showFontOptions) {
                    highlightButtonColor
                } else {
                    Color.Unspecified
                }

                Icon(
                    modifier = Modifier
                        .clip(clipShape)
                        .background(color = highlightColor, shape = clipShape)
                        .border(1.dp, color = highlightColor, shape = clipShape)
                        .clickable {
                            showFontOptions = !showFontOptions
                        }
                        .size(iconSize)
                        .padding(iconPadding),
                    imageVector = WrSdkIcons.titleChange,
                    contentDescription = "Font Options",
                    tint = tint
                )

                Spacer(modifier = Modifier.width(spaceWidth))

                Icon(
                    modifier = Modifier
                        .clip(clipShape)
                        .clickable(onClick = onCopy)
                        .size(iconSize)
                        .padding(iconPadding),
                    imageVector = WrSdkIcons.copy,
                    contentDescription = "Copy",
                    tint = tint
                )

                Spacer(modifier = Modifier.width(spaceWidth))

                Icon(
                    modifier = Modifier
                        .clip(clipShape)
                        .clickable(onClick = onCut)
                        .size(iconSize)
                        .padding(iconPadding),
                    imageVector = Icons.Default.ContentCut,
                    contentDescription = "Cut",
                    tint = tint
                )

                Spacer(modifier = Modifier.width(spaceWidth))

                Icon(
                    modifier = Modifier
                        .clip(clipShape)
                        .clickable(onClick = onDelete)
                        .size(iconSize)
                        .padding(iconPadding),
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = "Delete",
//            contentDescription = stringResource(R.string.delete),
                    tint = tint
                )

                Spacer(modifier = Modifier.width(spaceWidth))

                Icon(
                    modifier = Modifier
                        .clip(clipShape)
                        .clickable(onClick = onAddPage)
                        .size(iconSize)
                        .padding(iconPadding),
                    imageVector = WrSdkIcons.linkPage,
                    contentDescription = "Link to page",
                    tint = tint
                )
            }

            Icon(
                modifier = Modifier
                    .clip(clipShape)
                    .clickable(onClick = onClose)
                    .size(iconSize)
                    .padding(iconPadding),
                imageVector = WrSdkIcons.close,
                contentDescription = "Close",
//            contentDescription = stringResource(R.string.delete),
                tint = tint
            )
        }

        AnimatedVisibility(showFontOptions) {
            HeaderOptions(metadataState, highlightButtonColor, titleClick)
        }
    }
}

@Composable
private fun HeaderOptions(
    metadataState: Flow<Set<SelectionMetadata>>,
    highlightButtonColor: Color,
    titleClick: (Tag) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(50.dp).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        TitleChanges(
            metadataState = metadataState,
            highlightButtonColor = highlightButtonColor,
            titleClick = titleClick
        )
    }
}

@Composable
private fun TitleChanges(
    metadataState: Flow<Set<SelectionMetadata>>,
    modifier: Modifier = Modifier,
    highlightButtonColor: Color,
    titleClick: (Tag) -> Unit
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val textAlign = TextAlign.Center
        val textStyle = MaterialTheme.typography.bodyMedium
        val fontWeight = FontWeight.Bold
        val padding = PaddingValues(vertical = 10.dp)

        val metadata by metadataState.collectAsState(emptySet())
        val hasTitle = metadata.contains(SelectionMetadata.TITLE)
        val shape = MaterialTheme.shapes.medium

        val selectColor = { shouldHighlight: Boolean ->
            if (shouldHighlight) highlightButtonColor else Color.Unspecified
        }

        Text(
            modifier = Modifier.weight(1F)
                .clip(shape)
                .clickable {
                    titleClick(Tag.H1)
                }
                .background(selectColor(hasTitle), shape)
                .padding(padding),
            text = "Title",
            style = textStyle,
            fontWeight = fontWeight,
            textAlign = textAlign
        )

        val hasSubTitle = metadata.contains(SelectionMetadata.SUBTITLE)

        Text(
            modifier = Modifier.weight(1F)
                .clip(shape)
                .clickable {
                    titleClick(Tag.H2)
                }
                .background(selectColor(hasSubTitle), shape)
                .padding(padding),
            text = "SubTitle",
            style = textStyle,
            fontWeight = fontWeight,
            textAlign = textAlign
        )

        val hasHeader = metadata.contains(SelectionMetadata.HEADING)

        Text(
            modifier = Modifier.weight(1F)
                .clip(shape)
                .clickable {
                    titleClick(Tag.H3)
                }
                .background(selectColor(hasHeader), shape)
                .padding(padding),
            text = "Header",
            style = textStyle,
            fontWeight = fontWeight,
            textAlign = textAlign
        )
    }
}

@Composable
fun EditionScreenForText(
    modifier: Modifier = Modifier,
    iconSize: Dp = 36.dp,
    onSpanClick: (Span) -> Unit = {},
    onLinkConfirm: (String) -> Unit = {},
) {
    val iconPadding = PaddingValues(vertical = 4.dp)
    val clipShape = MaterialTheme.shapes.medium
    val spaceWidth = 8.dp

    var showLinkScreen by remember { mutableStateOf(false) }

    Crossfade(showLinkScreen) { showLink ->
        if (showLink) {
            AddLinkScreen(
                modifier = Modifier,
                cancel = { showLinkScreen = false },
                onLinkConfirm = onLinkConfirm
            )
        } else {
            Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
                val tint = MaterialTheme.colorScheme.onPrimary

                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        modifier = Modifier
                            .clip(clipShape)
                            .clickable {
                                onSpanClick(Span.BOLD)
                            }
                            .size(iconSize)
                            .padding(iconPadding),
                        imageVector = Icons.Outlined.FormatBold,
                        contentDescription = "BOLD",
//            contentDescription = stringResource(R.string.delete),
                        tint = tint,
                    )

                    Icon(
                        modifier = Modifier
                            .clip(clipShape)
                            .clickable {
                                onSpanClick(Span.ITALIC)
                            }
                            .size(iconSize)
                            .padding(iconPadding),
                        imageVector = Icons.Outlined.FormatItalic,
                        contentDescription = "ITALIC",
//            contentDescription = stringResource(R.string.delete),
                        tint = tint
                    )

                    Spacer(modifier = Modifier.width(spaceWidth))

                    Icon(
                        modifier = Modifier
                            .clip(clipShape)
                            .clickable {
                                onSpanClick(Span.UNDERLINE)
                            }
                            .size(iconSize)
                            .padding(iconPadding),
                        imageVector = Icons.Outlined.FormatUnderlined,
                        contentDescription = "UNDERLINE",
//            contentDescription = stringResource(R.string.delete),
                        tint = tint
                    )

                    Spacer(modifier = Modifier.width(spaceWidth))

                    Icon(
                        modifier = Modifier
                            .clip(clipShape)
                            .clickable {
                                showLinkScreen = true
                            }
                            .size(iconSize)
                            .padding(PaddingValues(vertical = 5.dp)),
                        imageVector = WrSdkIcons.linkPage,
                        contentDescription = "Link to page",
                        tint = tint
                    )

//            Spacer(modifier = Modifier.width(spaceWidth))
//
//            Icon(
//                modifier = Modifier
//                    .clip(clipShape)
//                    .clickable(onClick = onCopy)
//                    .size(iconSize)
//                    .padding(iconPadding),
//                imageVector = WrSdkIcons.copy,
//                contentDescription = "Copy",
//                tint = tint
//            )
//
//            Spacer(modifier = Modifier.width(spaceWidth))
//
//            Icon(
//                modifier = Modifier
//                    .clip(clipShape)
//                    .clickable(onClick = onCut)
//                    .size(iconSize)
//                    .padding(iconPadding),
//                imageVector = Icons.Default.ContentCut,
//                contentDescription = "Cut",
//                tint = tint
//            )

//            Spacer(modifier = Modifier.width(spaceWidth))
//
//            Icon(
//                modifier = Modifier
//                    .clip(clipShape)
//                    .clickable(onClick = onAddPage)
//                    .size(iconSize)
//                    .padding(iconPadding),
//                imageVector = WrSdkIcons.linkPage,
//                contentDescription = "Link to page",
//                tint = tint
//            )
                }
            }
        }
    }
}

@Composable
private fun AddLinkScreen(
    modifier: Modifier = Modifier,
    cancel: () -> Unit,
    onLinkConfirm: (String) -> Unit = {},
) {
    var linkText by remember { mutableStateOf("") }

    Popup(properties = PopupProperties(focusable = true), onDismissRequest = cancel) {
        Card(modifier = modifier.shadow(elevation = 6.dp, shape = CardDefaults.shape)) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicTextField(
                    modifier = Modifier.width(300.dp),
                    value = linkText,
                    onValueChange = { linkText = it },
                    maxLines = 1,
                    singleLine = true,
                    textStyle = MaterialTheme.typography
                        .bodyMedium
                        .copy(color = MaterialTheme.colorScheme.onBackground),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.onBackground)
                )

                Spacer(modifier = Modifier.width(8.dp))

                Icon(
                    imageVector = WrSdkIcons.check,
                    contentDescription = "Confirm",
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.medium)
                        .clickable {
                            onLinkConfirm(linkText)
                        }
                        .size(28.dp)
                        .padding(4.dp),
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
        }
    }
}

@Preview
@Composable
fun EditionScreenPreview() {
    EditionScreen(metadataState = flow { })
}
