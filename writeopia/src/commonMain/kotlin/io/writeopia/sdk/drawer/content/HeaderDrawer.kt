package io.writeopia.sdk.drawer.content

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import io.writeopia.sdk.drawer.StoryStepDrawer
import io.writeopia.sdk.manager.WriteopiaManager
import io.writeopia.sdk.model.draw.DrawInfo
import io.writeopia.sdk.models.story.StoryStep

/**
 * The header for the Document. It applies some stylish to the title of the document.
 */
class HeaderDrawer(
    private val modifier: Modifier = Modifier,
    private val headerClick: () -> Unit = {},
    private val drawer: BoxScope.() -> StoryStepDrawer,
) : StoryStepDrawer {

    @Composable
    override fun Step(step: StoryStep, drawInfo: DrawInfo) {
        val backgroundColor = step.decoration.backgroundColor

        Box(
            modifier = modifier
                .clickable(onClick = headerClick)
                .let { modifierLet ->
                    if (backgroundColor != null) {
                        modifierLet
                            .background(Color(backgroundColor))
                            .padding(top = 130.dp)
                    } else {
                        modifierLet.padding(top = 40.dp)
                    }
                }
                .fillMaxWidth()
        ) {
            drawer().Step(step = step, drawInfo = drawInfo)
        }
    }
}

fun headerDrawer(
    manager: WriteopiaManager,
    headerClick: () -> Unit = {},
    modifier: Modifier = Modifier
): StoryStepDrawer =
    HeaderDrawer(
        drawer = {
            MobileTitleDrawer(
                modifier = modifier.align(Alignment.BottomStart),
                onTextEdit = manager::changeStoryState,
                onLineBreak = manager::onLineBreak,
            )
        },
        headerClick = headerClick
    )

fun headerDrawerDesktop(
    manager: WriteopiaManager,
    headerClick: () -> Unit,
    onKeyEvent: (KeyEvent, TextFieldValue, StoryStep, Int) -> Boolean,
    modifier: Modifier = Modifier,
    innerPadding: PaddingValues? = null
): StoryStepDrawer =
    HeaderDrawer(
        drawer = {
            DesktopTitleDrawer(
                modifier = modifier.align(Alignment.BottomStart),
                innerPadding = innerPadding,
                onTextEdit = manager::changeStoryState,
                onKeyEvent = onKeyEvent
            )
        },
        headerClick = headerClick
    )
