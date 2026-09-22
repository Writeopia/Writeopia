package io.writeopia.auth.core.di

import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.writeopia.auth.core.data.AuthApi
import io.writeopia.auth.core.manager.AuthRepository
import io.writeopia.auth.core.repository.KeychainAuthRepository
import io.writeopia.auth.core.token.TokenManager
import io.writeopia.di.AppConnectionInjection
import io.writeopia.sdk.network.injector.WriteopiaConnectionInjector
import io.writeopia.sdk.network.oauth.TokenRefreshResult
import io.writeopia.sql.WriteopiaDb
import io.writeopia.sqldelight.di.WriteopiaDbInjector

actual class AuthCoreInjectionNeo(
    private val writeopiaDb: WriteopiaDb? = WriteopiaDbInjector.singleton()?.database,
    private val appConnectionInjection: AppConnectionInjection = AppConnectionInjection.singleton(),
) {

    private val authRepository: AuthRepository by lazy {
        KeychainAuthRepository(writeopiaDb)
    }

    // Use getBaseUrl() to avoid triggering singleton creation before bearer handler is set.
    // The client is authenticated with a Bearer token lazily resolved from tokenManager below,
    // so it works without hitting the AuthApi <-> TokenManager construction cycle.
    private val authApi: AuthApi by lazy {
        AuthApi(
            client = appConnectionInjection.provideHttpClient().config {
                install(Auth) {
                    bearer {
                        loadTokens {
                            BearerTokens(
                                tokenManager.getIdToken() ?: "",
                                tokenManager.getRefreshToken() ?: ""
                            )
                        }

                        refreshTokens {
                            when (val result = tokenManager.refreshTokens()) {
                                is TokenRefreshResult.Success ->
                                    BearerTokens(result.accessToken, result.refreshToken)

                                else -> null
                            }
                        }
                    }
                }
            },
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
