package io.writeopia.ui.drawer.video

import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay

/**
 * Composable that renders a video player using ExoPlayer.
 * Shows loading indicator during buffering and play button when paused.
 */
@OptIn(UnstableApi::class)
@Composable
fun VideoPlayer(
    playerState: VideoPlayerState,
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit = {}
) {
    var showControls by remember { mutableStateOf(true) }
    val interactionSource = remember { MutableInteractionSource() }

    // Update progress periodically
    LaunchedEffect(playerState.isPlaying) {
        while (playerState.isPlaying) {
            playerState.updateProgress()
            delay(500)
        }
    }

    // Auto-hide controls after 3 seconds of playback
    LaunchedEffect(playerState.isPlaying, showControls) {
        if (playerState.isPlaying && showControls) {
            delay(3000)
            showControls = false
        }
    }

    Box(
        modifier = modifier
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) {
                showControls = !showControls
            }
    ) {
        // ExoPlayer view
        AndroidView(
            factory = { context ->
                PlayerView(context).apply {
                    player = playerState.exoPlayer
                    useController = false
                }
            },
            update = { playerView ->
                playerView.player = playerState.exoPlayer
            },
            modifier = Modifier.fillMaxSize()
        )

        // Error state
        if (playerState.hasError) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.foundation.layout.Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Failed to load video",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Box(
                        modifier = Modifier
                            .padding(top = 16.dp)
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.9f))
                            .clickable { playerState.retry() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "Retry",
                            tint = Color.Black,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }
        }

        // Buffering indicator
        if (playerState.isBuffering && !playerState.hasError) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = Color.White,
                    modifier = Modifier.size(48.dp)
                )
            }
        }

        // Large centered play button when paused (and not buffering/error)
        if (!playerState.isPlaying && !playerState.isBuffering && !playerState.hasError) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.6f))
                        .clickable { playerState.play() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = "Play",
                        tint = Color.White,
                        modifier = Modifier.size(48.dp)
                    )
                }
            }
        }

        // Video player controls (bottom bar)
        if (showControls && !playerState.hasError) {
            VideoPlayerControls(
                playerState = playerState,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}
