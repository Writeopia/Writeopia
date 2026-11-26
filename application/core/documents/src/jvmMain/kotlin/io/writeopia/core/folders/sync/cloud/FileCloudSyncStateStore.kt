package io.writeopia.core.folders.sync.cloud

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** [CloudSyncStateStore] in a JSON file of the app's folder. */
class FileCloudSyncStateStore(private val file: File) : CloudSyncStateStore {

    @Serializable
    private data class State(val deviceId: String? = null, val seen: Map<String, String> = emptyMap())

    private val json = Json { ignoreUnknownKeys = true }
    private val lock = Mutex()

    override suspend fun deviceId(): String = lock.withLock {
        val state = read()
        state.deviceId ?: CloudSyncStateStore.newDeviceId().also { id -> write(state.copy(deviceId = id)) }
    }

    override suspend fun seenVersions(): Map<String, String> = lock.withLock { read().seen }

    override suspend fun saveSeenVersions(versions: Map<String, String>) = lock.withLock {
        write(read().copy(seen = versions))
    }

    /**
     * The stored state; a fresh one only when there is no file yet. A file that can't be read or
     * decoded throws: starting over would give this device a new id, and the other devices would
     * take its old files for someone else's.
     */
    private suspend fun read(): State = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext State()
        try {
            json.decodeFromString<State>(file.readText())
        } catch (e: Exception) {
            throw CloudStoreException("The cloud sync state in ${file.path} can't be read: ${e.message}")
        }
    }

    private suspend fun write(state: State): Unit = withContext(Dispatchers.IO) {
        file.parentFile?.mkdirs()
        // Written aside, then moved over in one step: the old file stays whole until the new one
        // replaces it, and a failed move throws instead of losing the device id.
        val temporary = File(file.parentFile, "${file.name}.tmp")
        temporary.writeText(json.encodeToString(State.serializer(), state))
        try {
            Files.move(
                temporary.toPath(),
                file.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
        } catch (e: AtomicMoveNotSupportedException) {
            Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
        Unit
    }
}
