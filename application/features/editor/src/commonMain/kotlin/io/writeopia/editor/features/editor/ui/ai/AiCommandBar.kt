package io.writeopia.editor.features.editor.ui.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import io.writeopia.common.utils.icons.WrIcons
import io.writeopia.resources.WrStrings
import io.writeopia.theme.WriteopiaTheme
import io.writeopia.ui.drawer.factory.isEnterKey

/**
 * The field at the bottom of the desktop editor where the user types a free command to the AI,
 * about the document and the lines selected when it is sent. Enter sends it (Shift+Enter breaks
 * the line), so does the button. It shows with the AI options of the side menu and takes the
 * focus then. The field is in the screen itself, not in a popup, so it can take the keyboard
 * focus on desktop.
 */
@Composable
internal fun AiCommandBar(
    onRun: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var command by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val canRun = command.isNotBlank()
    val run = {
        if (canRun) {
            onRun(command.trim())
            command = ""
        }
    }
    val shape = MaterialTheme.shapes.large

    Row(
        modifier = modifier
            .widthIn(max = 640.dp)
            .clip(shape)
            .background(WriteopiaTheme.colorScheme.cardBg, shape)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BasicTextField(
            value = command,
            onValueChange = { command = it },
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester)
                .onPreviewKeyEvent { keyEvent ->
                    if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key.isEnterKey() && !keyEvent.isShiftPressed) {
                        run()
                        true
                    } else {
                        false
                    }
                }
                // Every key the field handled stops here. Otherwise it reaches the window, whose
                // shortcuts act on the editor: Backspace would delete the selected lines.
                .onKeyEvent { true },
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onBackground),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.onBackground),
            maxLines = 4,
            decorationBox = { innerTextField ->
                if (command.isEmpty()) {
                    Text(
                        text = WrStrings.aiCommandHint(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                innerTextField()
            }
        )

        Spacer(modifier = Modifier.width(8.dp))

        Icon(
            imageVector = WrIcons.ai,
            contentDescription = WrStrings.aiCommandRun(),
            tint = if (canRun) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .clip(CircleShape)
                .clickable(enabled = canRun, onClick = run)
                .padding(8.dp)
                .size(20.dp)
        )
    }

    // The field shows when the user opens the AI options, to type right away.
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}
