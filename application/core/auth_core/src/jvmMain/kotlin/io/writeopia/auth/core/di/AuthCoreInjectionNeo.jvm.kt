package io.writeopia.auth.core.di

import io.writeopia.auth.core.data.AuthApi
import io.writeopia.auth.core.manager.AuthRepository
import io.writeopia.auth.core.repository.EncryptedAuthRepository
import io.writeopia.auth.core.token.TokenManager
import io.writeopia.sdk.network.injector.WriteopiaConnectionInjector
import io.writeopia.sql.WriteopiaDb
import io.writeopia.sqldelight.di.WriteopiaDbInjector

actual class AuthCoreInjectionNeo(
    private val writeopiaDb: WriteopiaDb? = WriteopiaDbInjector.singleton()?.database,
) {

    private val authRepository: AuthRepository by lazy {
        EncryptedAuthRepository(writeopiaDb)
    }

    private val tokenManager: TokenManager by lazy {
        TokenManager(authRepository, ::provideAuthApi)
    }

    actual fun provideAuthRepository(): AuthRepository = authRepository

    actual fun provideAuthApi(): AuthApi =
        AuthApi(
            clientProvider = { WriteopiaConnectionInjector.singleton().httpClient() },
            baseUrl = WriteopiaConnectionInjector.getBaseUrl()
        )

    actual fun provideTokenManager(): TokenManager = tokenManager

    actual companion object {
        private var instance: AuthCoreInjectionNeo? = null

        actual fun singleton(): AuthCoreInjectionNeo =
            instance ?: AuthCoreInjectionNeo().also {
                instance = it
            }
    }
}
