package io.writeopia.ui.drawer.content

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.writeopia.sdk.model.action.Action
import io.writeopia.sdk.model.draganddrop.DropInfo
import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.ui.components.SwipeBox
import io.writeopia.ui.components.multiselection.SelectableByDrag
import io.writeopia.ui.draganddrop.target.DragCardTarget
import io.writeopia.ui.draganddrop.target.DragTarget
import io.writeopia.ui.draganddrop.target.DropTarget
import io.writeopia.ui.drawer.StoryStepDrawer
import io.writeopia.ui.icons.WrSdkIcons
import io.writeopia.ui.model.DrawConfig
import io.writeopia.ui.model.DrawInfo
import io.writeopia.ui.platform.openFile

/**
 * Draws a reference to a PDF file that was dropped/added into the document. Clicking it opens
 * the file with the platform's default handler (currently only wired up on Desktop, see
 * [openFile]).
 */
class PdfDrawer(
    private val config: DrawConfig,
    private val onDragStart: () -> Unit,
    private val onDragStop: () -> Unit,
    private val onSelected: (Boolean, Double) -> Unit,
    private val onDelete: (Action.DeleteStory) -> Unit
) : StoryStepDrawer {

    @Composable
    override fun Step(step: StoryStep, drawInfo: DrawInfo) {
        val dropInfo = remember { DropInfo(step, drawInfo.position) }
        val interactionSource = remember { MutableInteractionSource() }
        val filePath = step.url ?: step.path

        SelectableByDrag { isInsideDrag ->
            if (isInsideDrag != null) {
                LaunchedEffect(isInsideDrag) {
                    onSelected(isInsideDrag, drawInfo.position)
                }
            }

            DropTarget(modifier = Modifier.padding(horizontal = 6.dp)) { _, _ ->
                SwipeBox(
                    modifier = Modifier.hoverable(interactionSource).fillMaxWidth(),
                    defaultColor = MaterialTheme.colorScheme.surfaceVariant,
                    activeColor = config.selectedColor(),
                    activeBorderColor = config.selectedBorderColor(),
                    borderWidth = 3.dp,
                    isOnEditState = drawInfo.selectMode,
                    swipeListener = { isSelected -> onSelected(isSelected, drawInfo.position) }
                ) {
                    DragCardTarget(
                        modifier = Modifier.clip(MaterialTheme.shapes.medium)
                            .align(Alignment.Center),
                        position = drawInfo.position,
                        dataToDrop = dropInfo,
                        iconTintOnHover = MaterialTheme.colorScheme.onBackground,
                        onDragStart = onDragStart,
                        onDragStop = onDragStop,
                        onIconClick = { onSelected(!drawInfo.selectMode, drawInfo.position) },
                    ) {
                        DragTarget(
                            modifier = Modifier.fillMaxWidth(),
                            dataToDrop = DropInfo(step, drawInfo.position)
                        ) {
                            PdfRow(
                                filePath = filePath,
                                onOpen = { path -> openFile(path) },
                                onDelete = {
                                    onDelete(Action.DeleteStory(step, drawInfo.position))
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PdfRow(filePath: String?, onOpen: (String) -> Unit, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(enabled = filePath != null) {
                filePath?.let(onOpen)
            }
            .pointerHoverIcon(PointerIcon.Hand)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            modifier = Modifier.size(20.dp),
            imageVector = WrSdkIcons.pdf,
            contentDescription = "PDF file",
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Text(
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp),
            text = filePath?.substringAfterLast('/') ?: "PDF file",
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = TextStyle(
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )
        )

        Icon(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .clickable(onClick = onDelete),
            imageVector = WrSdkIcons.close,
            contentDescription = "Remove PDF",
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
