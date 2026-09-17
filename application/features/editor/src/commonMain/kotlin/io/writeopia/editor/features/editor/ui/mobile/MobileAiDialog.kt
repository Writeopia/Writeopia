package io.writeopia.editor.features.editor.ui.mobile

import androidx.compose.runtime.Composable
import androidx.compose.ui.window.Dialog
import io.writeopia.editor.features.editor.ui.desktop.edit.menu.AiOptions
import io.writeopia.editor.features.editor.viewmodel.AiTargetMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Mobile entry point for cloud AI. Reuses the desktop/web [AiOptions] panel content inside a
 * Dialog, since mobile has no room for a persistent side panel. The dialog closes as soon as an
 * action is picked, so the user sees the answer stream into the document, same as on web/desktop.
 */
@Composable
fun MobileAiDialog(
    onDismissRequest: () -> Unit,
    currentModel: Flow<String>,
    models: Flow<List<String>>,
    hasSelectedLinesState: StateFlow<Boolean>,
    selectModel: (String) -> Unit,
    askAiWithMode: (AiTargetMode) -> Unit,
    aiSummary: (AiTargetMode) -> Unit,
    aiActionPoints: (AiTargetMode) -> Unit,
    aiFaq: (AiTargetMode) -> Unit,
    aiTags: (AiTargetMode) -> Unit,
    fixedTargetMode: AiTargetMode? = null,
) {
    Dialog(onDismissRequest = onDismissRequest) {
        AiOptions(
            currentModel = currentModel,
            models = models,
            hasSelectedLinesState = hasSelectedLinesState,
            selectModel = selectModel,
            showModelSelection = false,
            // Mobile has no block-selection UI to drive Selected Lines from the picker; it's
            // only ever used as a fixedTargetMode when opened from an active text selection.
            availableTargetModes = listOf(AiTargetMode.DOCUMENT, AiTargetMode.CURSOR),
            fixedTargetMode = fixedTargetMode,
            askAiWithMode = { mode ->
                askAiWithMode(mode)
                onDismissRequest()
            },
            aiSummary = { mode ->
                aiSummary(mode)
                onDismissRequest()
            },
            aiActionPoints = { mode ->
                aiActionPoints(mode)
                onDismissRequest()
            },
            aiFaq = { mode ->
                aiFaq(mode)
                onDismissRequest()
            },
            aiTags = { mode ->
                aiTags(mode)
                onDismissRequest()
            },
        )
    }
}
