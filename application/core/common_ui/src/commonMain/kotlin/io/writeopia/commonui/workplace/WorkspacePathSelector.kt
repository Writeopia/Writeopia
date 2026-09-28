package io.writeopia.commonui.workplace

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Shows the local folder of the workspace. Clicking it opens [WorkspaceConfigurationDialog] so the
 * user can type a path or pick a directory.
 */
@Composable
fun WorkspacePathSelector(
    workplacePath: String,
    selectWorkplacePath: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showEditPathDialog by remember {
        mutableStateOf(false)
    }

    val textShape = MaterialTheme.shapes.medium

    Text(
        workplacePath,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onBackground,
        fontWeight = FontWeight.Bold,
        modifier = modifier.border(
            1.dp,
            MaterialTheme.colorScheme.onSurfaceVariant,
            textShape
        )
            .clip(shape = textShape)
            .clickable {
                showEditPathDialog = true
            }
            .padding(8.dp)
            .fillMaxWidth()
    )

    if (showEditPathDialog) {
        WorkspaceConfigurationDialog(
            currentPath = workplacePath,
            pathChange = selectWorkplacePath,
            onDismissRequest = {
                showEditPathDialog = false
            },
            onConfirmation = {
                showEditPathDialog = false
            },
        )
    }
}
