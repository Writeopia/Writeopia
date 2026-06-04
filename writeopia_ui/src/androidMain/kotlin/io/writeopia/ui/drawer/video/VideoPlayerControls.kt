package io.writeopia.ui.drawer.video

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Bottom control bar for video player with play/pause, progress bar, and timestamps.
 */
@Composable
fun VideoPlayerControls(
    playerState: VideoPlayerState,
    modifier: Modifier = Modifier
) {
    var isDragging by remember { mutableStateOf(false) }
    var dragPosition by remember { mutableFloatStateOf(0f) }

    val progress = if (playerState.duration > 0) {
        if (isDragging) {
            dragPosition
        } else {
            playerState.currentPosition.toFloat() / playerState.duration.toFloat()
        }
    } else {
        0f
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.6f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Play/Pause button
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .clickable { playerState.togglePlayPause() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (playerState.isPlaying) {
                    Icons.Filled.Pause
                } else {
                    Icons.Filled.PlayArrow
                },
                contentDescription = if (playerState.isPlaying) "Pause" else "Play",
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
        }

        // Current time
        Text(
            text = formatDuration(playerState.currentPosition),
            color = Color.White,
            fontSize = 12.sp
        )

        // Progress slider
        Slider(
            value = progress,
            onValueChange = { newValue ->
                isDragging = true
                dragPosition = newValue
            },
            onValueChangeFinished = {
                val seekPosition = (dragPosition * playerState.duration).toLong()
                playerState.seekTo(seekPosition)
                isDragging = false
            },
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color.White,
                inactiveTrackColor = Color.White.copy(alpha = 0.3f)
            )
        )

        // Total duration
        Text(
            text = formatDuration(playerState.duration),
            color = Color.White,
            fontSize = 12.sp
        )
    }
}

/**
 * Formats milliseconds to MM:SS or HH:MM:SS format.
 */
private fun formatDuration(millis: Long): String {
    val totalSeconds = millis / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60

    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%d:%02d", minutes, seconds)
    }
}
