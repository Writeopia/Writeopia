package io.writeopia.core.folders.sync.cloud

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

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

    private suspend fun read(): State = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext State()
        runCatching { json.decodeFromString<State>(file.readText()) }.getOrElse { State() }
    }

    private suspend fun write(state: State) = withContext(Dispatchers.IO) {
        file.parentFile?.mkdirs()
        // Written aside, then moved over, so a crash never leaves half a file.
        val temporary = File(file.parentFile, "${file.name}.tmp")
        temporary.writeText(json.encodeToString(State.serializer(), state))
        if (!temporary.renameTo(file)) {
            file.delete()
            temporary.renameTo(file)
        }
    }
}
