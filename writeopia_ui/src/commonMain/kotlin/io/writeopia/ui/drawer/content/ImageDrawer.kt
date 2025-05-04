package io.writeopia.ui.drawer.content

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.LocalPlatformContext
import coil3.compose.SubcomposeAsyncImage
import coil3.request.ImageRequest
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

/**
 * Draws a image. Uses Coil to parse the image.
 */
class ImageDrawer(
    private val config: DrawConfig,
    private val containerModifier: (Boolean) -> Modifier? = { null },
    private val onDragStart: () -> Unit,
    private val onDragStop: () -> Unit,
    private val onSelected: (Boolean, Int) -> Unit,
    private val mergeRequest: (Action.Merge) -> Unit = { },
    private val onDelete: (Action.DeleteStory) -> Unit
) : StoryStepDrawer {

    @Composable
    override fun Step(step: StoryStep, drawInfo: DrawInfo) {
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
//            if (inBound && data != null) {
//                mergeRequest(
//                    Action.Merge(
//                        receiver = step,
//                        sender = data.info as StoryStep,
//                        positionFrom = data.positionFrom,
//                        positionTo = drawInfo.position
//                    )
//                )
//            }

                val imageModifier =
                    containerModifier(inBound) ?: Modifier.defaultImageShape(inBound)
                val focusRequester = remember { FocusRequester() }

                SwipeBox(
                    modifier = Modifier.hoverable(interactionSource),
                    defaultColor = MaterialTheme.colorScheme.surfaceVariant,
                    activeColor = config.selectedColor(),
                    activeBorderColor = config.selectedBorderColor(),
                    isOnEditState = drawInfo.selectMode,
                    swipeListener = { isSelected ->
                        onSelected(isSelected, drawInfo.position)
                    }
                ) {
                    DragCardTarget(
                        modifier = Modifier.clip(MaterialTheme.shapes.large),
                        position = drawInfo.position,
                        dataToDrop = dropInfo,
                        iconTintOnHover = MaterialTheme.colorScheme.onBackground,
                        onDragStart = onDragStart,
                        onDragStop = onDragStop,
                        onIconClick = {
                            onSelected(!drawInfo.selectMode, drawInfo.position)
                        },
                    ) {
                        Column(
                            modifier = imageModifier
                                .focusRequester(focusRequester)
                                .onGloballyPositioned {
                                    if (drawInfo.hasFocus()) {
                                        focusRequester.requestFocus()
                                    }
                                },
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            DragTarget(
                                modifier = imageModifier,
                                dataToDrop = DropInfo(step, drawInfo.position)
                            ) {
                                SubcomposeAsyncImage(
                                    model = ImageRequest.Builder(LocalPlatformContext.current)
                                        .data(step.url ?: step.path)
                                        .build(),
                                    contentScale = ContentScale.Crop,
                                    contentDescription = "",
                                    modifier = Modifier.clip(
                                        shape = RoundedCornerShape(size = 12.dp)
                                    ),
                                    loading = {
                                        CircularProgressIndicator()
                                    }
                                )
                            }
                            step.text?.let { text ->
                                Text(
                                    text = text,
                                    modifier = Modifier
                                        .padding(6.dp)
                                        .fillMaxWidth(),
                                    style = TextStyle(
                                        color = MaterialTheme.colorScheme.onBackground,
                                        textAlign = TextAlign.Center,
                                        fontWeight = FontWeight.Bold
                                    ),
                                )
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
                            contentDescription = "Trash",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
            }
        }
    }
}

fun Modifier.defaultImageShape(inBound: Boolean) =
    clip(shape = RoundedCornerShape(size = 12.dp))
//        .background(if (inBound) Color.LightGray else Color.DarkGray)
        .border(width = 1.dp, if (inBound) Color.LightGray else Color.DarkGray)
