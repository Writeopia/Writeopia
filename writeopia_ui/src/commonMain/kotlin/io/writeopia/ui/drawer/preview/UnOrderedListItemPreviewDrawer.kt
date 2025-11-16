package io.writeopia.ui.drawer.preview

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.sdk.models.story.StoryTypes
import io.writeopia.ui.drawer.StoryStepDrawer
import io.writeopia.ui.model.DrawInfo
import io.writeopia.ui.utils.previewTextStyle
import org.jetbrains.compose.ui.tooling.preview.Preview

class UnOrderedListItemPreviewDrawer(
    private val modifier: Modifier = Modifier.padding(vertical = 5.dp, horizontal = 16.dp),
    private val textModifier: Modifier = Modifier,
    private val startText: String = "-",
    private val textStyle: @Composable (StoryStep) -> TextStyle = {
        previewTextStyle(it)
    },
    private val maxLines: Int = 1,
    private val isDarkTheme: Boolean,
) : StoryStepDrawer {
    @Composable
    override fun Step(step: StoryStep, drawInfo: DrawInfo) {
        val textColor = MaterialTheme.colorScheme.onBackground

        Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
            Text(
                modifier = Modifier,
                text = startText,
                style = textStyle(step),
                color = textColor
            )

            TextPreviewDrawer(
                modifier = textModifier,
                style = textStyle,
                maxLines = maxLines,
                isDarkTheme = isDarkTheme
            ).Step(
                step = step,
                drawInfo = drawInfo
            )
        }
    }
}

@Preview
@Composable
fun UnOrderedListItemPreviewDrawerPreview() {
    Surface {
        UnOrderedListItemPreviewDrawer(isDarkTheme = true).Step(
            step = StoryStep(
                type = StoryTypes.TEXT.type,
                text = "This is a text list item preview"
            ),
            drawInfo = DrawInfo()
        )
    }
}
