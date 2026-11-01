package io.writeopia.ui.drawer.content

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.writeopia.sdk.model.action.Action
import io.writeopia.sdk.models.files.ExternalFile
import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.ui.drawer.SimpleTextDrawer
import io.writeopia.ui.drawer.StoryStepDrawer
import io.writeopia.ui.manager.WriteopiaStateManager
import io.writeopia.ui.model.DrawConfig
import io.writeopia.ui.model.DrawInfo

/**
 * Drawer for the "message_box" type: a message drawn on a background colour, given by
 * [StoryStep.decoration] and falling back to the theme.
 */
fun textBoxDrawer(
    isDesktop: Boolean,
    manager: WriteopiaStateManager,
    modifier: Modifier = Modifier,
    enabled: Boolean,
    dragIconWidth: Dp = 16.dp,
    config: DrawConfig,
    receiveExternalFile: (List<ExternalFile>, String) -> Unit,
    messageDrawer: @Composable RowScope.() -> SimpleTextDrawer,
): StoryStepDrawer = TextBoxDrawer(
    modifier = modifier,
    isDesktop = isDesktop,
    onSelected = manager::onSelected,
    dragIconWidth = dragIconWidth,
    config = config,
    onDragHover = manager::onDragHover,
    onDragStart = manager::onDragStart,
    onDragStop = manager::onDragStop,
    moveRequest = manager::moveRequest,
    enabled = enabled,
    receiveExternalFile = receiveExternalFile,
    messageDrawer = messageDrawer,
)

class TextBoxDrawer(
    private val modifier: Modifier = Modifier,
    private val isDesktop: Boolean,
    private val onSelected: (Boolean, String) -> Unit,
    private val dragIconWidth: Dp = 16.dp,
    private val config: DrawConfig,
    private val onDragHover: (String) -> Unit,
    private val onDragStart: () -> Unit,
    private val onDragStop: () -> Unit,
    private val moveRequest: (Action.Move) -> Unit,
    private val enabled: Boolean,
    private val receiveExternalFile: (List<ExternalFile>, String) -> Unit,
    private val messageDrawer: @Composable RowScope.() -> SimpleTextDrawer,
) : StoryStepDrawer {

    @Composable
    override fun Step(step: StoryStep, drawInfo: DrawInfo) {
        DesktopTextItemDrawer(
            modifier = modifier,
            customBackgroundColor = textBoxColor(
                step.decoration.backgroundColor,
                MaterialTheme.colorScheme.surfaceVariant,
            ),
            clickable = true,
            onSelected = onSelected,
            dragIconWidth = dragIconWidth,
            config = config,
            onDragHover = onDragHover,
            onDragStart = onDragStart,
            onDragStop = onDragStop,
            moveRequest = moveRequest,
            startContent = null,
            isDesktop = isDesktop,
            enabled = enabled,
            receiveExternalFile = receiveExternalFile,
            messageDrawer = messageDrawer,
        ).Step(step, drawInfo)
    }
}

internal fun textBoxColor(backgroundColor: Int?, fallback: Color): Color =
    backgroundColor?.let { Color(it) } ?: fallback
