package io.writeopia.core.folders.sync

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.serialization.json.Json

actual fun createConfigFileWatcher(json: Json): ConfigFileWatcher =
    NoOpConfigFileWatcher()

/**
 * Android no-op implementation of ConfigFileWatcher.
 * File watching is only supported on JVM/Desktop.
 */
class NoOpConfigFileWatcher : ConfigFileWatcher {

    override val configChanges: Flow<Long?> = emptyFlow()

    override val isWatching: Boolean = false

    override fun startWatching(workspacePath: String) {
        // No-op on Android
    }

    override fun stopWatching() {
        // No-op on Android
    }
}
