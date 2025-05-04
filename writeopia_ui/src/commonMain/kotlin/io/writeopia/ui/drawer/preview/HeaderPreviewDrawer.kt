package io.writeopia.ui.drawer.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.writeopia.ui.model.DrawInfo
import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.sdk.models.story.StoryTypes
import io.writeopia.ui.drawer.StoryStepDrawer
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * The drawer to the header of a document in the preview of a note.
 */
class HeaderPreviewDrawer(
    private val modifier: Modifier = Modifier,
    private val style: TextStyle? = null
) : StoryStepDrawer {

    @Composable
    override fun Step(step: StoryStep, drawInfo: DrawInfo) {
        val textColor = MaterialTheme.colorScheme.onBackground

        Box(
            modifier = modifier
                .padding(bottom = 16.dp)
                .fillMaxWidth()
                .defaultMinSize(minHeight = if (hasContent(step, drawInfo)) 70.dp else 20.dp)
                .let { modifierLet ->
                    val backgroundColor = step.decoration.backgroundColor
                    if (backgroundColor != null) {
                        modifierLet.background(Color(backgroundColor))
                    } else {
                        modifierLet
                    }
                }
        ) {
            if (hasContent(step, drawInfo)) {
                Row(
                    modifier = Modifier
                        .padding(vertical = 12.dp)
                        .align(Alignment.BottomStart),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val extraData = drawInfo.extraData

                    if (extraData.containsKey("imageVector")) {
                        val imageVector = extraData["imageVector"] as ImageVector

                        val tint = if (extraData.containsKey("imageVectorTint")) {
                            val tintColor = extraData["imageVectorTint"] as Int
                            Color(tintColor)
                        } else {
                            MaterialTheme.colorScheme.onBackground
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Icon(
                            imageVector = imageVector,
                            "header icon",
                            tint = tint,
                            modifier = Modifier.size(20.dp)
                        )

                        Spacer(modifier = Modifier.width(8.dp))
                    } else {
                        Spacer(modifier = Modifier.width(12.dp))
                    }

                    Text(
                        text = step.text ?: "",
                        style = style ?: MaterialTheme.typography.titleLarge,
                        color = textColor,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(end = 12.dp)
                    )
                }
            }
        }
    }

    private fun hasContent(step: StoryStep, drawInfo: DrawInfo): Boolean =
        step.text?.isNotEmpty() == true || drawInfo.extraData.containsKey("imageVector")
}

@Preview
@Composable
fun HeaderPreviewDrawerPreview() {
    HeaderPreviewDrawer().Step(
        step = StoryStep(
            type = StoryTypes.TITLE.type,
            text = "Some title"
        ),
        drawInfo = DrawInfo()
    )
}
