package io.writeopia.editor.features.editor.ui.mobile

import androidx.compose.foundation.layout.imePadding
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
import io.writeopia.editor.features.editor.viewmodel.AiTargetMode
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Mobile entry point for cloud AI: the [MobileAiOptions] in a bottom sheet, since mobile has
 * no room for a persistent side panel. The sheet slides away as soon as an action is picked, so
 * the user sees the answer arrive in the document, same as on web/desktop.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobileAiSheet(
    onDismissRequest: () -> Unit,
    hasSelectedLinesState: StateFlow<Boolean>,
    aiSummary: (AiTargetMode) -> Unit,
    aiActionPoints: (AiTargetMode) -> Unit,
    aiFaq: (AiTargetMode) -> Unit,
    aiTags: (AiTargetMode) -> Unit,
    aiCommand: (String, AiTargetMode) -> Unit,
    fixedTargetMode: AiTargetMode? = null,
    modifier: Modifier = Modifier,
    sheetMaxWidth: Dp = 500.dp,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val coroutineScope = rememberCoroutineScope()

    // The action starts right away, so the answer begins while the sheet slides out.
    val hide = {
        coroutineScope.launch { sheetState.hide() }.invokeOnCompletion { onDismissRequest() }
        Unit
    }
    val runThenHide = { action: (AiTargetMode) -> Unit ->
        { mode: AiTargetMode ->
            action(mode)
            hide()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        sheetMaxWidth = sheetMaxWidth,
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier,
    ) {
        MobileAiOptions(
            hasSelectedLinesState = hasSelectedLinesState,
            // Mobile has no block-selection UI to drive Selected Lines from the picker; it's
            // only ever used as a fixedTargetMode when opened from an active text selection.
            availableTargetModes = listOf(AiTargetMode.DOCUMENT, AiTargetMode.CURSOR),
            fixedTargetMode = fixedTargetMode,
            aiSummary = runThenHide(aiSummary),
            aiActionPoints = runThenHide(aiActionPoints),
            aiFaq = runThenHide(aiFaq),
            aiTags = runThenHide(aiTags),
            aiCommand = { command, mode ->
                aiCommand(command, mode)
                hide()
            },
            modifier = Modifier
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 20.dp, vertical = 4.dp),
        )
    }
}
