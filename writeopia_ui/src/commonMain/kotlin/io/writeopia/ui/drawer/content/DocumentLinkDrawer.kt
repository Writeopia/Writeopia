package io.writeopia.ui.drawer.content

import androidx.compose.foundation.clickable
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.writeopia.sdk.model.action.Action
import io.writeopia.sdk.model.draganddrop.DropInfo
import io.writeopia.sdk.models.files.ExternalFile
import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.ui.components.SwipeBox
import io.writeopia.ui.components.multiselection.SelectableByDrag
import io.writeopia.ui.draganddrop.target.DragRowTarget
import io.writeopia.ui.draganddrop.target.DropTargetVerticalDivision
import io.writeopia.ui.draganddrop.target.InBounds
import io.writeopia.ui.draganddrop.target.external.externalImageDropTarget
import io.writeopia.ui.draganddrop.target.external.shouldAcceptImageDrop
import io.writeopia.ui.drawer.StoryStepDrawer
import io.writeopia.ui.model.DrawConfig
import io.writeopia.ui.model.DrawInfo

/**
 * Drawer for a links to other documents.
 */
class DocumentLinkDrawer(
    private val modifier: Modifier = Modifier,
    private val onSelected: (Boolean, Int) -> Unit,
    private val dragIconWidth: Dp,
    private val config: DrawConfig,
    private val onDragHover: (Int) -> Unit,
    private val onDragStart: () -> Unit,
    private val onDragStop: () -> Unit,
    private val moveRequest: (Action.Move) -> Unit,
    private val endContent: @Composable ((StoryStep, DrawInfo, Boolean) -> Unit)? = null,
    private val enabled: Boolean,
    private val paddingValues: PaddingValues = PaddingValues(0.dp),
    private val receiveExternalFile: (List<ExternalFile>, Int) -> Unit,
    private val onClick: (String) -> Unit
) : StoryStepDrawer {

    @Composable
    override fun Step(step: StoryStep, drawInfo: DrawInfo) {
        val dropInfo = DropInfo(step, drawInfo.position)
        val interactionSource = remember { MutableInteractionSource() }
        val isHovered by interactionSource.collectIsHoveredAsState()

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

            DropTargetVerticalDivision { inBound, data ->
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
                    modifier = modifier.hoverable(interactionSource),
                    activeColor = config.selectedColor(),
                    activeBorderColor = config.selectedBorderColor(),
                    isOnEditState = drawInfo.selectMode,
                    swipeListener = { isSelected ->
                        onSelected(isSelected, drawInfo.position)
                    },
                    paddingValues = paddingValues
                ) {
                    DragRowTarget(
                        modifier = Modifier.fillMaxWidth(),
                        dataToDrop = dropInfo,
                        showIcon = isHovered && enabled,
                        position = drawInfo.position,
                        dragIconWidth = dragIconWidth,
                        onDragStart = onDragStart,
                        onDragStop = onDragStop,
                        isHoldDraggable = drawInfo.selectMode,
                        emptySpaceClick = {},
                        onClick = {
                            onSelected(!drawInfo.selectMode, drawInfo.position)
                        }
                    ) {
                        Text(
                            modifier = Modifier.padding(
                                horizontal = config.iaAnswerHorizontalPadding.dp,
                                vertical = config.iaAnswerHorizontalPadding.dp
                            ).clickable {
                                step.documentLink?.id?.let(onClick)
                            }.pointerHoverIcon(PointerIcon.Hand),
                            text = step.documentLink?.title ?: "New Document",
                            style = TextStyle(
                                color = MaterialTheme.colorScheme.onBackground,
                                fontSize = 16.sp,
                                textDecoration = TextDecoration.Underline
//                            fontFamily = fontFamily
                            ),
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
