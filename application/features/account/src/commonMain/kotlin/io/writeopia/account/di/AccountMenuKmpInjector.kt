package io.writeopia.account.di

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import io.writeopia.account.viewmodel.AccountMenuKmpViewModel
import io.writeopia.account.viewmodel.AccountMenuViewModel
import io.writeopia.auth.core.di.AuthCoreInjectionNeo
import io.writeopia.core.folders.di.WorkspaceInjection

class AccountMenuKmpInjector private constructor(
    private val workspaceInjection: WorkspaceInjection = WorkspaceInjection.singleton(),
) {

    private fun provideAccountMenuKmpViewModel(): AccountMenuKmpViewModel =
        AccountMenuKmpViewModel(
            authRepository = AuthCoreInjectionNeo.singleton().provideAuthRepository(),
            workspaceSync = workspaceInjection.provideWorkspaceSync()
        )

    @Composable
    fun provideAccountMenuViewModel(): AccountMenuViewModel =
        viewModel { provideAccountMenuKmpViewModel() }

    companion object {
        private var instance: AccountMenuKmpInjector? = null

        fun singleton(): AccountMenuKmpInjector = instance ?: AccountMenuKmpInjector().also {
            instance = it
        }
    }
}
