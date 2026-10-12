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
import io.writeopia.writingagent.model.WritingSuggestionsState

const val WRITING_SUGGESTIONS_BOX_TAG = "WritingSuggestionsBox"

/**
 * The box at the bottom right with what the writing agent suggests. Each suggestion has a button
 * that applies it. The box is hidden while there is nothing to suggest. The user can fold it
 * down to a small "AI suggestions" badge with the count, to focus on the text, and open it again
 * from the badge.
 */
@Composable
fun WritingSuggestionsBox(
    controller: WritingAgentController,
    ui: WritingAgentUi,
    modifier: Modifier = Modifier,
) {
    val state by controller.state.collectAsState()
    val collapsed by controller.collapsed.collectAsState()

    AnimatedVisibility(
        visible = state.hasSuggestions,
        enter = slideInVertically(initialOffsetY = { it / 2 }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it / 2 }) + fadeOut(),
        modifier = modifier.testTag(WRITING_SUGGESTIONS_BOX_TAG)
    ) {
        if (collapsed) {
            CollapsedBadge(
                count = state.count,
                isLoading = state.isLoading,
                onExpand = controller::toggleCollapsed
            )
        } else {
            ExpandedBox(state = state, controller = controller, ui = ui)
        }
    }
}

/** The folded box: the AI icon, "AI suggestions" and how many there are. A click unfolds it. */
@Composable
private fun CollapsedBadge(
    count: Int,
    isLoading: Boolean,
    onExpand: () -> Unit,
) {
    // The same corners as the open box, so folding only changes the size.
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onExpand)
            .testTag("$WRITING_SUGGESTIONS_BOX_TAG.badge"),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 4.dp,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                Icon(
                    imageVector = WrIcons.ai,
                    contentDescription = WrStrings.writingSuggestionsShow(),
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = WrStrings.writingSuggestionsBadge(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.width(8.dp))

            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.primary
            ) {
                Text(
                    text = count.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun ExpandedBox(
    state: WritingSuggestionsState,
    controller: WritingAgentController,
    ui: WritingAgentUi,
) {
    Surface(
        modifier = Modifier
            .widthIn(min = 220.dp, max = 320.dp)
            .clip(RoundedCornerShape(12.dp)),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 4.dp,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 8.dp)) {
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

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = WrIcons.smallArrowDown,
                        contentDescription = WrStrings.writingSuggestionsHide(),
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .clickable(onClick = controller::toggleCollapsed)
                            .padding(2.dp)
                            .testTag("$WRITING_SUGGESTIONS_BOX_TAG.collapse"),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.width(4.dp))

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
            }

            Spacer(modifier = Modifier.height(4.dp))

            state.suggestions.forEach { suggestion ->
                SuggestionRow(
                    label = suggestion.action.label(),
                    testTag = "$WRITING_SUGGESTIONS_BOX_TAG.${suggestion.action}",
                    onApply = { controller.execute(suggestion.action, ui) }
                )
                Spacer(modifier = Modifier.height(2.dp))
            }

            if (state.offerFolder) {
                SuggestionRow(
                    label = WrStrings.writingSuggestionFolder(),
                    testTag = "$WRITING_SUGGESTIONS_BOX_TAG.folder",
                    onApply = controller::openFolderDialog
                )
                Spacer(modifier = Modifier.height(2.dp))
            }

            if (state.offerTags) {
                SuggestionRow(
                    label = WrStrings.writingSuggestionTags(),
                    testTag = "$WRITING_SUGGESTIONS_BOX_TAG.tags",
                    onApply = controller::openTagDialog
                )
                Spacer(modifier = Modifier.height(2.dp))
            }
        }
    }
}

@Composable
private fun SuggestionRow(
    label: String,
    testTag: String,
    onApply: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onApply)
            .padding(start = 8.dp, end = 4.dp, top = 3.dp, bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )

        Spacer(modifier = Modifier.width(8.dp))

        // A plain label instead of a Material button: the button's 40dp minimum made every row tall.
        Text(
            text = WrStrings.writingSuggestionApply(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .clickable(onClick = onApply)
                .padding(horizontal = 6.dp, vertical = 2.dp)
                .testTag(testTag)
        )
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
