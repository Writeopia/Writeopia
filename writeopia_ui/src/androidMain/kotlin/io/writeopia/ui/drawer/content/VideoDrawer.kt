package io.writeopia.ui.drawer.content

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import io.writeopia.ui.drawer.video.VideoPlayer
import io.writeopia.ui.drawer.video.rememberVideoPlayerState
import io.writeopia.ui.icons.WrSdkIcons
import io.writeopia.ui.model.DrawConfig
import io.writeopia.ui.model.DrawInfo

/**
 * Draws a video player using ExoPlayer/Media3.
 */
class VideoDrawer(
    private val config: DrawConfig,
    private val containerModifier: (Boolean) -> Modifier? = { null },
    private val onDragStart: () -> Unit,
    private val onDragStop: () -> Unit,
    private val onSelected: (Boolean, Double) -> Unit,
    private val mergeRequest: (Action.Merge) -> Unit = { },
    private val onDelete: (Action.DeleteStory) -> Unit
) : StoryStepDrawer {

    @Composable
    override fun Step(step: StoryStep, drawInfo: DrawInfo) {
        val videoUrl = step.url

        // Return early if no URL
        if (videoUrl.isNullOrBlank()) return

        val dropInfo = remember { DropInfo(step, drawInfo.position) }
        val interactionSource = remember { MutableInteractionSource() }

        SelectableByDrag { isInsideDrag ->
            if (isInsideDrag != null) {
                LaunchedEffect(isInsideDrag) {
                    onSelected(isInsideDrag, drawInfo.position)
                }
            }

            DropTarget(
                modifier = Modifier.padding(horizontal = 6.dp)
            ) { inBound, data ->
                val videoModifier =
                    containerModifier(inBound) ?: Modifier.defaultVideoShape(inBound)

                SwipeBox(
                    modifier = Modifier.hoverable(interactionSource).fillMaxWidth(),
                    defaultColor = MaterialTheme.colorScheme.surfaceVariant,
                    activeColor = config.selectedColor(),
                    activeBorderColor = config.selectedBorderColor(),
                    borderWidth = 3.dp,
                    isOnEditState = drawInfo.selectMode,
                    swipeListener = { isSelected ->
                        onSelected(isSelected, drawInfo.position)
                    }
                ) {
                    DragCardTarget(
                        modifier = Modifier.clip(MaterialTheme.shapes.large).align(Alignment.Center),
                        position = drawInfo.position,
                        dataToDrop = dropInfo,
                        iconTintOnHover = MaterialTheme.colorScheme.onBackground,
                        onDragStart = onDragStart,
                        onDragStop = onDragStop,
                        onIconClick = {
                            onSelected(!drawInfo.selectMode, drawInfo.position)
                        },
                    ) {
                        Box(modifier = videoModifier) {
                            DragTarget(
                                modifier = videoModifier,
                                dataToDrop = DropInfo(step, drawInfo.position)
                            ) {
                                val playerState = rememberVideoPlayerState(videoUrl)

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(shape = RoundedCornerShape(size = 12.dp))
                                        .border(
                                            width = 1.dp,
                                            color = Color.Gray,
                                            shape = RoundedCornerShape(size = 12.dp)
                                        )
                                        .aspectRatio(16f / 9f)
                                ) {
                                    VideoPlayer(
                                        playerState = playerState,
                                        modifier = Modifier.matchParentSize()
                                    )
                                }
                            }
                        }

                        Icon(
                            modifier = Modifier
                                .padding(4.dp)
                                .clip(CircleShape)
                                .clickable {
                                    onDelete(
                                        Action.DeleteStory(
                                            step,
                                            drawInfo.position
                                        )
                                    )
                                }
                                .clip(CircleShape)
                                .align(Alignment.TopEnd)
                                .padding(6.dp),
                            imageVector = WrSdkIcons.close,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
            }
        }
    }
}

fun Modifier.defaultVideoShape(inBound: Boolean) =
    clip(shape = RoundedCornerShape(size = 12.dp))
        .border(width = 1.dp, if (inBound) Color.LightGray else Color.DarkGray)
