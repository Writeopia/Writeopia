package io.writeopia.core.folders.sync

import io.writeopia.sdk.serialization.storage.WorkspaceStorageConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.nio.file.ClosedWatchServiceException
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardWatchEventKinds
import java.nio.file.WatchService
import java.util.concurrent.TimeUnit

actual fun createConfigFileWatcher(json: Json): ConfigFileWatcher =
    JvmConfigFileWatcher(json)

/**
 * JVM implementation of ConfigFileWatcher using Java NIO WatchService.
 * Watches for changes to writeopia_config_file.json and emits when lastUpdateTable increases.
 *
 * Note: On macOS, WatchService uses polling internally with ~10 second latency.
 */
class JvmConfigFileWatcher(
    private val json: Json
) : ConfigFileWatcher {

    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var watchJob: Job? = null
    private var watchService: WatchService? = null
    private var lastKnownTimestamp: Long? = null

    private val _configChanges = MutableSharedFlow<Long?>(replay = 0)
    override val configChanges: Flow<Long?> = _configChanges.asSharedFlow()

    override val isWatching: Boolean
        get() = watchJob?.isActive == true

    override fun startWatching(workspacePath: String) {
        if (workspacePath.isBlank()) return

        stopWatching()

        val path = Paths.get(workspacePath)
        if (!Files.isDirectory(path)) return

        watchService = FileSystems.getDefault().newWatchService()
        path.register(watchService, StandardWatchEventKinds.ENTRY_MODIFY)

        // Initialize with current value
        lastKnownTimestamp = readLastUpdateTable(workspacePath)

        watchJob = coroutineScope.launch {
            while (isActive) {
                // poll() with timeout to allow cancellation checks. The service may be closed by
                // stopWatching() while poll() blocks: that ends the loop instead of crashing the
                // coroutine (an uncaught exception that tests pick up).
                val key = try {
                    watchService?.poll(2, TimeUnit.SECONDS)
                } catch (e: ClosedWatchServiceException) {
                    break
                }

                if (key != null) {
                    for (event in key.pollEvents()) {
                        val changedFile = event.context() as? Path
                        if (changedFile?.toString() == CONFIG_FILE_NAME) {
                            val newTimestamp = readLastUpdateTable(workspacePath)

                            if (newTimestamp != null &&
                                lastKnownTimestamp != null &&
                                newTimestamp > lastKnownTimestamp!!
                            ) {
                                _configChanges.emit(newTimestamp)
                            }

                            if (newTimestamp != null) {
                                lastKnownTimestamp = newTimestamp
                            }
                        }
                    }
                    key.reset()
                }
            }
        }
    }

    override fun stopWatching() {
        watchJob?.cancel()
        watchJob = null
        try {
            watchService?.close()
        } catch (e: Exception) {
            // Ignore close exceptions
        }
        watchService = null
    }

    private fun readLastUpdateTable(workspacePath: String): Long? {
        val configFile = File("$workspacePath/$CONFIG_FILE_NAME")
        return try {
            if (configFile.exists()) {
                val content = configFile.readText()
                json.decodeFromString<WorkspaceStorageConfig>(content).lastUpdateTable
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        private const val CONFIG_FILE_NAME = "writeopia_config_file.json"
    }
}
