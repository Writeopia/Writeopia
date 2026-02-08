package io.writeopia.ui.drawer.content

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.writeopia.sdk.model.action.Action
import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.ui.draganddrop.target.DropTarget
import io.writeopia.ui.drawer.StoryStepDrawer
import io.writeopia.ui.drawer.decorations.DefaultTagDecoration
import io.writeopia.ui.drawer.decorations.TagDecoration
import io.writeopia.ui.model.DrawConfig
import io.writeopia.ui.model.DrawInfo
import io.writeopia.ui.modifiers.StepsModifier

/**
 * Draws a white space. This Drawer is very important for accepting drop os other Composables for
 * reorder purposes. A space create a move request when dropping Composables in it while the other
 * story units create a mergeRequest.
 */
class SpaceDrawer(
    private val config: DrawConfig,
    private val moveRequest: (Action.Move) -> Unit = {},
    private val backgroundColor: Color = Color.Transparent,
    private val tagDecoration: TagDecoration = DefaultTagDecoration,
    private val modifier: Modifier = Modifier,
) : StoryStepDrawer {

    @Composable
    override fun Step(step: StoryStep, drawInfo: DrawInfo) {
        val isInsideCodeBlock = drawInfo.extraData[StepsModifier.IS_INSIDE_CODE_BLOCK_KEY] == true

        DropTarget(
            modifier = Modifier
        ) { inBound, data ->
            if (inBound && data != null) {
                moveRequest(
                    Action.Move(
                        data.info as StoryStep,
                        positionFrom = data.positionFrom,
                        positionTo = drawInfo.position
                    )
                )
            }

            val bgColor = if (isInsideCodeBlock) {
                config.codeBlockBackgroundColor()
            } else {
                tagDecoration.background(
                    Color.Transparent,
                    step.tags.map { it.tag },
                    config
                )
            }

            Box(
                modifier = modifier
                    .background(bgColor)
                    .height(10.dp)
                    .fillMaxWidth()
                    .padding(top = 3.dp, bottom = 3.dp, start = 12.dp)
                    .background(backgroundColor, MaterialTheme.shapes.medium)
            )
        }
    }
}
