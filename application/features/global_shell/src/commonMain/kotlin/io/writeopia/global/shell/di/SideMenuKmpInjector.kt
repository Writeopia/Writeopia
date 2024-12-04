package io.writeopia.global.shell.di

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import io.writeopia.auth.core.di.AuthCoreInjection
import io.writeopia.global.shell.viewmodel.GlobalShellKmpViewModel
import io.writeopia.global.shell.viewmodel.GlobalShellViewModel
import io.writeopia.notemenu.data.repository.ConfigurationRepository
import io.writeopia.notemenu.data.repository.FolderRepository
import io.writeopia.notemenu.data.usecase.NotesNavigationUseCase
import io.writeopia.notemenu.data.usecase.NotesUseCase
import io.writeopia.notemenu.di.NotesInjector
import io.writeopia.notemenu.di.UiConfigurationInjector
import io.writeopia.sdk.persistence.core.di.RepositoryInjector
import io.writeopia.sdk.persistence.core.repository.DocumentRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

class SideMenuKmpInjector(
    private val notesInjector: NotesInjector,
    private val authCoreInjection: AuthCoreInjection,
    private val repositoryInjection: RepositoryInjector,
    private val uiConfigurationInjector: UiConfigurationInjector,
    private val selectionState: StateFlow<Boolean>
) : SideMenuInjector {
    private fun provideDocumentRepository(): DocumentRepository =
        repositoryInjection.provideDocumentRepository()

    private fun provideNotesUseCase(
        documentRepository: DocumentRepository = provideDocumentRepository(),
        configurationRepository: ConfigurationRepository =
            notesInjector.provideNotesConfigurationRepository(),
        folderRepository: FolderRepository = notesInjector.provideFoldersRepository()
    ): NotesUseCase {
        return NotesUseCase.singleton(documentRepository, configurationRepository, folderRepository)
    }

    @Composable
    override fun provideSideMenuViewModel(coroutineScope: CoroutineScope?): GlobalShellViewModel =
        remember {
            GlobalShellKmpViewModel(
                notesUseCase = provideNotesUseCase(),
                uiConfigurationRepo = uiConfigurationInjector.provideUiConfigurationRepository(),
                authManager = authCoreInjection.provideAccountManager(),
                notesNavigationUseCase = NotesNavigationUseCase.singleton(),
            ).apply {
                if (coroutineScope != null) {
                    this.initCoroutine(coroutineScope)
                }
            }
        }
}
