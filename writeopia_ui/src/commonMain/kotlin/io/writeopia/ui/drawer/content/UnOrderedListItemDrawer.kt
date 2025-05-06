package io.writeopia.ui.drawer.content

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.writeopia.sdk.model.action.Action
import io.writeopia.sdk.models.files.ExternalFile
import io.writeopia.ui.model.DrawInfo
import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.sdk.models.story.StoryTypes
import io.writeopia.ui.drawer.SimpleTextDrawer
import io.writeopia.ui.drawer.StoryStepDrawer
import io.writeopia.ui.manager.WriteopiaStateManager
import io.writeopia.ui.model.DrawConfig
import kotlinx.coroutines.flow.MutableStateFlow
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * Drawer for a unordered list. This type of item it just a normal message with some decoration
 * at the start of Composable to show that this is part of a list.
 */

fun unOrderedListItemDrawer(
    isDesktop: Boolean,
    manager: WriteopiaStateManager,
    modifier: Modifier = Modifier,
    enabled: Boolean,
    dragIconWidth: Dp = 16.dp,
    checkBoxPadding: PaddingValues = PaddingValues(0.dp),
    config: DrawConfig,
    receiveExternalFile: (List<ExternalFile>, Int) -> Unit,
    messageDrawer: @Composable RowScope.() -> SimpleTextDrawer
): StoryStepDrawer = unOrderedListItemDrawer(
    isDesktop = isDesktop,
    modifier = modifier,
    onSelected = manager::onSelected,
    customBackgroundColor = Color.Transparent,
    checkBoxPadding = checkBoxPadding,
    config = config,
    onDragHover = manager::onDragHover,
    onDragStart = manager::onDragStart,
    onDragStop = manager::onDragStop,
    moveRequest = manager::moveRequest,
    dragIconWidth = dragIconWidth,
    enabled = enabled,
    receiveExternalFile = receiveExternalFile,
    messageDrawer = messageDrawer,
)

fun unOrderedListItemDrawer(
    isDesktop: Boolean,
    modifier: Modifier = Modifier,
    customBackgroundColor: Color = Color.Transparent,
    clickable: Boolean = true,
    onSelected: (Boolean, Int) -> Unit = { _, _ -> },
    enabled: Boolean,
    dragIconWidth: Dp = 16.dp,
    checkBoxPadding: PaddingValues = PaddingValues(0.dp),
    config: DrawConfig,
    onDragHover: (Int) -> Unit,
    onDragStart: () -> Unit,
    onDragStop: () -> Unit,
    moveRequest: (Action.Move) -> Unit = {},
    receiveExternalFile: (List<ExternalFile>, Int) -> Unit,
    startContent: @Composable ((StoryStep, DrawInfo) -> Unit)? = { _, _ ->
        Text(
            modifier = Modifier.padding(checkBoxPadding),
            text = "-",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
    },
    messageDrawer: @Composable RowScope.() -> SimpleTextDrawer
): StoryStepDrawer =
    DesktopTextItemDrawer(
        modifier,
        customBackgroundColor,
        clickable,
        onSelected,
        dragIconWidth,
        config,
        onDragHover,
        onDragStart,
        onDragStop,
        moveRequest,
        startContent,
        null,
        isDesktop,
        enabled,
        receiveExternalFile,
        messageDrawer
    )

@Preview
@Composable
private fun UnOrderedListItemPreview() {
    val modifier = Modifier
        .background(Color.White)
        .padding(vertical = 4.dp, horizontal = 6.dp)
        .fillMaxWidth()

    unOrderedListItemDrawer(
        modifier = modifier,
        isDesktop = true,
        onDragHover = {},
        onDragStart = {},
        onDragStop = {},
        enabled = true,
        config = DrawConfig(),
        receiveExternalFile = { _, _ -> },
        messageDrawer = {
            TextDrawer(
                selectionState = MutableStateFlow(false),
                onSelectionLister = {},
                isDarkTheme = true
            )
        }
    ).Step(
        StoryStep(type = StoryTypes.UNORDERED_LIST_ITEM.type, text = "Item1"),
        DrawInfo()
    )
}
