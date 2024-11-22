package io.writeopia.ui.drawer.content

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalMinimumInteractiveComponentEnforcement
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.writeopia.sdk.model.action.Action
import io.writeopia.ui.model.DrawInfo
import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.sdk.models.story.StoryTypes
import io.writeopia.ui.drawer.SimpleTextDrawer
import io.writeopia.ui.drawer.StoryStepDrawer
import io.writeopia.ui.manager.WriteopiaStateManager
import kotlinx.coroutines.flow.MutableStateFlow
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * Check item drawer. Draws a checkbox followed by a text.
 */
@OptIn(ExperimentalMaterial3Api::class)
fun checkItemDrawer(
    modifier: Modifier = Modifier,
    customBackgroundColor: Color = Color.Transparent,
    clickable: Boolean = true,
    onSelected: (Boolean, Int) -> Unit = { _, _ -> },
    dragIconWidth: Dp = 16.dp,
    onCheckedChange: (Action.StoryStateChange) -> Unit = {},
    onDragHover: (Int) -> Unit,
    onDragStart: () -> Unit,
    onDragStop: () -> Unit,
    moveRequest: (Action.Move) -> Unit,
    startContent: @Composable ((StoryStep, DrawInfo) -> Unit)? = { step, drawInfo ->
        CompositionLocalProvider(LocalMinimumInteractiveComponentEnforcement provides false) {
            Spacer(modifier = Modifier.width(8.dp))
            Checkbox(
                checked = step.checked ?: false,
                onCheckedChange = { checked ->
                    onCheckedChange(
                        Action.StoryStateChange(
                            step.copy(checked = checked),
                            drawInfo.position
                        )
                    )
                },
                modifier.padding(vertical = 4.dp),
                enabled = drawInfo.editable,
            )
        }
    },
    messageDrawer: @Composable RowScope.() -> SimpleTextDrawer
): StoryStepDrawer =
    DesktopTextItemDrawer(
        modifier = modifier,
        customBackgroundColor,
        clickable,
        onSelected,
        dragIconWidth,
        onDragHover,
        onDragStart,
        onDragStop,
        moveRequest,
        startContent,
        messageDrawer
    )

fun checkItemDrawer(
    manager: WriteopiaStateManager,
    modifier: Modifier = Modifier,
    dragIconWidth: Dp = 16.dp,
    messageDrawer: @Composable RowScope.() -> SimpleTextDrawer
): StoryStepDrawer = checkItemDrawer(
    modifier = modifier,
    onCheckedChange = manager::changeStoryState,
    onSelected = manager::onSelected,
    customBackgroundColor = Color.Transparent,
    dragIconWidth = dragIconWidth,
    onDragHover = manager::onDragHover,
    onDragStart = manager::onDragStart,
    onDragStop = manager::onDragStop,
    moveRequest = manager::moveRequest,
    messageDrawer = {
        messageDrawer()
    },
)

@Preview
@Composable
fun CheckItemDrawerStepPreview() {
    checkItemDrawer(
        modifier = Modifier,
        onDragHover = {},
        onDragStart = {},
        onDragStop = {},
        moveRequest = {},
        messageDrawer = {
            TextDrawer(
                selectionState = MutableStateFlow(false),
                onSelectionLister = {}
            )
        }).Step(
        step = StoryStep(
            type = StoryTypes.CHECK_ITEM.type,
            text = "This is a check item"
        ),
        drawInfo = DrawInfo(),
    )
}
