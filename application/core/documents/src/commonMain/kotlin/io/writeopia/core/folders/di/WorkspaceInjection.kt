package io.writeopia.core.folders.di

import io.writeopia.auth.core.data.WorkspaceApi
import io.writeopia.auth.core.di.AuthCoreInjectionNeo
import io.writeopia.auth.core.manager.WorkspaceHandler
import io.writeopia.core.configuration.di.AppConfigurationInjector
import io.writeopia.core.folders.api.DocumentsApi
import io.writeopia.core.folders.sync.DocumentConflictHandler
import io.writeopia.core.folders.sync.ImageSync
import io.writeopia.core.folders.sync.WorkspaceSync
import io.writeopia.di.AppConnectionInjection
import io.writeopia.sdk.network.injector.WriteopiaConnectionInjector
import io.writeopia.sdk.persistence.core.di.RepositoryInjector

class WorkspaceInjection private constructor(
    private val authCoreInjection: AuthCoreInjectionNeo = AuthCoreInjectionNeo.singleton(),
    private val appConnectionInjection: AppConnectionInjection = AppConnectionInjection.singleton(),
    private val connectionInjector: WriteopiaConnectionInjector =
        WriteopiaConnectionInjector.singleton(),
    private val repositoryInjection: RepositoryInjector = RepositoryInjector.singleton(),
    private val appConfigurationInjector: AppConfigurationInjector =
        AppConfigurationInjector.singleton(),
) {

    private fun provideWorkspaceApi() =
        WorkspaceApi(appConnectionInjection.provideHttpClient(), connectionInjector.baseUrl())

    fun provideWorkspaceHandler(): WorkspaceHandler =
        WorkspaceHandlerImpl(
            authRepository = authCoreInjection.provideAuthRepository(),
            workspaceApi = provideWorkspaceApi(),
            workspaceSync = provideWorkspaceSync(),
            workspaceConfigRepository = appConfigurationInjector.provideNotesConfigurationRepository()
        )

    fun provideWorkspaceSync(): WorkspaceSync {
        val documentRepo = repositoryInjection.provideDocumentRepository()
        return WorkspaceSync(
            folderRepository = FoldersInjector.singleton().provideFoldersRepository(),
            documentRepository = documentRepo,
            authRepository = authCoreInjection.provideAuthRepository(),
            documentsApi = DocumentsApi(
                appConnectionInjection.provideHttpClient(),
                connectionInjector.baseUrl()
            ),
            documentConflictHandler = DocumentConflictHandler(
                documentRepository = documentRepo,
                folderRepository = FoldersInjector.singleton().provideFoldersRepository(),
                authCoreInjection.provideAuthRepository()
            ),
            imageSync = ImageSync(
                appConnectionInjection.provideHttpClient(),
                connectionInjector.baseUrl(),
                repositoryInjection.provideDocumentRepository()
            )
        )
    }

    companion object {
        private var instance: WorkspaceInjection? = null

        fun singleton() = instance ?: WorkspaceInjection().also {
            instance = it
        }
    }
}
