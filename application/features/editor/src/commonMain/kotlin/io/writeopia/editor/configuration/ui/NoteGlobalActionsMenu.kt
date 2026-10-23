package io.writeopia.editor.configuration.ui

// import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.writeopia.commonui.buttons.verticalPaddingCommonButton
import io.writeopia.editor.features.editor.ui.desktop.edit.menu.DeleteButton
import io.writeopia.editor.features.editor.ui.desktop.edit.menu.FavoriteButton
import io.writeopia.editor.features.editor.ui.desktop.edit.menu.FontOptions
import io.writeopia.editor.features.editor.ui.desktop.edit.menu.LockButton
import io.writeopia.editor.features.editor.ui.desktop.edit.menu.MoveToButton
import io.writeopia.editor.features.editor.ui.tags.DocumentTagChipsRow
import io.writeopia.model.Font
import io.writeopia.resources.WrStrings
import io.writeopia.sdk.models.document.DocumentTag
import io.writeopia.theme.WriteopiaTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

const val GLOBAL_MENU_TAGS_TAG = "GlobalMenuTags"

// @Preview
@Composable
internal fun NoteGlobalActionsMenu(
    isEditableState: StateFlow<Boolean>,
    setEditable: () -> Unit,
    isFavoriteState: StateFlow<Boolean>,
    toggleFavorite: () -> Unit,
    onMoveToClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onShareJson: () -> Unit = {},
    onShareMd: () -> Unit = {},
    changeFontFamily: (Font) -> Unit,
    selectedState: StateFlow<Font>,
    onPublishClick: () -> Unit = {},
    showPresentation: Boolean = false,
    onPresentationClick: () -> Unit = {},
    documentTagsState: StateFlow<List<DocumentTag>> = MutableStateFlow(emptyList()),
    onViewTagsClick: () -> Unit = {},
    onAddTagClick: () -> Unit = {},
    onRemoveTagClick: (String) -> Unit = {},
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 16.dp)
            .pointerInput(Unit) { detectTapGestures { } }
    ) {
        // Scrolls when the actions don't fit, like on small screens or with large fonts
        Column(modifier = Modifier.widthIn(max = 500.dp).verticalScroll(rememberScrollState())) {
            Spacer(modifier = Modifier.height(16.dp))

            Title(WrStrings.actions())

            Spacer(modifier = Modifier.height(8.dp))

            // Two per row, like the other buttons of the menu
            Row {
                LockButton(isEditableState, setEditable, modifier = Modifier.weight(1F))

                Spacer(modifier = Modifier.width(4.dp))

                FavoriteButton(isFavoriteState, toggleFavorite, modifier = Modifier.weight(1F))
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row {
                MoveToButton(onMoveToClick, modifier = Modifier.weight(1F))

                Spacer(modifier = Modifier.width(4.dp))

                DeleteButton(onDeleteClick, modifier = Modifier.weight(1F))
            }

            Spacer(modifier = Modifier.height(16.dp))

            Title(WrStrings.documentTagsTitle())

            Spacer(modifier = Modifier.height(8.dp))

            DocumentTagsSection(
                documentTagsState = documentTagsState,
                isEditableState = isEditableState,
                onViewTagsClick = onViewTagsClick,
                onAddTagClick = onAddTagClick,
                onRemoveTagClick = onRemoveTagClick,
            )

            Spacer(modifier = Modifier.height(16.dp))

            Title(WrStrings.font())

            Spacer(modifier = Modifier.height(8.dp))

            FontOptions(
                modifier = Modifier.fillMaxWidth(),
                changeFontFamily = changeFontFamily,
                selectedState = selectedState,
                selectedColor = WriteopiaTheme.colorScheme.highlight,
                defaultColor = MaterialTheme.colorScheme.background
            )

            if (showPresentation) {
                Spacer(modifier = Modifier.height(16.dp))

                Title("Presentation")

                Spacer(modifier = Modifier.height(8.dp))

                ShareButton(
                    modifier = Modifier.fillMaxWidth(),
                    text = "Presentations",
                    onClick = onPresentationClick
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Title(WrStrings.export())

            Spacer(modifier = Modifier.height(8.dp))

            Row {
                ShareButton(
                    modifier = Modifier.weight(1F),
                    text = WrStrings.exportJson(),
                    onClick = onShareJson
                )

                Spacer(modifier = Modifier.width(4.dp))

                ShareButton(
                    modifier = Modifier.weight(1F),
                    text = WrStrings.exportMarkdown(),
                    onClick = onShareMd
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Title(WrStrings.publish())

            Spacer(modifier = Modifier.height(8.dp))

            ShareButton(
                modifier = Modifier.fillMaxWidth(),
                text = WrStrings.publishToWeb(),
                onClick = onPublishClick
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * The tags of the document as chips, each removable while the document is editable, with a
 * button to see them with their explanations and one to add a tag. The mobile counterpart of
 * the section in the desktop side menu.
 */
@Composable
private fun DocumentTagsSection(
    documentTagsState: StateFlow<List<DocumentTag>>,
    isEditableState: StateFlow<Boolean>,
    onViewTagsClick: () -> Unit,
    onAddTagClick: () -> Unit,
    onRemoveTagClick: (String) -> Unit,
) {
    val tags by documentTagsState.collectAsState()
    val isEditable by isEditableState.collectAsState()

    if (tags.isEmpty()) {
        Text(
            text = WrStrings.documentTagsNone(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 2.dp)
        )
    } else {
        DocumentTagChipsRow(
            tags = tags,
            removable = isEditable,
            onRemove = onRemoveTagClick,
            onMoreClick = onViewTagsClick,
            testTagPrefix = GLOBAL_MENU_TAGS_TAG,
        )
    }

    Spacer(modifier = Modifier.height(8.dp))

    Row {
        ShareButton(
            modifier = Modifier.weight(1F).testTag("$GLOBAL_MENU_TAGS_TAG.view"),
            text = WrStrings.documentTagsView(),
            onClick = onViewTagsClick
        )

        Spacer(modifier = Modifier.width(4.dp))

        ShareButton(
            modifier = Modifier.weight(1F).testTag("$GLOBAL_MENU_TAGS_TAG.add"),
            text = WrStrings.documentTagsAdd(),
            enabled = isEditable,
            onClick = onAddTagClick
        )
    }
}

@Composable
private fun Title(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onBackground
    )
}

@Composable
private fun ShareButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Text(
        modifier = modifier
            .alpha(if (enabled) 1F else DISABLED_ALPHA)
            .clip(RoundedCornerShape(6.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .background(MaterialTheme.colorScheme.background)
            // The height of the lock button, so all the buttons of the menu match
            .padding(horizontal = 8.dp, vertical = verticalPaddingCommonButton().dp),
        text = text,
        color = MaterialTheme.colorScheme.onBackground,
        style = MaterialTheme.typography.bodySmall.copy(
            fontWeight = FontWeight.Bold
        ),
        textAlign = TextAlign.Center
    )
}

private const val DISABLED_ALPHA = 0.5F
