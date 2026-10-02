package io.writeopia.notes.desktop.components

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import io.writeopia.common.utils.configuration.LocalPlatform
import io.writeopia.common.utils.configuration.PlatformType
import io.writeopia.common.utils.NotesNavigation
import io.writeopia.common.utils.icons.PlatformIcons
import io.writeopia.notemenu.navigation.navigateBackToNotes
import io.writeopia.notemenu.ui.screen.configuration.modifier.icon
import io.writeopia.notemenu.ui.screen.menu.FolderBreadcrumb
import io.writeopia.sdk.models.document.Folder
import kotlinx.coroutines.flow.StateFlow

@Composable
fun GlobalHeader(
    navigationController: NavHostController,
    pathState: StateFlow<List<Folder>>,
    toggleMaxScreen: () -> Unit,
) {
    val platform = LocalPlatform.current

    Row(
        modifier = Modifier.fillMaxWidth()
            .padding(6.dp)
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onDoubleClick = toggleMaxScreen,
                onClick = {}
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Only show navigation arrows on desktop, not on web
        if (platform == PlatformType.DESKTOP) {
            Icon(
                modifier = Modifier.icon {
                    if (navigationController.previousBackStackEntry != null) {
                        navigationController.navigateUp()
                    }
                },
                imageVector = PlatformIcons.backArrowMobile,
                contentDescription = "Navigate back",
                tint = MaterialTheme.colorScheme.onBackground
            )
        }

        val path by pathState.collectAsState()

        FolderBreadcrumb(
            path = path,
            onSelect = { folder ->
                navigationController.navigateBackToNotes(
                    folder?.let { NotesNavigation.Folder(it.id) } ?: NotesNavigation.Root
                )
            },
            modifier = Modifier.padding(start = 8.dp),
            textStyle = MaterialTheme.typography.bodyMedium
        )
    }
}
