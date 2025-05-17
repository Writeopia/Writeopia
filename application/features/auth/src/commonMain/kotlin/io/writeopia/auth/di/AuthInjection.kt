package io.writeopia.auth.di

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import io.writeopia.auth.core.data.AuthApi
import io.writeopia.auth.core.di.AuthCoreInjectionNeo
import io.writeopia.auth.core.manager.AuthRepository
import io.writeopia.auth.menu.AuthMenuViewModel
import io.writeopia.auth.register.RegisterViewModel

class AuthInjection(
    private val authCoreInjection: AuthCoreInjectionNeo = AuthCoreInjectionNeo.singleton()
) {

    @Composable
    internal fun provideRegisterViewModel(
        authRepository: AuthRepository = authCoreInjection.provideAuthRepository(),
        authApi: AuthApi = authCoreInjection.provideAuthApi()
    ): RegisterViewModel = viewModel { RegisterViewModel(authRepository, authApi) }

    @Composable
    internal fun provideAuthMenuViewModel(
        authManager: AuthRepository = authCoreInjection.provideAuthRepository(),
        authApi: AuthApi = authCoreInjection.provideAuthApi()
    ): AuthMenuViewModel = viewModel { AuthMenuViewModel(authManager, authApi) }
}
