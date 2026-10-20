package io.writeopia.editor.features.editor.ui.mobile

import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.writeopia.editor.features.editor.ui.desktop.edit.menu.AiOptions
import io.writeopia.editor.features.editor.viewmodel.AiTargetMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Mobile entry point for cloud AI. Reuses the desktop/web [AiOptions] panel content inside a
 * bottom sheet, since mobile has no room for a persistent side panel. The sheet slides away as
 * soon as an action is picked, so the user sees the answer stream into the document, same as on
 * web/desktop.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobileAiSheet(
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
    modifier: Modifier = Modifier,
    sheetMaxWidth: Dp = 500.dp,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val coroutineScope = rememberCoroutineScope()

    // The action starts right away, so the answer begins streaming while the sheet slides out.
    val runThenHide = { action: (AiTargetMode) -> Unit ->
        { mode: AiTargetMode ->
            action(mode)
            coroutineScope.launch { sheetState.hide() }.invokeOnCompletion { onDismissRequest() }
            Unit
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        sheetMaxWidth = sheetMaxWidth,
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier,
    ) {
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
            framed = false,
            askAiWithMode = runThenHide(askAiWithMode),
            aiSummary = runThenHide(aiSummary),
            aiActionPoints = runThenHide(aiActionPoints),
            aiFaq = runThenHide(aiFaq),
            aiTags = runThenHide(aiTags),
            modifier = Modifier.navigationBarsPadding().padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}
