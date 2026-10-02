package io.writeopia.notemenu.ui.screen.menu

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.writeopia.common.utils.icons.WrIcons
import io.writeopia.sdk.models.document.Folder
import io.writeopia.theme.WriteopiaTheme

const val FOLDER_BREADCRUMB_TEST_TAG = "folderBreadcrumb"

/**
 * Where the folder shown is: the workspace, the folders it's inside and the folder itself, as the
 * iOS app shows it. Used by the header of the desktop app and by the folder screen of the mobile
 * app. Tapping one of them goes back to it.
 *
 * @param path the folders from the root down to the folder shown, the last one being current.
 * Empty at the root, where only "Home" is shown.
 * @param onSelect called with the folder tapped, or null for the root.
 */
@Composable
fun FolderBreadcrumb(
    path: List<Folder>,
    onSelect: (Folder?) -> Unit,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = MaterialTheme.typography.bodySmall,
) {
    val scrollState = rememberScrollState()
    val ancestors = path.dropLast(1)
    val current = path.lastOrNull()

    // The folder shown stays visible when the path is longer than the screen.
    LaunchedEffect(path.map { folder -> folder.id to folder.title }) {
        scrollState.scrollTo(scrollState.maxValue)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .testTag(FOLDER_BREADCRUMB_TEST_TAG),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (current == null) {
            CurrentCrumb(title = "Home", textStyle = textStyle)
            return@Row
        }

        Crumb(
            title = "Home",
            icon = WrIcons.home,
            textStyle = textStyle,
            onClick = { onSelect(null) }
        )

        ancestors.forEach { folder ->
            Separator()
            Crumb(title = folder.title, textStyle = textStyle, onClick = { onSelect(folder) })
        }

        Separator()

        CurrentCrumb(title = current.title, textStyle = textStyle)
    }
}

@Composable
private fun CurrentCrumb(title: String, textStyle: TextStyle) {
    Text(
        text = title,
        style = textStyle.copy(fontWeight = FontWeight.SemiBold),
        color = WriteopiaTheme.colorScheme.textLight,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .widthIn(max = CRUMB_MAX_WIDTH)
            .padding(horizontal = 4.dp, vertical = 6.dp)
            .semantics { heading() }
    )
}

@Composable
private fun Crumb(
    title: String,
    textStyle: TextStyle,
    onClick: () -> Unit,
    icon: ImageVector? = null,
) {
    Row(
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(
                modifier = Modifier.size(16.dp),
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.width(4.dp))
        }

        Text(
            text = title,
            style = textStyle,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = CRUMB_MAX_WIDTH)
        )
    }
}

@Composable
private fun Separator() {
    Icon(
        modifier = Modifier.size(14.dp),
        imageVector = WrIcons.arrowRight,
        contentDescription = null,
        tint = WriteopiaTheme.colorScheme.textLighter
    )
}

// Long titles are cut so the folder shown is reachable without scrolling past everything.
private val CRUMB_MAX_WIDTH = 160.dp
