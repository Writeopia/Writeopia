package io.writeopia.ui.drawer.video

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.io.File

/**
 * State holder for video player on desktop.
 * Uses system default video player for streaming playback.
 */
class VideoPlayerState(
    val videoUrl: String
) {
    fun openInSystemPlayer() {
        try {
            val os = System.getProperty("os.name").lowercase()

            when {
                os.contains("mac") -> {
                    // On macOS, use 'open' command which will open in default video player
                    // Adding -a flag to try common video players that support streaming
                    val players = listOf(
                        "IINA",           // Popular macOS video player with streaming support
                        "VLC",            // VLC media player
                        "QuickTime Player" // Default macOS player
                    )

                    var opened = false
                    for (player in players) {
                        try {
                            val process = ProcessBuilder("open", "-a", player, videoUrl)
                                .redirectErrorStream(true)
                                .start()
                            val exitCode = process.waitFor()
                            if (exitCode == 0) {
                                opened = true
                                break
                            }
                        } catch (e: Exception) {
                            // Try next player
                        }
                    }

                    // Fallback: just use 'open' which will use system default
                    if (!opened) {
                        ProcessBuilder("open", videoUrl).start()
                    }
                }

                os.contains("win") -> {
                    // On Windows, try VLC first, then default
                    val vlcPaths = listOf(
                        "C:\\Program Files\\VideoLAN\\VLC\\vlc.exe",
                        "C:\\Program Files (x86)\\VideoLAN\\VLC\\vlc.exe"
                    )

                    var opened = false
                    for (vlcPath in vlcPaths) {
                        if (File(vlcPath).exists()) {
                            try {
                                ProcessBuilder(vlcPath, videoUrl).start()
                                opened = true
                                break
                            } catch (e: Exception) {
                                // Try next
                            }
                        }
                    }

                    // Fallback: use start command
                    if (!opened) {
                        ProcessBuilder("cmd", "/c", "start", "", videoUrl).start()
                    }
                }

                os.contains("linux") -> {
                    // On Linux, try VLC first, then xdg-open
                    var opened = false

                    // Try VLC
                    try {
                        val process = ProcessBuilder("which", "vlc")
                            .redirectErrorStream(true)
                            .start()
                        if (process.waitFor() == 0) {
                            ProcessBuilder("vlc", videoUrl).start()
                            opened = true
                        }
                    } catch (e: Exception) {
                        // VLC not found
                    }

                    // Try mpv
                    if (!opened) {
                        try {
                            val process = ProcessBuilder("which", "mpv")
                                .redirectErrorStream(true)
                                .start()
                            if (process.waitFor() == 0) {
                                ProcessBuilder("mpv", videoUrl).start()
                                opened = true
                            }
                        } catch (e: Exception) {
                            // mpv not found
                        }
                    }

                    // Fallback: xdg-open
                    if (!opened) {
                        ProcessBuilder("xdg-open", videoUrl).start()
                    }
                }

                else -> {
                    // Unknown OS, try xdg-open as fallback
                    ProcessBuilder("xdg-open", videoUrl).start()
                }
            }
        } catch (e: Exception) {
            println("Failed to open video in system player: ${e.message}")
            e.printStackTrace()
        }
    }
}

@Composable
fun rememberVideoPlayerState(videoUrl: String): VideoPlayerState = remember(videoUrl) {
    VideoPlayerState(videoUrl)
}
