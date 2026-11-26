package io.writeopia.core.folders.sync.cloud

/** What a platform brings to the cloud sync: the account sign-in and where it keeps its state. */
class PlatformCloudSync(
    val connector: CloudAccountConnector,
    val stateStore: CloudSyncStateStore,
)

/** Null where the offline workspace can't sync with the user's cloud storage (yet). */
expect fun platformCloudSync(): PlatformCloudSync?
