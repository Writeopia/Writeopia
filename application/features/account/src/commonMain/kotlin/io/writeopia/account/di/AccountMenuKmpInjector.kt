package io.writeopia.account.di

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import io.writeopia.account.viewmodel.AccountMenuKmpViewModel
import io.writeopia.account.viewmodel.AccountMenuViewModel
import io.writeopia.auth.core.di.AuthCoreInjectionNeo
import io.writeopia.core.folders.di.WorkspaceInjection

class AccountMenuKmpInjector private constructor(
    private val workspaceInjection: WorkspaceInjection = WorkspaceInjection.singleton(),
    private val authCoreInjection: AuthCoreInjectionNeo = AuthCoreInjectionNeo.singleton(),
) {

    @Composable
    fun provideAccountMenuViewModel(): AccountMenuViewModel =
        viewModel {
            AccountMenuKmpViewModel(
                authRepository = authCoreInjection.provideAuthRepository(),
                workspaceHandler = workspaceInjection.provideWorkspaceHandler()
            )
        }

    companion object {
        private var instance: AccountMenuKmpInjector? = null

        fun singleton(): AccountMenuKmpInjector = instance ?: AccountMenuKmpInjector().also {
            instance = it
        }
    }
}
