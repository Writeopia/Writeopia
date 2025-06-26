package io.writeopia.documents.graph.di

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import io.writeopia.auth.core.di.AuthCoreInjectionNeo
import io.writeopia.core.folders.di.FoldersInjector
import io.writeopia.documents.graph.repository.GraphRepository
import io.writeopia.documents.graph.ui.DocumentsGraphViewModel
import io.writeopia.sdk.persistence.core.di.RepositoryInjector

class DocumentsGraphInjection(
    private val repositoryInjection: RepositoryInjector,
    private val authCoreInjection: AuthCoreInjectionNeo = AuthCoreInjectionNeo.singleton()
) {

    private fun provideGraphRepository() =
        GraphRepository(
            FoldersInjector.singleton().provideFoldersRepository(),
            repositoryInjection.provideDocumentRepository()
        )

    @Composable
    fun injectViewModel(): DocumentsGraphViewModel = viewModel {
        DocumentsGraphViewModel(provideGraphRepository(), authCoreInjection.provideAuthRepository())
    }
}
