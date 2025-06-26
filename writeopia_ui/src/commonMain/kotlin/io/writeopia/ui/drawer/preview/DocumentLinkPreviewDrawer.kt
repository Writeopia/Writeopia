package io.writeopia.ui.drawer.preview

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.ui.drawer.StoryStepDrawer
import io.writeopia.ui.model.DrawInfo
import io.writeopia.ui.utils.previewTextStyle

class DocumentLinkPreviewDrawer(
    private val modifier: Modifier = Modifier.padding(vertical = 5.dp, horizontal = 16.dp),
    private val style: @Composable (StoryStep) -> TextStyle = {
        previewTextStyle(it).copy(textDecoration = TextDecoration.Underline)
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
            text = step.documentLink?.title ?: "[Document Link]",
            style = style(step),
            color = textColor(drawInfo),
            maxLines = maxLines
        )
    }
}
