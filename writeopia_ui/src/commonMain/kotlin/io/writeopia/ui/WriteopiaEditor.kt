package io.writeopia.ui

import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import io.writeopia.ui.components.AutoScrollLazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.writeopia.sdk.model.story.Selection
import io.writeopia.ui.draganddrop.target.DraggableScreen
import io.writeopia.ui.drawer.StoryStepDrawer
import io.writeopia.ui.model.DrawInfo
import io.writeopia.ui.model.DrawState
import io.writeopia.ui.model.DrawStory

@Composable
fun WriteopiaEditor(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    editable: Boolean = true,
    listState: LazyListState = rememberLazyListState(),
    drawers: Map<Int, StoryStepDrawer>,
    storyState: DrawState,
    keyFn: (DrawStory) -> Int = { drawStory ->
        drawStory.desktopKey + (drawStory.cursor?.position ?: 0)
    }
) {
    val content = storyState.stories
        .filterNot { draw ->
            draw.storyStep.tags.any { it.tag.isHidden() }
        }

    DraggableScreen(modifier = modifier) {
        AutoScrollLazyColumn(
            modifier = modifier,
            contentPadding = contentPadding,
            state = listState,
            content = {
                items(
                    content,
                    key = keyFn,
                    itemContent = { drawStory ->
                        val size = storyState.stories.size

                        val isOnDragSpace = drawStory.storyStep.localId == "onDragSpace"

                        Box(
                            modifier = Modifier.animateItem(
                                placementSpec = tween(
                                    durationMillis = if (isOnDragSpace) 80 else 100
                                ),
                                fadeInSpec = null,
                                fadeOutSpec = null
                            )
                        ) {
                            drawers[drawStory.storyStep.type.number]?.Step(
                                step = drawStory.storyStep,
                                drawInfo = DrawInfo(
                                    editable = editable,
                                    focus = storyState.focus,
                                    position = drawStory.position,
                                    extraData = drawStory.extraInfo + mapOf("listSize" to size),
                                    selectMode = drawStory.isSelected,
                                    selection = drawStory.cursor ?: Selection.start()
                                )
                            )
                        }
                    }
                )
            }
        )
    }
}
