package io.writeopia.ui.drawer.video

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Composable that renders a video placeholder with option to open in system player.
 */
@Composable
fun VideoPlayer(
    playerState: VideoPlayerState,
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit = {}
) {
    Box(
        modifier = modifier
            .background(
                color = Color(0xFF1a1a2e),
                shape = RoundedCornerShape(8.dp)
            )
            .clickable { playerState.openInSystemPlayer() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(16.dp)
        ) {
            // Video icon
            Icon(
                imageVector = Icons.Filled.VideoFile,
                contentDescription = "Video",
                tint = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(48.dp)
            )

            // Play button
            Box(
                modifier = Modifier
                    .padding(top = 16.dp)
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.9f))
                    .clickable { playerState.openInSystemPlayer() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = "Play in system player",
                    tint = Color(0xFF1a1a2e),
                    modifier = Modifier.size(40.dp)
                )
            }

            // Label
            Row(
                modifier = Modifier.padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Stream in video player",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier
                        .padding(start = 4.dp)
                        .size(14.dp)
                )
            }

            // Video URL hint (truncated)
            val displayUrl = playerState.videoUrl
                .removePrefix("https://")
                .removePrefix("http://")
                .take(40)
                .let { if (playerState.videoUrl.length > 48) "$it..." else it }

            Text(
                text = displayUrl,
                color = Color.White.copy(alpha = 0.4f),
                fontSize = 10.sp,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}
