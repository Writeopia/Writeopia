package io.writeopia.commonui.tabs

import androidx.compose.foundation.background
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isAltPressed
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.isTertiaryPressed
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Opens a document or folder in a new tab (id, title, is a folder). Provided by the desktop app
 * around its screens, so lists deep down can offer it without passing it through every screen;
 * null where there are no tabs.
 */
val LocalOpenInNewTab = staticCompositionLocalOf<((String, String, Boolean) -> Unit)?> { null }

/**
 * The desktop gestures that open a document or folder in a new tab, like a browser: the middle
 * button, or Cmd/Ctrl + click (with no Alt: Cmd/Ctrl + Alt is the multi-selection). The right
 * button asks for [onContextMenu]. Seen before the item's own click, which then doesn't happen.
 * Nothing changes when [onOpenInNewTab] is null (no tabs: the web, the phones).
 */
fun Modifier.openInNewTabGestures(
    onOpenInNewTab: (() -> Unit)?,
    onContextMenu: (() -> Unit)? = null,
): Modifier {
    if (onOpenInNewTab == null) return this

    return pointerInput(onOpenInNewTab, onContextMenu) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.type != PointerEventType.Press) continue

                val buttons = event.buttons
                val keys = event.keyboardModifiers
                val commandClick = buttons.isPrimaryPressed &&
                    (keys.isMetaPressed || keys.isCtrlPressed) &&
                    !keys.isAltPressed

                when {
                    buttons.isTertiaryPressed || commandClick -> {
                        event.changes.forEach { it.consume() }
                        onOpenInNewTab()
                    }

                    buttons.isSecondaryPressed && onContextMenu != null -> {
                        event.changes.forEach { it.consume() }
                        onContextMenu()
                    }
                }
            }
        }
    }
}

/** The right-click menu of an item that can open in a new tab. */
@Composable
fun OpenInNewTabMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    onOpenInNewTab: () -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = Modifier.background(MaterialTheme.colorScheme.background),
    ) {
        DropdownMenuItem(
            text = { Text("Open in new tab", style = MaterialTheme.typography.bodySmall) },
            onClick = {
                onDismissRequest()
                onOpenInNewTab()
            }
        )
    }
}
