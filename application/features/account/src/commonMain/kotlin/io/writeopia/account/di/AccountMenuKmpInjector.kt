package io.writeopia.account.di

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import io.writeopia.account.viewmodel.AccountMenuKmpViewModel
import io.writeopia.account.viewmodel.AccountMenuViewModel
import io.writeopia.account.viewmodel.UserAddViewModel
import io.writeopia.account.viewmodel.UserSearchViewModel
import io.writeopia.account.viewmodel.WorkspaceUsersViewModel
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

    @Composable
    fun provideWorkspaceUsersViewModel(
        workspaceId: String,
        workspaceName: String
    ): WorkspaceUsersViewModel =
        viewModel(key = "workspace_users_$workspaceId") {
            WorkspaceUsersViewModel(
                workspaceId = workspaceId,
                workspaceName = workspaceName,
                workspaceApi = workspaceInjection.provideWorkspaceApi(),
                authRepository = authCoreInjection.provideAuthRepository()
            )
        }

    @Composable
    fun provideUserSearchViewModel(
        workspaceId: String,
        workspaceName: String
    ): UserSearchViewModel =
        viewModel(key = "user_search_$workspaceId") {
            UserSearchViewModel(
                workspaceId = workspaceId,
                workspaceName = workspaceName,
                workspaceApi = workspaceInjection.provideWorkspaceApi(),
                authRepository = authCoreInjection.provideAuthRepository()
            )
        }

    @Composable
    fun provideUserAddViewModel(
        workspaceId: String,
        workspaceName: String,
        userId: String,
        userName: String,
        userEmail: String
    ): UserAddViewModel =
        viewModel(key = "user_add_${workspaceId}_$userId") {
            UserAddViewModel(
                workspaceId = workspaceId,
                workspaceName = workspaceName,
                userId = userId,
                userName = userName,
                userEmail = userEmail,
                workspaceApi = workspaceInjection.provideWorkspaceApi(),
                authRepository = authCoreInjection.provideAuthRepository()
            )
        }

    companion object {
        private var instance: AccountMenuKmpInjector? = null

        fun singleton(): AccountMenuKmpInjector = instance ?: AccountMenuKmpInjector().also {
            instance = it
        }
    }
}
