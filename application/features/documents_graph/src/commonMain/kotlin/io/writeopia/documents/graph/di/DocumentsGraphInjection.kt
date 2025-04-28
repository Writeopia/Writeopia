package io.writeopia.documents.graph.di

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import io.writeopia.core.configuration.di.AppConfigurationInjector
import io.writeopia.core.folders.di.FoldersInjector
import io.writeopia.core.folders.repository.NotesUseCase
import io.writeopia.documents.graph.ui.DocumentsGraphViewModel
import io.writeopia.sdk.persistence.core.di.RepositoryInjector

class DocumentsGraphInjection(
    private val appConfigurationInjector: AppConfigurationInjector =
        AppConfigurationInjector.singleton(),
    private val repositoryInjection: RepositoryInjector,
) {

    private fun provideNotesUseCase() =
        NotesUseCase.singleton(
            documentRepository = repositoryInjection.provideDocumentRepository(),
            notesConfig = appConfigurationInjector.provideNotesConfigurationRepository(),
            folderRepository = FoldersInjector.singleton().provideFoldersRepository()
        )

    @Composable
    fun injectViewModel(): DocumentsGraphViewModel = viewModel {
        DocumentsGraphViewModel(provideNotesUseCase())
    }
}
