package io.writeopia.ui.drawer.content

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
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
import io.writeopia.ui.icons.WrSdkIcons
import io.writeopia.ui.model.DrawConfig
import io.writeopia.ui.model.DrawInfo
import org.jetbrains.compose.resources.stringResource
import writeopia.writeopia_ui.generated.resources.Res
import writeopia.writeopia_ui.generated.resources.ai_generated

/**
 * Drawer for AI answers.
 */
class AiAnswerDrawer(
    private val modifier: Modifier = Modifier,
    private val customBackgroundColor: Color,
    private val paddingValues: PaddingValues = PaddingValues(0.dp),
    private val enabled: Boolean,
    private val onSelected: (Boolean, Int) -> Unit,
    private val dragIconWidth: Dp,
    private val config: DrawConfig,
    private val onDragHover: (Int) -> Unit,
    private val onDragStart: () -> Unit,
    private val onDragStop: () -> Unit,
    private val moveRequest: (Action.Move) -> Unit,
    private val receiveExternalFile: (List<ExternalFile>, Int) -> Unit,
    private val acceptStoryStep: (Int) -> Unit
) : StoryStepDrawer {

    @Composable
    override fun Step(step: StoryStep, drawInfo: DrawInfo) {
        val dropInfo = DropInfo(step, drawInfo.position)
        val interactionSource = remember { MutableInteractionSource() }
        val isHovered by interactionSource.collectIsHoveredAsState()

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
                    modifier = modifier.hoverable(interactionSource),
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
                        BasicTextField(
                            modifier = Modifier.padding(
                                horizontal = config.iaAnswerHorizontalPadding.dp,
                                vertical = config.iaAnswerHorizontalPadding.dp
                            ),
                            value = step.text ?: "",
                            onValueChange = {},
                            textStyle = TextStyle(
                                color = MaterialTheme.colorScheme.onBackground,
                                fontSize = 16.sp,
                                fontFamily = FontFamily.Monospace
                            ),
                            readOnly = true
                        )
                    }

                    val clipboardManager = LocalClipboardManager.current

                    AnimatedVisibility(
                        isHovered,
                        enter = fadeIn(),
                        exit = fadeOut(),
                        modifier = Modifier.align(Alignment.TopEnd).padding(end = 8.dp)
                    ) {
                        Row {
                            Icon(
                                imageVector = WrSdkIcons.copy,
                                contentDescription = "Copy",
                                tint = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .clickable {
                                        clipboardManager.setText(
                                            buildAnnotatedString { append(step.text) }
                                        )
                                    }
                                    .size(32.dp)
                                    .padding(6.dp)
                            )

                            Icon(
                                imageVector = WrSdkIcons.check,
                                contentDescription = "Accept",
                                tint = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .clickable {
                                        acceptStoryStep(drawInfo.position)
                                    }
                                    .size(32.dp)
                                    .padding(6.dp)
                            )
                        }
                    }

                    AnimatedVisibility(
                        isHovered,
                        enter = fadeIn(),
                        exit = fadeOut(),
                        modifier = Modifier.align(Alignment.TopStart).padding(horizontal = 10.dp)
                    ) {
                        Text(
                            text = stringResource(Res.string.ai_generated),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold
                        )
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
