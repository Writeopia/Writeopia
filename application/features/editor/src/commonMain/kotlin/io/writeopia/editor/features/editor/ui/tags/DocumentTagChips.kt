package io.writeopia.editor.features.editor.ui.tags

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.writeopia.common.utils.icons.WrIcons
import io.writeopia.resources.WrStrings
import io.writeopia.sdk.models.document.DocumentTag
import io.writeopia.theme.WriteopiaTheme

private const val VISIBLE_TAG_CHIPS = 3

/**
 * The first few tags of the document on one scrollable line of chips, each removable when
 * [removable], followed by a "+N more" chip when there are more than fit. Shared by the desktop
 * side menu and the mobile actions menu, so a document's tags look the same everywhere.
 *
 * [testTagPrefix] namespaces the test tags of the chips, so each menu can be driven in tests.
 */
@Composable
internal fun DocumentTagChipsRow(
    tags: List<DocumentTag>,
    removable: Boolean,
    onRemove: (String) -> Unit,
    onMoreClick: () -> Unit,
    testTagPrefix: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tags.take(VISIBLE_TAG_CHIPS).forEach { tag ->
            DocumentTagChip(
                tag = tag,
                removable = removable,
                onRemove = { onRemove(tag.id) },
                testTagPrefix = testTagPrefix,
            )
        }

        val hidden = tags.size - VISIBLE_TAG_CHIPS
        if (hidden > 0) {
            MoreTagsChip(count = hidden, onClick = onMoreClick, testTagPrefix = testTagPrefix)
        }
    }
}

@Composable
private fun DocumentTagChip(
    tag: DocumentTag,
    removable: Boolean,
    onRemove: () -> Unit,
    testTagPrefix: String,
) {
    Row(
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .background(WriteopiaTheme.colorScheme.optionsSelector, MaterialTheme.shapes.small)
            .padding(start = 8.dp, end = if (removable) 4.dp else 8.dp, top = 3.dp, bottom = 3.dp)
            .testTag("$testTagPrefix.chip.${tag.id}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = tag.name,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 160.dp)
        )

        if (removable) {
            Spacer(modifier = Modifier.width(2.dp))
            Icon(
                imageVector = WrIcons.close,
                contentDescription = WrStrings.documentTagsRemove(),
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onRemove)
                    .padding(1.dp),
                tint = MaterialTheme.colorScheme.onBackground
            )
        }
    }
}

/** A "+N more" chip for the tags past the first [VISIBLE_TAG_CHIPS]; it opens the tags dialog. */
@Composable
private fun MoreTagsChip(count: Int, onClick: () -> Unit, testTagPrefix: String) {
    Text(
        text = WrStrings.documentTagsMore(count),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .border(1.dp, MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 3.dp)
            .testTag("$testTagPrefix.more")
    )
}
