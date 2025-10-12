package io.writeopia.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import io.writeopia.sdk.models.span.Span
import io.writeopia.ui.icons.WrSdkIcons
import org.jetbrains.compose.ui.tooling.preview.Preview

// Here
// This screen could live in a module for extra Composables. In the future there will be more
// buttons here
@Composable
fun EditionScreen(
    modifier: Modifier = Modifier,
    iconSize: Dp = 36.dp,
    onSpanClick: (Span) -> Unit = {},
    checkboxClick: () -> Unit = {},
    listItemClick: () -> Unit = {},
    onDelete: () -> Unit = {},
    onCopy: () -> Unit = {},
    onCut: () -> Unit = {},
    onAddPage: () -> Unit = {},
    onClose: () -> Unit = {},
) {
    val iconPadding = PaddingValues(vertical = 4.dp)
    val clipShape = MaterialTheme.shapes.medium
    val spaceWidth = 8.dp

    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        val tint = MaterialTheme.colorScheme.onPrimary

        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState()),
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
            AddLinkScreen(cancel = { showLinkScreen = false }, onLinkConfirm = onLinkConfirm)
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
        Card(modifier = modifier) {
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
                        .copy(color = MaterialTheme.colorScheme.onPrimary),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.onPrimary)
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
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }

}


@Preview
@Composable
fun EditionScreenPreview() {
    EditionScreen()
}
