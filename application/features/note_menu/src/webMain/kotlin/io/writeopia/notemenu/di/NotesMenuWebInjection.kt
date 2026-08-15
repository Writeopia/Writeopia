package io.writeopia.notemenu.di

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import io.writeopia.auth.core.di.AuthCoreInjectionNeo
import io.writeopia.common.utils.NotesNavigation
import io.writeopia.core.folders.api.DocumentsApi
import io.writeopia.di.AppConnectionInjection
import io.writeopia.notemenu.viewmodel.ChooseNoteViewModel
import io.writeopia.notemenu.viewmodel.onlybe.OnlyBackendChooseNoteKmpViewModel
import io.writeopia.sdk.network.injector.WriteopiaConnectionInjector

/**
 * Web-specific injection that provides OnlyBackendChooseNoteKmpViewModel.
 * This viewmodel fetches all data directly from the backend without local storage.
 */
class NotesMenuWebInjection private constructor(
    private val authCoreInjection: AuthCoreInjectionNeo = AuthCoreInjectionNeo.singleton(),
    private val appConnectionInjection: AppConnectionInjection = AppConnectionInjection.singleton(),
    private val connectionInjector: WriteopiaConnectionInjector = WriteopiaConnectionInjector.singleton(),
) : NotesMenuInjection {

    private fun provideDocumentsApi() =
        DocumentsApi(appConnectionInjection.provideHttpClient(), connectionInjector.baseUrl())

    @Composable
    override fun provideChooseNoteViewModel(
        notesNavigation: NotesNavigation
    ): ChooseNoteViewModel =
        viewModel(key = "web_choose_note_${notesNavigation.id}") {
            OnlyBackendChooseNoteKmpViewModel(
                documentsApi = provideDocumentsApi(),
                authRepository = authCoreInjection.provideAuthRepository(),
                notesNavigation = notesNavigation,
            )
        }

    companion object {
        private var instance: NotesMenuWebInjection? = null

        fun singleton(): NotesMenuWebInjection =
            instance ?: NotesMenuWebInjection().also {
                instance = it
            }
    }
}
