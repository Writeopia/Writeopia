package io.writeopia.core.folders.di

import io.writeopia.auth.core.di.AuthCoreInjectionNeo
import io.writeopia.core.folders.api.DocumentsApi
import io.writeopia.core.folders.sync.DocumentConflictHandler
import io.writeopia.core.folders.sync.WorkspaceSync
import io.writeopia.di.AppConnectionInjection
import io.writeopia.sdk.network.injector.WriteopiaConnectionInjector
import io.writeopia.sdk.persistence.core.di.RepositoryInjector
import io.writeopia.sqldelight.di.SqlDelightDaoInjector

class WorkspaceInjection private constructor(
    private val authCoreInjection: AuthCoreInjectionNeo = AuthCoreInjectionNeo.singleton(),
    // Mudar o RepositoryInjector para expect/actual ao invés de interface
    private val repositoryInjection: RepositoryInjector = SqlDelightDaoInjector.singleton(),
    private val appConnectionInjection: AppConnectionInjection = AppConnectionInjection.singleton(),
    private val connectionInjector: WriteopiaConnectionInjector =
        WriteopiaConnectionInjector.singleton(),
) {

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
        )
    }

    companion object {
        private var instance: WorkspaceInjection? = null

        fun singleton() = instance ?: WorkspaceInjection().also {
            instance = it
        }
    }
}
