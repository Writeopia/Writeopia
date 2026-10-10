package io.writeopia.writingagent.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.writeopia.app.dto.writingagent.WritingSuggestionAction
import io.writeopia.common.utils.icons.WrIcons
import io.writeopia.resources.WrStrings
import io.writeopia.writingagent.actions.WritingAgentUi
import io.writeopia.writingagent.controller.WritingAgentController
import io.writeopia.writingagent.model.WritingSuggestion

const val WRITING_SUGGESTIONS_BOX_TAG = "WritingSuggestionsBox"

/**
 * The box at the bottom right with what the writing agent suggests. Each suggestion has a button
 * that applies it. The box is hidden while there is nothing to suggest.
 */
@Composable
fun WritingSuggestionsBox(
    controller: WritingAgentController,
    ui: WritingAgentUi,
    modifier: Modifier = Modifier,
) {
    val state by controller.state.collectAsState()

    AnimatedVisibility(
        visible = state.hasSuggestions,
        enter = slideInVertically(initialOffsetY = { it / 2 }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it / 2 }) + fadeOut(),
        modifier = modifier.testTag(WRITING_SUGGESTIONS_BOX_TAG)
    ) {
        Surface(
            modifier = Modifier
                .widthIn(min = 220.dp, max = 320.dp)
                .clip(RoundedCornerShape(12.dp)),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shadowElevation = 4.dp,
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (state.isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Icon(
                                imageVector = WrIcons.ai,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Text(
                            text = WrStrings.writingSuggestions(),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Icon(
                        imageVector = WrIcons.close,
                        contentDescription = WrStrings.dismiss(),
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .clickable(onClick = controller::dismiss)
                            .padding(2.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                state.suggestions.forEach { suggestion ->
                    SuggestionRow(
                        suggestion = suggestion,
                        onApply = { controller.execute(suggestion.action, ui) }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        }
    }
}

@Composable
private fun SuggestionRow(
    suggestion: WritingSuggestion,
    onApply: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onApply)
            .padding(start = 8.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = suggestion.action.label(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )

        TextButton(onClick = onApply, modifier = Modifier.testTag("$WRITING_SUGGESTIONS_BOX_TAG.${suggestion.action}")) {
            Text(
                text = WrStrings.writingSuggestionApply(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun WritingSuggestionAction.label(): String =
    when (this) {
        WritingSuggestionAction.CODE_BLOCK -> WrStrings.writingSuggestionCodeBlock()
        WritingSuggestionAction.LIST -> WrStrings.writingSuggestionList()
        WritingSuggestionAction.CHECK_LIST -> WrStrings.writingSuggestionCheckList()
        WritingSuggestionAction.NEW_DOCUMENT_LINK -> WrStrings.writingSuggestionNewDocumentLink()
        WritingSuggestionAction.IMAGE -> WrStrings.writingSuggestionImage()
        WritingSuggestionAction.DRAWING -> WrStrings.writingSuggestionDrawing()
        WritingSuggestionAction.SPREADSHEET -> WrStrings.writingSuggestionSpreadsheet()
        WritingSuggestionAction.SECTION_HEADING -> WrStrings.writingSuggestionSectionHeading()
        WritingSuggestionAction.DOCUMENT_TITLE -> WrStrings.writingSuggestionDocumentTitle()
        WritingSuggestionAction.CALLOUT -> WrStrings.writingSuggestionCallout()
        WritingSuggestionAction.TLDR -> WrStrings.writingSuggestionTldr()
        WritingSuggestionAction.CONCLUSION -> WrStrings.writingSuggestionConclusion()
    }
