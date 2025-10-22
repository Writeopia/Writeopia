package io.writeopia.auth.di

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import io.writeopia.auth.core.data.AuthApi
import io.writeopia.auth.core.di.AuthCoreInjectionNeo
import io.writeopia.auth.core.manager.AuthRepository
import io.writeopia.auth.menu.AuthMenuViewModel
import io.writeopia.auth.register.RegisterViewModel
import io.writeopia.auth.register.ResetPasswordViewModel
import io.writeopia.auth.workspace.ChooseWorkspaceViewModel
import io.writeopia.auth.core.data.WorkspaceApi
import io.writeopia.di.AppConnectionInjection
import io.writeopia.sdk.network.injector.WriteopiaConnectionInjector

class AuthInjection private constructor(
    private val authCoreInjection: AuthCoreInjectionNeo = AuthCoreInjectionNeo.singleton(),
    private val appConnectionInjection: AppConnectionInjection = AppConnectionInjection.singleton(),
    private val connectionInjector: WriteopiaConnectionInjector =
        WriteopiaConnectionInjector.singleton(),
) {

    fun provideWorkspaceApi() =
        WorkspaceApi(
            appConnectionInjection.provideHttpClient(),
            connectionInjector.baseUrl()
        )

    @Composable
    internal fun provideRegisterViewModel(
        authRepository: AuthRepository = authCoreInjection.provideAuthRepository(),
        authApi: AuthApi = authCoreInjection.provideAuthApi()
    ): RegisterViewModel = viewModel { RegisterViewModel(authRepository, authApi) }

    @Composable
    internal fun provideResetPasswordViewModel(
        authApi: AuthApi = authCoreInjection.provideAuthApi(),
        authRepository: AuthRepository = authCoreInjection.provideAuthRepository(),
    ): ResetPasswordViewModel = viewModel { ResetPasswordViewModel(authApi, authRepository) }

    @Composable
    fun provideAuthMenuViewModel(
        authManager: AuthRepository = authCoreInjection.provideAuthRepository(),
        authApi: AuthApi = authCoreInjection.provideAuthApi()
    ): AuthMenuViewModel = viewModel { AuthMenuViewModel(authManager, authApi) }

    @Composable
    fun provideChooseWorkspaceViewModel(): ChooseWorkspaceViewModel = viewModel {
        ChooseWorkspaceViewModel(
            authRepository = authCoreInjection.provideAuthRepository(),
            workspaceApi = provideWorkspaceApi()
        )
    }

    companion object {
        private var instance: AuthInjection? = null

        fun singleton() = instance ?: AuthInjection().also {
            instance = it
        }
    }
}
