package io.writeopia.auth.core.di

import io.writeopia.auth.core.data.AuthApi
import io.writeopia.auth.core.manager.AuthRepository
import io.writeopia.auth.core.repository.KeychainAuthRepository
import io.writeopia.auth.core.token.TokenManager
import io.writeopia.di.AppConnectionInjection
import io.writeopia.sdk.network.injector.WriteopiaConnectionInjector
import io.writeopia.sql.WriteopiaDb
import io.writeopia.sqldelight.di.WriteopiaDbInjector

actual class AuthCoreInjectionNeo(
    private val writeopiaDb: WriteopiaDb? = WriteopiaDbInjector.singleton()?.database,
    private val appConnectionInjection: AppConnectionInjection = AppConnectionInjection.singleton(),
) {

    private val authRepository: AuthRepository by lazy {
        KeychainAuthRepository(writeopiaDb)
    }

    // Use getBaseUrl() to avoid triggering singleton creation before bearer handler is set
    private val authApi: AuthApi by lazy {
        AuthApi(
            client = appConnectionInjection.provideHttpClient(),
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
