package io.writeopia.sdk.drawer.content

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.video.videoFrameMillis
import io.writeopia.ui.model.DrawInfo
import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.ui.drawer.StoryStepDrawer
import io.writeopia.ui.drawer.video.VideoPlayer
import io.writeopia.ui.drawer.video.rememberVideoPlayerState

/**
 * Draws a video with thumbnail preview and full playback support.
 *
 * Two modes:
 * - Thumbnail mode: Shows video frame at 1s with play button overlay
 * - Playback mode: Full video player with ExoPlayer and controls
 */
class VideoDrawer(private val containerModifier: Modifier? = null) : StoryStepDrawer {

    @Composable
    override fun Step(step: StoryStep, drawInfo: DrawInfo) {
        val videoUrl = step.url

        // Return early if no URL
        if (videoUrl.isNullOrBlank()) return

        var isPlaybackMode by remember { mutableStateOf(false) }

        Box(modifier = Modifier.padding(vertical = 3.dp, horizontal = 8.dp)) {
            if (isPlaybackMode) {
                PlaybackMode(
                    videoUrl = videoUrl,
                    containerModifier = containerModifier,
                    onDismiss = { isPlaybackMode = false }
                )
            } else {
                ThumbnailMode(
                    videoUrl = videoUrl,
                    containerModifier = containerModifier,
                    onClick = { isPlaybackMode = true }
                )
            }
        }
    }
}

@Composable
private fun ThumbnailMode(
    videoUrl: String,
    containerModifier: Modifier?,
    onClick: () -> Unit
) {
    Box(
        modifier = (containerModifier ?: Modifier
            .clip(shape = RoundedCornerShape(size = 12.dp))
            .border(
                width = 1.dp,
                color = Color.Gray,
                shape = RoundedCornerShape(size = 12.dp)
            ))
            .clickable { onClick() }
    ) {
        val request = ImageRequest.Builder(LocalContext.current)
            .data(videoUrl)
            .videoFrameMillis(1000)
            .build()

        AsyncImage(
            model = request,
            contentDescription = "Video thumbnail",
            modifier = Modifier
        )

        // Large centered play button
        Box(
            modifier = Modifier
                .matchParentSize(),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = "Play video",
                    tint = Color.White,
                    modifier = Modifier.size(40.dp)
                )
            }
        }

        // Movie icon badge in top-right corner
        Box(
            modifier = Modifier
                .padding(8.dp)
                .align(alignment = Alignment.TopEnd)
        ) {
            Box(
                modifier = Modifier
                    .clip(shape = CircleShape)
                    .background(Color(0xFFF2994A))
                    .padding(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Movie,
                    contentDescription = "Video",
                    tint = Color.White
                )
            }
        }
    }
}

@Composable
private fun PlaybackMode(
    videoUrl: String,
    containerModifier: Modifier?,
    onDismiss: () -> Unit
) {
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
            modifier = Modifier.matchParentSize(),
            onDismiss = onDismiss
        )
    }
}
