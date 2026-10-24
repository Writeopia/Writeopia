package io.writeopia.editor.configuration.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.writeopia.commonui.IconsPicker
import io.writeopia.resources.WrStrings
import io.writeopia.sdk.models.document.MenuItem
import io.writeopia.theme.WriteopiaTheme
import kotlinx.coroutines.flow.StateFlow

/**
 * The icons and colors for the icon next to the title, with a button to take the icon away
 * when the document has one. Used inline by the side menus and in the bottom sheet of phones.
 */
@Composable
fun DocumentIconPicker(
    documentIconState: StateFlow<MenuItem.Icon?>,
    onIconSelect: (String, Int) -> Unit,
    onRemoveIcon: () -> Unit,
    modifier: Modifier = Modifier,
    gridHeight: Dp = 220.dp,
) {
    val icon by documentIconState.collectAsState()

    Column(modifier = modifier) {
        // The picker keeps its selection: starts over once the icon is removed.
        key(icon == null) {
            IconsPicker(
                modifier = Modifier.fillMaxWidth().height(gridHeight),
                initialIconName = icon?.label,
                initialTint = icon?.tint,
                iconSelect = onIconSelect,
            )
        }

        if (icon != null) {
            TextButton(modifier = Modifier.fillMaxWidth(), onClick = onRemoveIcon) {
                Text(
                    text = WrStrings.documentIconRemove(),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

/** The icon picker of phones, opened from the actions of the global menu. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentIconSheet(
    documentIconState: StateFlow<MenuItem.Icon?>,
    onIconSelect: (String, Int) -> Unit,
    onRemoveIcon: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = WriteopiaTheme.colorScheme.lightBackground,
    ) {
        Column(modifier = Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
            Text(
                modifier = Modifier.padding(horizontal = 16.dp),
                text = WrStrings.documentIconTitle(),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            DocumentIconPicker(
                documentIconState = documentIconState,
                onIconSelect = onIconSelect,
                onRemoveIcon = onRemoveIcon,
                modifier = Modifier.padding(horizontal = 6.dp),
                gridHeight = 300.dp,
            )
        }
    }
}
