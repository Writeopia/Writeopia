package io.writeopia.auth.core.di

import io.writeopia.auth.core.data.AuthApi
import io.writeopia.auth.core.manager.AuthRepository
import io.writeopia.auth.core.manager.InMemoryAuthRepository
import io.writeopia.auth.core.token.TokenManager
import io.writeopia.sdk.network.injector.WriteopiaConnectionInjector

actual class AuthCoreInjectionNeo {

    private val authRepository: AuthRepository by lazy {
        InMemoryAuthRepository()
    }

    // WriteopiaConnectionInjector's client authenticates requests with whatever bearer
    // token handler is current at request time, so it's safe to use here even before
    // setupBearerTokenHandler() has run.
    private val authApi: AuthApi by lazy {
        AuthApi(
            client = WriteopiaConnectionInjector.singleton().httpClient(),
            baseUrl = WriteopiaConnectionInjector.getBaseUrl()
        )
    }

    private val tokenManager: TokenManager by lazy {
        TokenManager(authRepository, authApi)
    }

    actual fun provideAuthRepository(): AuthRepository = authRepository

    actual fun provideAuthApi(): AuthApi = authApi

    actual fun provideTokenManager(): TokenManager = tokenManager

    actual companion object {
        private var instance: AuthCoreInjectionNeo? = null

        actual fun singleton(): AuthCoreInjectionNeo =
            instance ?: AuthCoreInjectionNeo().also {
                instance = it
            }
    }
}
