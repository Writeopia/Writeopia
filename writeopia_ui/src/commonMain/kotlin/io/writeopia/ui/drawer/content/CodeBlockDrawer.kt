package io.writeopia.ui.drawer.content

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontFamily
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
import io.writeopia.ui.drawer.SimpleTextDrawer
import io.writeopia.ui.drawer.StoryStepDrawer
import io.writeopia.ui.model.DrawConfig
import io.writeopia.ui.model.DrawInfo

private const val CODE_BLOCK_POSITION_KEY = "codeBlockPosition"
private const val CODE_BLOCK_LINE_NUMBER_KEY = "codeBlockLineNumber"

class CodeBlockDrawer(
    private val dragIconWidth: Dp = 16.dp,
    private val config: DrawConfig,
    private val onSelected: (Boolean, Int) -> Unit = { _, _ -> },
    private val onDragHover: (Int) -> Unit,
    private val onDragStart: () -> Unit = {},
    private val onDragStop: () -> Unit = {},
    private val moveRequest: (Action.Move) -> Unit = {},
    private val enabled: Boolean,
    private val isDesktop: Boolean,
    private val receiveExternalFile: (List<ExternalFile>, Int) -> Unit,
    private val messageDrawer: @Composable RowScope.() -> SimpleTextDrawer,
) : StoryStepDrawer {

    @Composable
    override fun Step(step: StoryStep, drawInfo: DrawInfo) {
        val focusRequester = remember { FocusRequester() }
        val dropInfo = DropInfo(step, drawInfo.position)
        val interactionSource = remember { MutableInteractionSource() }
        val isHovered by interactionSource.collectIsHoveredAsState()
        var showDragIcon by remember { mutableStateOf(false) }

        val position = drawInfo.extraData[CODE_BLOCK_POSITION_KEY] as? Int ?: 2
        val lineNumber = drawInfo.extraData[CODE_BLOCK_LINE_NUMBER_KEY] as? Int ?: 1
        val shape = shapeForPosition(position)
        val padding = paddingForPosition(position, config)

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
                modifier = Modifier
//                    .padding(start = config.textDrawerStartPadding.dp)
                    .background(config.codeBlockBackgroundColor(), shape)
                    .padding(padding)
            ) { inBound, data ->
                when (inBound) {
                    InBounds.OUTSIDE -> {}
                    InBounds.INSIDE_UP -> {
                        val pos = drawInfo.position - 1
                        handleDrag(pos, data)
                    }

                    InBounds.INSIDE_DOWN -> {
                        val pos = drawInfo.position
                        handleDrag(pos, data)
                    }
                }

                SwipeBox(
                    modifier = Modifier
                        .hoverable(interactionSource)
                        .clickable {
                            focusRequester.requestFocus()
                        },
                    defaultColor = Color.Transparent,
                    activeColor = config.selectedColor(),
                    activeBorderColor = config.selectedBorderColor(),
                    isOnEditState = drawInfo.selectMode,
                    swipeListener = { isSelected ->
                        onSelected(isSelected, drawInfo.position)
                    },
                    paddingValues = PaddingValues(0.dp)
                ) {
                    DragRowTarget(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                focusRequester.requestFocus()
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

                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier.width(32.dp),
                                contentAlignment = Alignment.CenterEnd
                            ) {
                                Text(
                                    text = lineNumber.toString(),
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                            }

                            VerticalDivider(
                                modifier = Modifier.fillMaxHeight(),
                                thickness = 1.dp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                            )

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
                        }
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

    companion object {
        private val corner = 8.dp

        fun shapeForPosition(position: Int): Shape =
            when (position) {
                -1 -> RoundedCornerShape(topStart = corner, topEnd = corner)
                1 -> RoundedCornerShape(bottomStart = corner, bottomEnd = corner)
                2 -> RoundedCornerShape(corner)
                else -> RoundedCornerShape(0.dp)
            }

        fun paddingForPosition(position: Int, config: DrawConfig): PaddingValues {
            val verticalPadding = 8.dp
            val horizontalPadding = config.codeBlockStartPadding.dp

            return when (position) {
                -1 -> PaddingValues(start = horizontalPadding, top = verticalPadding)
                1 -> PaddingValues(start = horizontalPadding, bottom = verticalPadding)
                2 -> PaddingValues(
                    start = horizontalPadding,
                    top = verticalPadding,
                    bottom = verticalPadding
                )

                else -> PaddingValues(start = horizontalPadding)
            }
        }
    }
}
