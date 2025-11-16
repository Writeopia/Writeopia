package io.writeopia.ui.drawer.preview

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.sdk.models.story.StoryTypes
import io.writeopia.ui.drawer.StoryStepDrawer
import io.writeopia.ui.model.DrawInfo
import io.writeopia.ui.utils.Spans
import io.writeopia.ui.utils.previewTextStyle
import org.jetbrains.compose.ui.tooling.preview.Preview

class TextPreviewDrawer(
    private val modifier: Modifier = Modifier.padding(vertical = 5.dp, horizontal = 16.dp),
    private val style: @Composable (StoryStep) -> TextStyle = {
        previewTextStyle(it)
    },
    private val maxLines: Int = Int.MAX_VALUE,
    private val textColor: @Composable (DrawInfo) -> Color = {
        MaterialTheme.colorScheme.onBackground
    },
    private val isDarkTheme: Boolean,
) : StoryStepDrawer {

    @Composable
    override fun Step(step: StoryStep, drawInfo: DrawInfo) {
        val inputText by derivedStateOf {
            Spans.createStringWithSpans(step.text, step.spans, isDarkTheme)
        }

        Text(
            modifier = modifier,
            text = inputText,
            style = style(step),
            color = textColor(drawInfo),
            maxLines = maxLines
        )
    }
}

@Preview
@Composable
fun TextPreviewDrawerPreview() {
    Surface {
        TextPreviewDrawer(isDarkTheme = false).Step(
            step = StoryStep(
                type = StoryTypes.TEXT.type,
                text = "This is a text message preview"
            ),
            drawInfo = DrawInfo(),
        )
    }
}
