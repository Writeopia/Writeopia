package io.writeopia.ui.drawer.preview

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.sdk.models.story.StoryTypes
import io.writeopia.ui.drawer.StoryStepDrawer
import io.writeopia.ui.model.DrawInfo
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
    }
) : StoryStepDrawer {

    @Composable
    override fun Step(step: StoryStep, drawInfo: DrawInfo) {
        Text(
            modifier = modifier,
            text = step.text ?: "",
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
        TextPreviewDrawer().Step(
            step = StoryStep(
                type = StoryTypes.TEXT.type,
                text = "This is a text message preview"
            ),
            drawInfo = DrawInfo()
        )
    }
}
