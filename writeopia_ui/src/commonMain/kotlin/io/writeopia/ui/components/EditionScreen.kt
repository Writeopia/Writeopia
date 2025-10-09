package io.writeopia.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import com.composables.icons.lucide.Lucide
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Bold
import com.composables.icons.lucide.Italic
import com.composables.icons.lucide.Scissors
import com.composables.icons.lucide.Trash2
import com.composables.icons.lucide.Underline
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
                imageVector = Lucide.Bold,
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
                imageVector = Lucide.Italic,
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
                imageVector = Lucide.Underline,
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
                imageVector = Lucide.Scissors,
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
                imageVector = Lucide.Trash2,
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
                imageVector = Lucide.Bold,
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
                imageVector = Lucide.Italic,
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
                imageVector = Lucide.Underline,
                contentDescription = "UNDERLINE",
//            contentDescription = stringResource(R.string.delete),
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

@Preview
@Composable
fun EditionScreenPreview() {
    EditionScreen()
}
