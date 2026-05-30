package io.writeopia.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.writeopia.ui.model.DrawInfo
import io.writeopia.ui.model.DrawState
import io.writeopia.ui.draganddrop.target.DraggableScreen
import io.writeopia.ui.drawer.StoryStepDrawer

@Composable
fun WriteopiaEditorBox(
    modifier: Modifier = Modifier,
    editable: Boolean = true,
    drawers: Map<Int, StoryStepDrawer>,
    storyState: DrawState = DrawState(emptyList())
) {
    DraggableScreen(modifier = modifier) {
        Column(
            modifier = modifier,
            content = {
                storyState.stories.forEachIndexed { _, drawStory ->
                    drawers[drawStory.storyStep.type.number]?.Step(
                        step = drawStory.storyStep,
                        drawInfo = DrawInfo(
                            editable = editable,
                            focus = storyState.focus,
                            position = drawStory.position,
                            previousPosition = drawStory.storyStep.previousPosition,
                            nextPosition = drawStory.storyStep.nextPosition,
                            extraData = mapOf("listSize" to storyState.stories.size),
                            selectMode = drawStory.isSelected,
                            selection = drawStory.cursor
                        )
                    )
                }
            }
        )
    }
}
