package io.writeopia.core.folders.sync.cloud

import io.ktor.client.HttpClient
import java.io.File

/**
 * Google Drive, when the build has the OAuth client of the desktop app (`local.properties` or the
 * environment, see the build file); without it there is no cloud sync to offer.
 */
actual fun platformCloudSync(): PlatformCloudSync? {
    val clientId = GoogleDriveDesktopConfig.CLIENT_ID
    if (clientId.isBlank()) return null

    val appDirectory = File(System.getProperty("user.home"), ".writeopia")
    return PlatformCloudSync(
        connector = GoogleDesktopConnector(
            clientId = clientId,
            clientSecret = GoogleDriveDesktopConfig.CLIENT_SECRET,
            httpClient = HttpClient(),
        ),
        stateStore = FileCloudSyncStateStore(File(appDirectory, "cloud_sync_state.json")),
    )
}
