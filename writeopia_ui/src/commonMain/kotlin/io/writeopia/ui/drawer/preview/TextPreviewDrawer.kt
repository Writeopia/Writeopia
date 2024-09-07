package io.writeopia.ui.drawer.preview

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.writeopia.sdk.model.draw.DrawInfo
import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.sdk.models.story.StoryTypes
import io.writeopia.ui.drawer.StoryStepDrawer
import org.jetbrains.compose.ui.tooling.preview.Preview

class TextPreviewDrawer(
    private val modifier: Modifier = Modifier.padding(vertical = 5.dp, horizontal = 16.dp),
    private val style: @Composable () -> TextStyle = {
        MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp)
    },
    private val maxLines: Int = Int.MAX_VALUE
) : StoryStepDrawer {

    @Composable
    override fun Step(step: StoryStep, drawInfo: DrawInfo) {
        val textColor = if (drawInfo.selectMode) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onBackground
        }

        Text(
            modifier = modifier,
            text = step.text ?: "",
            style = style(),
            color = textColor,
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
