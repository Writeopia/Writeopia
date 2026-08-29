package io.writeopia.auth.core.di

import android.content.Context
import io.writeopia.auth.core.data.AuthApi
import io.writeopia.auth.core.manager.AuthRepository
import io.writeopia.auth.core.repository.RoomAuthRepository
import io.writeopia.auth.core.repository.SecureTokenStorage
import io.writeopia.common.utils.persistence.di.AppDaosInjection
import io.writeopia.di.AppConnectionInjection
import io.writeopia.persistence.room.injection.AppRoomDaosInjection
import io.writeopia.sdk.network.injector.WriteopiaConnectionInjector

actual class AuthCoreInjectionNeo(
    private val context: Context,
    private val appsDaosInjection: AppDaosInjection = AppRoomDaosInjection.singleton(),
    private val appConnectionInjection: AppConnectionInjection = AppConnectionInjection.singleton(),
    private val connectionInjector: WriteopiaConnectionInjector =
        WriteopiaConnectionInjector.singleton()
) {

    private val secureTokenStorage: SecureTokenStorage by lazy {
        SecureTokenStorage(context)
    }

    actual fun provideAuthRepository(): AuthRepository =
        RoomAuthRepository(
            appsDaosInjection.provideUserDao(),
            appsDaosInjection.provideTokenDao(),
            appsDaosInjection.provideWorkspaceDao(),
            secureTokenStorage
        )

    actual fun provideAuthApi(): AuthApi =
        AuthApi(
            client = appConnectionInjection.provideHttpClient(),
            baseUrl = connectionInjector.baseUrl()
        )

    actual companion object {
        private var instance: AuthCoreInjectionNeo? = null

        fun initialize(context: Context) {
            instance = AuthCoreInjectionNeo(context)
        }

        actual fun singleton(): AuthCoreInjectionNeo =
            instance ?: throw IllegalStateException(
                "AuthCoreInjectionNeo not initialized. Call initialize(context) first."
            )
    }
}
