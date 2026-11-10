package io.writeopia.ui.drawer.content

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImagePainter
import coil3.compose.LocalPlatformContext
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import coil3.size.Size
import io.writeopia.sdk.model.action.Action
import io.writeopia.sdk.model.draganddrop.DropInfo
import io.writeopia.sdk.models.files.ExternalFile
import io.writeopia.sdk.models.story.EquationImage
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

/**
 * An equation: the LaTeX in the text of the step, shown as the picture the backend rendered of it
 * (`url`): black glyphs on a transparent background at [EquationImage.SCALE] pixels per point,
 * tinted here with the text color and shown at a [EquationImage.SCALE]th of their size, so the
 * same picture reads well in both themes. Without a picture yet, the LaTeX is shown as text. The
 * pencil asks the app to edit the LaTeX.
 */
class EquationDrawer(
    private val showEditAlways: Boolean,
    private val customBackgroundColor: Color,
    private val config: DrawConfig,
    private val dragIconWidth: Dp,
    private val enabled: Boolean,
    private val onDragHover: (String) -> Unit,
    private val onDragStart: () -> Unit,
    private val onDragStop: () -> Unit,
    private val moveRequest: (Action.Move) -> Unit,
    private val onSelected: (Boolean, String) -> Unit,
    private val receiveExternalFile: (List<ExternalFile>, String) -> Unit,
    private val onEditClick: (String) -> Unit,
) : StoryStepDrawer {

    @Composable
    override fun Step(
        step: StoryStep,
        drawInfo: DrawInfo
    ) {
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
                        receiveExternalFile(files, drawInfo.position)
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
            ) { inBound, data ->
                when (inBound) {
                    InBounds.OUTSIDE -> {}
                    InBounds.INSIDE_UP -> {
                        handleDrag(drawInfo.previousPosition ?: drawInfo.position, data)
                    }

                    InBounds.INSIDE_DOWN -> {
                        handleDrag(drawInfo.position, data)
                    }
                }

                SwipeBox(
                    modifier = Modifier.hoverable(interactionSource),
                    defaultColor = customBackgroundColor,
                    activeColor = config.selectedColor(),
                    activeBorderColor = config.selectedBorderColor(),
                    isOnEditState = drawInfo.selectMode,
                    swipeListener = { isSelected ->
                        onSelected(isSelected, drawInfo.position)
                    },
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
                        Box(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                            Row(
                                modifier = Modifier.align(Alignment.Center).padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                EquationContent(
                                    step = step,
                                    modifier = Modifier
                                        .weight(1f, fill = false)
                                        .testTag("Equation_${drawInfo.position}")
                                )

                                Spacer(modifier = Modifier.width(6.dp))

                                Crossfade(
                                    targetState = (isHovered || showEditAlways) && enabled,
                                    label = "iconCrossFade",
                                    animationSpec = tween(durationMillis = 200)
                                ) { show ->
                                    if (show) {
                                        Icon(
                                            WrSdkIcons.edit,
                                            contentDescription = "Edit equation",
                                            tint = MaterialTheme.colorScheme.onBackground,
                                            modifier = Modifier.size(32.dp)
                                                .clip(CircleShape)
                                                .clickable { onEditClick(drawInfo.position) }
                                                .padding(8.dp)
                                                .testTag("EquationEdit_${drawInfo.position}")
                                        )
                                    } else {
                                        Spacer(modifier = Modifier.size(32.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun handleDrag(position: String, data: DropInfo?) {
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

/**
 * The picture of the equation at its natural size (pixels over [EquationImage.SCALE]), shrunk
 * only when wider than the room it has, or the LaTeX as text while there is no picture.
 */
@Composable
private fun EquationContent(step: StoryStep, modifier: Modifier = Modifier) {
    val url = step.url
    val latex = step.text ?: ""
    val tint = MaterialTheme.colorScheme.onBackground

    if (url == null) {
        EquationLatexText(latex, modifier)
        return
    }

    // The original size is asked for up front: the painter is only drawn once the picture is
    // there, and without a size it would wait for that drawing forever.
    val request = ImageRequest.Builder(LocalPlatformContext.current)
        .data(url)
        .size(Size.ORIGINAL)
        .build()
    val painter = rememberAsyncImagePainter(model = request)
    val state by painter.state.collectAsState()

    when (state) {
        is AsyncImagePainter.State.Error -> EquationLatexText(latex, modifier)

        is AsyncImagePainter.State.Success -> {
            val intrinsic = painter.intrinsicSize
            BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
                val naturalWidth = if (intrinsic.isSpecified) (intrinsic.width / EquationImage.SCALE).dp else 0.dp
                val naturalHeight = if (intrinsic.isSpecified) (intrinsic.height / EquationImage.SCALE).dp else 0.dp
                val fit = if (naturalWidth > maxWidth && naturalWidth > 0.dp) maxWidth / naturalWidth else 1f

                Image(
                    painter = painter,
                    contentDescription = latex,
                    contentScale = ContentScale.Fit,
                    colorFilter = ColorFilter.tint(tint),
                    modifier = Modifier.width(naturalWidth * fit).height(naturalHeight * fit)
                )
            }
        }

        else -> {
            Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(8.dp))
                EquationLatexText(latex, Modifier)
            }
        }
    }
}

@Composable
private fun EquationLatexText(latex: String, modifier: Modifier) {
    Text(
        text = latex,
        modifier = modifier,
        fontFamily = FontFamily.Monospace,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onBackground
    )
}
