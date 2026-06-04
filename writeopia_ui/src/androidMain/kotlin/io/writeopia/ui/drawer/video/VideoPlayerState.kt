package io.writeopia.ui.drawer.video

import android.content.Context
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer

/**
 * State holder for video player that wraps ExoPlayer.
 * Manages playback state, progress tracking, and lifecycle.
 */
class VideoPlayerState(
    context: Context,
    private val videoUrl: String
) {
    val exoPlayer: ExoPlayer = ExoPlayer.Builder(context).build()

    var isPlaying by mutableStateOf(false)
        private set

    var isBuffering by mutableStateOf(false)
        private set

    var currentPosition by mutableLongStateOf(0L)
        private set

    var duration by mutableLongStateOf(0L)
        private set

    var hasError by mutableStateOf(false)
        private set

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            isBuffering = playbackState == Player.STATE_BUFFERING
            if (playbackState == Player.STATE_READY) {
                duration = exoPlayer.duration.coerceAtLeast(0L)
            }
        }

        override fun onIsPlayingChanged(playing: Boolean) {
            isPlaying = playing
        }

        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            hasError = true
        }
    }

    init {
        exoPlayer.addListener(playerListener)
        val mediaItem = MediaItem.fromUri(videoUrl)
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
    }

    fun play() {
        hasError = false
        exoPlayer.play()
    }

    fun pause() {
        exoPlayer.pause()
    }

    fun togglePlayPause() {
        if (isPlaying) pause() else play()
    }

    fun seekTo(positionMs: Long) {
        exoPlayer.seekTo(positionMs)
        currentPosition = positionMs
    }

    fun updateProgress() {
        currentPosition = exoPlayer.currentPosition.coerceAtLeast(0L)
        if (exoPlayer.duration > 0) {
            duration = exoPlayer.duration
        }
    }

    fun retry() {
        hasError = false
        exoPlayer.prepare()
        exoPlayer.play()
    }

    fun release() {
        exoPlayer.removeListener(playerListener)
        exoPlayer.release()
    }
}

@OptIn(UnstableApi::class)
@Composable
fun rememberVideoPlayerState(videoUrl: String): VideoPlayerState {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val playerState = remember(videoUrl) {
        VideoPlayerState(context, videoUrl)
    }

    DisposableEffect(playerState, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> playerState.pause()
                Lifecycle.Event.ON_STOP -> playerState.pause()
                else -> {}
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            playerState.release()
        }
    }

    return playerState
}
