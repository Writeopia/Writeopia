package io.writeopia.core.folders.sync.cloud

import io.writeopia.sdk.models.id.GenerateId

/**
 * What this device remembers between cloud syncs: its own id, which names its files, and the
 * version of each file of the other devices last taken in, so an unchanged file is not
 * downloaded again.
 */
interface CloudSyncStateStore {

    /** Made on first use and kept for good. Never holds a dot. */
    suspend fun deviceId(): String

    /** File id to version. */
    suspend fun seenVersions(): Map<String, String>

    suspend fun saveSeenVersions(versions: Map<String, String>)

    /** Forgets everything but the device id, so the next sync takes in every file again. */
    suspend fun clearSeenVersions() = saveSeenVersions(emptyMap())

    companion object {
        fun newDeviceId(): String = GenerateId.generate().replace(".", "")
    }
}

class InMemoryCloudSyncStateStore(private var deviceId: String? = null) : CloudSyncStateStore {
    private var seen: Map<String, String> = emptyMap()

    override suspend fun deviceId(): String =
        deviceId ?: CloudSyncStateStore.newDeviceId().also { id -> deviceId = id }

    override suspend fun seenVersions(): Map<String, String> = seen

    override suspend fun saveSeenVersions(versions: Map<String, String>) {
        seen = versions
    }
}
