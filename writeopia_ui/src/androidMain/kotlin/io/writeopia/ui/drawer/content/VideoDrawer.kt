package io.writeopia.sdk.drawer.content

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.writeopia.ui.model.DrawInfo
import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.ui.drawer.StoryStepDrawer
import io.writeopia.ui.drawer.video.VideoPlayer
import io.writeopia.ui.drawer.video.rememberVideoPlayerState

/**
 * Draws a video player using ExoPlayer/Media3.
 */
class VideoDrawer(private val containerModifier: Modifier? = null) : StoryStepDrawer {

    @Composable
    override fun Step(step: StoryStep, drawInfo: DrawInfo) {
        val videoUrl = step.url

        // Return early if no URL
        if (videoUrl.isNullOrBlank()) return

        Box(modifier = Modifier.padding(vertical = 3.dp, horizontal = 8.dp)) {
            val playerState = rememberVideoPlayerState(videoUrl)

            Box(
                modifier = (containerModifier ?: Modifier
                    .fillMaxWidth()
                    .clip(shape = RoundedCornerShape(size = 12.dp))
                    .border(
                        width = 1.dp,
                        color = Color.Gray,
                        shape = RoundedCornerShape(size = 12.dp)
                    ))
                    .aspectRatio(16f / 9f)
            ) {
                VideoPlayer(
                    playerState = playerState,
                    modifier = Modifier.matchParentSize()
                )
            }
        }
    }
}
