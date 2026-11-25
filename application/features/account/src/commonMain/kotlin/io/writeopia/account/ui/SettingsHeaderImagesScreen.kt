package io.writeopia.account.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.writeopia.account.di.AccountMenuKmpInjector
import io.writeopia.account.viewmodel.WorkspaceHeaderImagesState
import io.writeopia.ai.WorkspaceHeaderImage
import io.writeopia.common.utils.icons.WrIcons
import io.writeopia.resources.WrStrings
import io.writeopia.ui.image.HeaderImage

const val SETTINGS_HEADER_IMAGES_TAG = "SettingsHeaderImages"

private val TILE_MIN_WIDTH = 200.dp
private val TILE_SPACING = 12.dp

/** The header pictures page, with its view model. Loads the pictures each time it opens. */
@Composable
fun SettingsHeaderImagesRoute(modifier: Modifier = Modifier) {
    val viewModel = AccountMenuKmpInjector.singleton().provideWorkspaceHeaderImagesViewModel()
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.load()
    }

    SettingsHeaderImagesScreen(
        state = state,
        onRetry = viewModel::load,
        onDelete = viewModel::delete,
        onDismissDeleteError = viewModel::dismissDeleteError,
        modifier = modifier,
    )
}

/**
 * The header pictures of the workspace, drawn with the AI for the documents, each with a button
 * to delete it. Deleting asks first. It doesn't scroll: the page around it does.
 */
@Composable
fun SettingsHeaderImagesScreen(
    state: WorkspaceHeaderImagesState,
    onRetry: () -> Unit,
    onDelete: (String) -> Unit,
    onDismissDeleteError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var toDelete by remember { mutableStateOf<WorkspaceHeaderImage?>(null) }

    Column(modifier = modifier.fillMaxWidth().testTag(SETTINGS_HEADER_IMAGES_TAG)) {
        Text(
            text = WrStrings.settingsHeaderImagesDescription(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (state.deleteFailed) {
            Text(
                text = WrStrings.settingsHeaderImageDeleteFailed(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.clickable(onClick = onDismissDeleteError).padding(bottom = 12.dp)
            )
        }

        when (state.status) {
            WorkspaceHeaderImagesState.Status.LOADING -> {
                Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
                }
            }

            WorkspaceHeaderImagesState.Status.OFFLINE -> {
                Message(WrStrings.settingsHeaderImagesOffline())
            }

            WorkspaceHeaderImagesState.Status.FAILED -> {
                Message(WrStrings.settingsHeaderImagesFailed())
                TextButton(onClick = onRetry, modifier = Modifier.testTag("$SETTINGS_HEADER_IMAGES_TAG.retry")) {
                    Text(WrStrings.retry())
                }
            }

            WorkspaceHeaderImagesState.Status.LOADED -> {
                if (state.images.isEmpty()) {
                    Message(WrStrings.settingsHeaderImagesEmpty())
                } else {
                    HeaderImagesGrid(
                        images = state.images,
                        deleting = state.deleting,
                        onDelete = { image -> toDelete = image },
                    )
                }
            }
        }
    }

    toDelete?.let { image ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text(WrStrings.settingsHeaderImageDeleteTitle()) },
            text = { Text(WrStrings.settingsHeaderImageDeleteMessage()) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(image.id)
                        toDelete = null
                    },
                    modifier = Modifier.testTag("$SETTINGS_HEADER_IMAGES_TAG.confirmDelete")
                ) {
                    Text(WrStrings.delete(), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { toDelete = null }) {
                    Text(WrStrings.cancel())
                }
            }
        )
    }
}

/** The pictures in as many columns as fit, without a lazy grid, since the page scrolls. */
@Composable
private fun HeaderImagesGrid(
    images: List<WorkspaceHeaderImage>,
    deleting: Set<String>,
    onDelete: (WorkspaceHeaderImage) -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val columns = ((maxWidth + TILE_SPACING) / (TILE_MIN_WIDTH + TILE_SPACING)).toInt().coerceIn(1, 4)

        Column(verticalArrangement = Arrangement.spacedBy(TILE_SPACING)) {
            images.chunked(columns).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(TILE_SPACING)) {
                    row.forEach { image ->
                        HeaderImageTile(
                            image = image,
                            isDeleting = image.id in deleting,
                            onDelete = { onDelete(image) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    // The last row keeps the width of the others.
                    repeat(columns - row.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun HeaderImageTile(
    image: WorkspaceHeaderImage,
    isDeleting: Boolean,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .testTag("$SETTINGS_HEADER_IMAGES_TAG.image.${image.id}")
    ) {
        HeaderImage(
            url = image.url,
            contentDescription = null,
            modifier = Modifier.fillMaxSize().alpha(if (isDeleting) 0.4f else 1f)
        )

        if (isDeleting) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center).size(24.dp),
                strokeWidth = 2.dp
            )
        } else {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f))
                    .clickable(onClick = onDelete)
                    .testTag("$SETTINGS_HEADER_IMAGES_TAG.delete.${image.id}"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = WrIcons.delete,
                    contentDescription = WrStrings.delete(),
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun Message(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}
