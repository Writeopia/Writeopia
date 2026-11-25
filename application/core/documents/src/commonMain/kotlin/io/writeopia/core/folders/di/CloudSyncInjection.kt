package io.writeopia.core.folders.di

import io.ktor.client.HttpClient
import io.writeopia.auth.core.di.AuthCoreInjectionNeo
import io.writeopia.core.folders.sync.cloud.CloudSyncManager
import io.writeopia.core.folders.sync.cloud.CloudWorkspaceSync
import io.writeopia.core.folders.sync.cloud.GoogleDriveFileStore
import io.writeopia.core.folders.sync.cloud.InMemoryCloudSyncStateStore
import io.writeopia.core.folders.sync.cloud.PlatformCloudSync
import io.writeopia.core.folders.sync.cloud.platformCloudSync
import io.writeopia.sdk.models.workspace.Workspace
import io.writeopia.sdk.persistence.core.di.RepositoryInjector

/** The sync of the offline workspace with the user's own cloud storage. One per app. */
class CloudSyncInjection private constructor(
    private val platform: PlatformCloudSync?,
    private val authCoreInjection: AuthCoreInjectionNeo = AuthCoreInjectionNeo.singleton(),
) {
    private val cloudSync by lazy {
        val platform = requireNotNull(platform)
        CloudWorkspaceSync(
            documentRepository = RepositoryInjector.singleton().provideDocumentRepository(),
            folderRepository = FoldersInjector.singleton().provideFoldersRepository(),
            // Its own client: Drive takes Google's token, never the Writeopia one.
            store = GoogleDriveFileStore(HttpClient(), platform.connector::accessToken),
            state = platform.stateStore,
        )
    }

    val manager: CloudSyncManager by lazy {
        CloudSyncManager(
            connector = platform?.connector,
            cloudSync = { cloudSync },
            stateStore = platform?.stateStore ?: InMemoryCloudSyncStateStore(),
            currentWorkspaceId = {
                authCoreInjection.provideAuthRepository().getWorkspace()?.id
                    ?: Workspace.disconnectedWorkspace().id
            },
        )
    }

    companion object {
        private var instance: CloudSyncInjection? = null

        fun singleton(): CloudSyncInjection =
            instance ?: CloudSyncInjection(platformCloudSync()).also { instance = it }
    }
}
