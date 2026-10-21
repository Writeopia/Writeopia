package io.writeopia.editor.features.editor.ui.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.writeopia.editor.features.editor.ui.desktop.edit.menu.AiTargetButton
import io.writeopia.editor.features.editor.ui.desktop.edit.menu.TextButton
import io.writeopia.editor.features.editor.ui.desktop.edit.menu.Title
import io.writeopia.editor.features.editor.ui.desktop.edit.menu.aiButtonPadding
import io.writeopia.editor.features.editor.ui.desktop.edit.menu.aiButtonTextStyle
import io.writeopia.editor.features.editor.ui.desktop.edit.menu.targetModeLabel
import io.writeopia.editor.features.editor.viewmodel.AiTargetMode
import io.writeopia.resources.WrStrings
import kotlinx.coroutines.flow.StateFlow

private const val COMMAND_TAB = 0
private const val QUICK_ACTIONS_TAB = 1

/**
 * The AI options, in two tabs, shared by the desktop side panel and the mobile sheet: a free
 * command about the document and the lines selected when it is typed, which the AI either
 * applies to those lines or answers after them, and the quick actions (summary, action points,
 * FAQ, tags), with "Apply to" picking the text they work on.
 */
@Composable
internal fun AiOptionsTabs(
    hasSelectedLinesState: StateFlow<Boolean>,
    availableTargetModes: List<AiTargetMode>,
    fixedTargetMode: AiTargetMode?,
    aiSummary: (AiTargetMode) -> Unit,
    aiActionPoints: (AiTargetMode) -> Unit,
    aiFaq: (AiTargetMode) -> Unit,
    aiTags: (AiTargetMode) -> Unit,
    aiCommand: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(COMMAND_TAB) }
    var command by rememberSaveable { mutableStateOf("") }

    Column(modifier = modifier) {
        Title(WrStrings.askAi())

        Spacer(modifier = Modifier.height(8.dp))

        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onBackground,
        ) {
            Tab(
                selected = selectedTab == COMMAND_TAB,
                onClick = { selectedTab = COMMAND_TAB },
                text = { Text(WrStrings.aiCommand(), style = MaterialTheme.typography.labelLarge) }
            )
            Tab(
                selected = selectedTab == QUICK_ACTIONS_TAB,
                onClick = { selectedTab = QUICK_ACTIONS_TAB },
                text = { Text(WrStrings.quickActions(), style = MaterialTheme.typography.labelLarge) }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        when (selectedTab) {
            COMMAND_TAB -> CommandTab(
                command = command,
                onCommandChange = { command = it },
                enabled = command.isNotBlank(),
                onRun = { aiCommand(command) },
            )

            else -> AiQuickActions(
                hasSelectedLinesState = hasSelectedLinesState,
                availableTargetModes = availableTargetModes,
                fixedTargetMode = fixedTargetMode,
                aiSummary = aiSummary,
                aiActionPoints = aiActionPoints,
                aiFaq = aiFaq,
                aiTags = aiTags,
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
    }
}

/**
 * The quick actions of the AI (summary, action points, FAQ, tags), with "Apply to" picking the
 * text they work on. The second tab of [AiOptionsTabs], and the whole AI panel on desktop, where
 * the free command has a field of its own at the bottom of the editor.
 */
@Composable
internal fun AiQuickActions(
    hasSelectedLinesState: StateFlow<Boolean>,
    availableTargetModes: List<AiTargetMode>,
    fixedTargetMode: AiTargetMode?,
    aiSummary: (AiTargetMode) -> Unit,
    aiActionPoints: (AiTargetMode) -> Unit,
    aiFaq: (AiTargetMode) -> Unit,
    aiTags: (AiTargetMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    var targetMode by remember {
        mutableStateOf(fixedTargetMode ?: availableTargetModes.firstOrNull() ?: AiTargetMode.DOCUMENT)
    }
    val hasSelectedLines by hasSelectedLinesState.collectAsState()

    // Nothing runs when Selected Lines is the target but no lines are selected.
    val enabled = targetMode != AiTargetMode.SELECTED_LINES || hasSelectedLines

    Column(modifier = modifier) {
        Text(
            text = WrStrings.applyTo(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(bottom = 4.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (fixedTargetMode != null) {
                AiTargetButton(
                    text = targetModeLabel(fixedTargetMode),
                    isSelected = true,
                    onClick = {},
                    modifier = Modifier.weight(1f)
                )
            } else {
                availableTargetModes.forEach { mode ->
                    AiTargetButton(
                        text = targetModeLabel(mode),
                        isSelected = targetMode == mode,
                        onClick = { targetMode = mode },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        if (fixedTargetMode == null && targetMode == AiTargetMode.SELECTED_LINES && !hasSelectedLines) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = WrStrings.selectLinesFirst(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // The quick actions all work on existing text, so there is none for the cursor, where
        // only new text can be written: the command tab is the way there.
        if (targetMode == AiTargetMode.CURSOR) {
            Text(
                text = WrStrings.quickActionsCursorHint(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
            )
            return
        }

        TextButton(
            modifier = Modifier.fillMaxWidth(),
            text = WrStrings.summary(),
            paddingValues = aiButtonPadding(),
            textStyle = aiButtonTextStyle(),
            enabled = enabled,
            onClick = { aiSummary(targetMode) }
        )

        Spacer(modifier = Modifier.height(2.dp))

        TextButton(
            modifier = Modifier.fillMaxWidth(),
            text = WrStrings.actionPoints(),
            paddingValues = aiButtonPadding(),
            textStyle = aiButtonTextStyle(),
            enabled = enabled,
            onClick = { aiActionPoints(targetMode) }
        )

        Spacer(modifier = Modifier.height(2.dp))

        TextButton(
            modifier = Modifier.fillMaxWidth(),
            text = "FAQ",
            paddingValues = aiButtonPadding(),
            textStyle = aiButtonTextStyle(),
            enabled = enabled,
            onClick = { aiFaq(targetMode) }
        )

        Spacer(modifier = Modifier.height(2.dp))

        TextButton(
            modifier = Modifier.fillMaxWidth(),
            text = "Tags",
            paddingValues = aiButtonPadding(),
            textStyle = aiButtonTextStyle(),
            enabled = enabled,
            onClick = { aiTags(targetMode) }
        )
    }
}

@Composable
private fun CommandTab(
    command: String,
    onCommandChange: (String) -> Unit,
    enabled: Boolean,
    onRun: () -> Unit,
) {
    Column {
        OutlinedTextField(
            value = command,
            onValueChange = onCommandChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text(
                    text = WrStrings.aiCommandHint(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            textStyle = MaterialTheme.typography.bodyMedium,
            minLines = 3,
            maxLines = 6,
            shape = MaterialTheme.shapes.medium,
        )

        Spacer(modifier = Modifier.height(8.dp))

        TextButton(
            modifier = Modifier.fillMaxWidth(),
            text = WrStrings.aiCommandRun(),
            highlight = true,
            paddingValues = aiButtonPadding(),
            textStyle = aiButtonTextStyle(),
            enabled = enabled,
            onClick = onRun
        )
    }
}
