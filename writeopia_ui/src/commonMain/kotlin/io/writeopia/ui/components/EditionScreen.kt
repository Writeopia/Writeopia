package io.writeopia.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.outlined.FormatBold
import androidx.compose.material.icons.outlined.FormatItalic
import androidx.compose.material.icons.outlined.FormatUnderlined
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import io.writeopia.sdk.models.span.Span
import io.writeopia.ui.icons.WrSdkIcons
import org.jetbrains.compose.ui.tooling.preview.Preview

// Here
// This screen could live in a module for extra Composables. In the future there will be more
// buttons here
@Composable
fun EditionScreen(
    modifier: Modifier = Modifier,
    onSpanClick: (Span) -> Unit = {},
    checkboxClick: () -> Unit = {},
    listItemClick: () -> Unit = {},
    onDelete: () -> Unit = {},
    onCopy: () -> Unit = {},
    onCut: () -> Unit = {},
    onClose: () -> Unit = {},
    onAddPage: () -> Unit = {},
) {
    val iconPadding = PaddingValues(vertical = 4.dp)
    val clipShape = MaterialTheme.shapes.medium
    val iconSize = 36.dp
    val spaceWidth = 8.dp

    Row(modifier = modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
        val tint = MaterialTheme.colorScheme.onPrimary

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
                .size(32.dp)
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
                .size(32.dp)
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

        Spacer(modifier = Modifier.weight(1F))

        Icon(
            modifier = Modifier
                .clip(clipShape)
                .clickable(onClick = onClose)
                .size(iconSize)
                .padding(iconPadding),
            imageVector = WrSdkIcons.close,
            contentDescription = "List item",
//            contentDescription = stringResource(R.string.delete),
            tint = tint
        )
    }
}

@Preview
@Composable
fun EditionScreenPreview() {
    EditionScreen()
}
