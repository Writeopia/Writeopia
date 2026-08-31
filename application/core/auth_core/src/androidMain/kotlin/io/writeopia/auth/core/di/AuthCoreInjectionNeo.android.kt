package io.writeopia.auth.core.di

import android.content.Context
import io.writeopia.auth.core.data.AuthApi
import io.writeopia.auth.core.manager.AuthRepository
import io.writeopia.auth.core.repository.RoomAuthRepository
import io.writeopia.auth.core.repository.SecureTokenStorage
import io.writeopia.auth.core.token.TokenManager
import io.writeopia.common.utils.persistence.di.AppDaosInjection
import io.writeopia.di.AppConnectionInjection
import io.writeopia.persistence.room.injection.AppRoomDaosInjection
import io.writeopia.sdk.network.injector.WriteopiaConnectionInjector

actual class AuthCoreInjectionNeo(
    private val context: Context,
    private val appsDaosInjection: AppDaosInjection = AppRoomDaosInjection.singleton(),
    private val appConnectionInjection: AppConnectionInjection = AppConnectionInjection.singleton(),
) {

    private val secureTokenStorage: SecureTokenStorage by lazy {
        SecureTokenStorage(context)
    }

    private val authRepository: AuthRepository by lazy {
        RoomAuthRepository(
            appsDaosInjection.provideUserDao(),
            appsDaosInjection.provideWorkspaceDao(),
            secureTokenStorage
        )
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

        fun initialize(context: Context) {
            instance = AuthCoreInjectionNeo(context.applicationContext)
        }

        actual fun singleton(): AuthCoreInjectionNeo =
            instance ?: throw IllegalStateException(
                "AuthCoreInjectionNeo not initialized. Call initialize(context) first."
            )
    }
}
