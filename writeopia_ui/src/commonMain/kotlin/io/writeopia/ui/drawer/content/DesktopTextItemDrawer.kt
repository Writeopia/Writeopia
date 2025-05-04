package io.writeopia.ui.drawer.content

import androidx.compose.foundation.clickable
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.writeopia.sdk.model.action.Action
import io.writeopia.sdk.model.draganddrop.DropInfo
import io.writeopia.sdk.models.files.ExternalFile
import io.writeopia.ui.model.DrawInfo
import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.ui.components.SwipeBox
import io.writeopia.ui.components.multiselection.SelectableByDrag
import io.writeopia.ui.draganddrop.target.DragRowTarget
import io.writeopia.ui.draganddrop.target.DropTargetVerticalDivision
import io.writeopia.ui.draganddrop.target.InBounds
import io.writeopia.ui.draganddrop.target.external.externalImageDropTarget
import io.writeopia.ui.draganddrop.target.external.shouldAcceptImageDrop
import io.writeopia.ui.drawer.SimpleTextDrawer
import io.writeopia.ui.drawer.StoryStepDrawer
import io.writeopia.ui.drawer.decorations.DefaultTagDecoration
import io.writeopia.ui.drawer.decorations.TagDecoration
import io.writeopia.ui.model.DrawConfig

/**
 * Drawer for a complex message with swipe action, drag and drop logic and a start content to add functionality
 * like a checkbox or a different Composable.
 */
class DesktopTextItemDrawer(
    private val modifier: Modifier,
    private val customBackgroundColor: Color,
    private val clickable: Boolean,
    private val onSelected: (Boolean, Int) -> Unit,
    private val dragIconWidth: Dp,
    private val config: DrawConfig,
    private val onDragHover: (Int) -> Unit,
    private val onDragStart: () -> Unit,
    private val onDragStop: () -> Unit,
    private val moveRequest: (Action.Move) -> Unit,
    private val startContent: @Composable ((StoryStep, DrawInfo) -> Unit)?,
    private val endContent: @Composable ((StoryStep, DrawInfo, Boolean) -> Unit)? = null,
    private val isDesktop: Boolean,
    private val enabled: Boolean,
    private val receiveExternalFile: (List<ExternalFile>, Int) -> Unit,
    private val messageDrawer: @Composable RowScope.() -> SimpleTextDrawer,
    private val tagDecoration: TagDecoration = DefaultTagDecoration,
    private val paddingValues: PaddingValues = PaddingValues(0.dp),
) : StoryStepDrawer {

    @Composable
    override fun Step(step: StoryStep, drawInfo: DrawInfo) {
        val focusRequester = remember { FocusRequester() }
        val dropInfo = DropInfo(step, drawInfo.position)
        val interactionSource = remember { MutableInteractionSource() }
        val isHovered by interactionSource.collectIsHoveredAsState()
        var showDragIcon by remember { mutableStateOf(false) }

        val (paddingBottom, paddingTop) = step.tags
            .any { it.tag.isTitle() }
            .takeIf { it }
            ?.let { 4 to 16 }
            ?: (0 to 0)

        SelectableByDrag(
            modifier = Modifier.dragAndDropTarget(
                shouldStartDragAndDrop = ::shouldAcceptImageDrop,
                target = externalImageDropTarget(
                    onStart = onDragStart,
                    onEnd = onDragStop,
                    onEnter = {
                        onDragHover(drawInfo.position)
                    },
                    onExit = {},
                    onFileReceived = { files ->
                        receiveExternalFile(files, drawInfo.position + 1)
                    }
                )
            )
        ) { isInsideDrag ->
            if (isInsideDrag != null) {
                LaunchedEffect(isInsideDrag) {
                    onSelected(isInsideDrag, drawInfo.position)
                }
            }

            DropTargetVerticalDivision(
                modifier = Modifier.padding(bottom = paddingBottom.dp, top = paddingTop.dp)
            ) { inBound, data ->
                when (inBound) {
                    InBounds.OUTSIDE -> {}
                    InBounds.INSIDE_UP -> {
                        val position = drawInfo.position - 1
                        handleDrag(position, data)
                    }

                    InBounds.INSIDE_DOWN -> {
                        val position = drawInfo.position
                        handleDrag(position, data)
                    }
                }

                SwipeBox(
                    modifier = modifier
                        .hoverable(interactionSource)
                        .apply {
                            if (clickable) {
                                clickable {
                                    focusRequester.requestFocus()
                                }
                            }
                        },
                    defaultColor = customBackgroundColor,
                    activeColor = config.selectedColor(),
                    activeBorderColor = config.selectedBorderColor(),
                    isOnEditState = drawInfo.selectMode,
                    swipeListener = { isSelected ->
                        onSelected(isSelected, drawInfo.position)
                    },
                    paddingValues = paddingValues
                ) {
                    DragRowTarget(
                        modifier = Modifier
                            .fillMaxWidth()
                            .let { modifierLet ->
                                tagDecoration.decorate(modifierLet, step.tags, config)
                            }
                            .apply {
                                if (clickable) {
                                    clickable {
                                        focusRequester.requestFocus()
                                    }
                                }
                            },
                        dataToDrop = dropInfo,
                        showIcon = showDragIcon || isHovered && enabled,
                        position = drawInfo.position,
                        dragIconWidth = dragIconWidth,
                        onDragStart = onDragStart,
                        onDragStop = onDragStop,
                        isHoldDraggable = drawInfo.selectMode,
                        emptySpaceClick = {
                            focusRequester.requestFocus()
                        },
                        onClick = {
                            onSelected(!drawInfo.selectMode, drawInfo.position)
                        }
                    ) {
                        val interactionSourceText = remember { MutableInteractionSource() }
                        startContent?.invoke(step, drawInfo)

                        messageDrawer().apply {
                            onFocusChanged = { _, focusState ->
                                if (!isDesktop) {
                                    showDragIcon = focusState.hasFocus
                                }
                            }
                        }.Text(
                            step = step,
                            drawInfo = drawInfo,
                            interactionSource = interactionSourceText,
                            focusRequester = focusRequester,
                            decorationBox = @Composable { innerTextField -> innerTextField() },
                        )

                        endContent?.invoke(step, drawInfo, isHovered)
                    }
                }
            }
        }
    }

    private fun handleDrag(position: Int, data: DropInfo?) {
        onDragHover(position)

        if (data != null) {
            moveRequest(
                Action.Move(
                    data.info as StoryStep,
                    positionFrom = data.positionFrom,
                    positionTo = position
                )
            )
        }
    }
}
