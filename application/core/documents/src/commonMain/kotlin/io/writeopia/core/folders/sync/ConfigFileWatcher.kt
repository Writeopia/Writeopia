package io.writeopia.core.folders.sync

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json

/**
 * Platform-agnostic interface for watching the workspace config file for changes.
 * When lastUpdateTable increases (indicating external changes from another app/instance),
 * the configChanges flow emits the new value to trigger automatic sync.
 */
interface ConfigFileWatcher {
    /**
     * Emits new lastUpdateTable value when it increases.
     */
    val configChanges: Flow<Long?>

    /**
     * Start watching the config file in the given workspace path.
     */
    fun startWatching(workspacePath: String)

    /**
     * Stop watching the config file.
     */
    fun stopWatching()

    /**
     * Whether the watcher is currently active.
     */
    val isWatching: Boolean
}

/**
 * Factory function to create platform-specific ConfigFileWatcher implementation.
 * On JVM/Desktop, returns a WatchService-based implementation.
 * On other platforms, returns a no-op implementation.
 */
expect fun createConfigFileWatcher(json: Json): ConfigFileWatcher
