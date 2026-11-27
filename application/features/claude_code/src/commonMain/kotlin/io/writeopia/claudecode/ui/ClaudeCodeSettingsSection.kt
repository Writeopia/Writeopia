package io.writeopia.claudecode.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.writeopia.claudecode.ClaudeCodeConnection
import io.writeopia.claudecode.ClaudeCodeState
import io.writeopia.resources.WrStrings
import io.writeopia.theme.WriteopiaTheme

/**
 * The Claude Code part of the settings: the switch of the local MCP server and the command that
 * connects Claude Code to it. Shows nothing where the app has no server (only the desktop has).
 */
@Composable
fun ClaudeCodeSettingsSection(modifier: Modifier = Modifier) {
    val controller = ClaudeCodeConnection.controller ?: return
    val state by controller.state.collectAsState()
    val textColor = MaterialTheme.colorScheme.onBackground

    Column(modifier = modifier) {
        Text(
            WrStrings.claudeCodeTitle(),
            style = MaterialTheme.typography.titleLarge,
            color = textColor
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            WrStrings.claudeCodeDescription(),
            style = MaterialTheme.typography.bodyMedium,
            color = textColor
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                WrStrings.claudeCodeEnable(),
                style = MaterialTheme.typography.bodyLarge,
                color = textColor,
                modifier = Modifier.weight(1F)
            )
            Switch(
                checked = state !is ClaudeCodeState.Off,
                onCheckedChange = controller::setEnabled
            )
        }

        when (val current = state) {
            is ClaudeCodeState.Running -> {
                Spacer(modifier = Modifier.height(12.dp))
                CommandBox(pluginCommand = current.pluginCommand)
            }

            is ClaudeCodeState.Failed -> {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    current.reason,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }

            ClaudeCodeState.Off -> {}
        }
    }
}

@Composable
private fun CommandBox(pluginCommand: String) {
    val textColor = MaterialTheme.colorScheme.onBackground

    Column {
        Text(
            WrStrings.claudeCodeCommandHint(),
            style = MaterialTheme.typography.bodyMedium,
            color = textColor
        )

        Spacer(modifier = Modifier.height(8.dp))

        CopyableCommand(pluginCommand)
    }
}

@Composable
private fun CopyableCommand(command: String) {
    val clipboard = LocalClipboardManager.current
    var copied by remember(command) { mutableStateOf(false) }
    val textColor = MaterialTheme.colorScheme.onBackground

    Column {
        SelectionContainer {
            Text(
                command,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = textColor,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(WriteopiaTheme.colorScheme.cardBg, RoundedCornerShape(8.dp))
                    .padding(12.dp)
            )
        }

        Row(horizontalArrangement = Arrangement.Start) {
            TextButton(onClick = {
                clipboard.setText(AnnotatedString(command))
                copied = true
            }) {
                Text(if (copied) WrStrings.claudeCodeCopied() else WrStrings.claudeCodeCopy())
            }
        }
    }
}
