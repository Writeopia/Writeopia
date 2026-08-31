package io.writeopia.auth.core.di

import io.writeopia.auth.core.data.AuthApi
import io.writeopia.auth.core.manager.AuthRepository
import io.writeopia.auth.core.token.TokenManager
import io.writeopia.sdk.network.injector.WriteopiaConnectionInjector

expect class AuthCoreInjectionNeo {

    fun provideAuthRepository(): AuthRepository

    fun provideAuthApi(): AuthApi

    fun provideTokenManager(): TokenManager

    companion object {
        fun singleton(): AuthCoreInjectionNeo
    }
}

/**
 * Sets up the bearer token handler for automatic token refresh.
 * Call this after WriteopiaConnectionInjector.setBaseUrl() and before
 * any API calls that require authentication.
 */
fun setupBearerTokenHandler() {
    val authCoreInjection = AuthCoreInjectionNeo.singleton()
    val tokenManager = authCoreInjection.provideTokenManager()
    WriteopiaConnectionInjector.setBearerTokenHandler(tokenManager)
}
