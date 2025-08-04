package io.writeopia.auth.core.di

import io.writeopia.auth.core.data.AuthApi
import io.writeopia.auth.core.manager.AuthRepository
import io.writeopia.auth.core.repository.RoomAuthRepository
import io.writeopia.common.utils.persistence.di.AppDaosInjection
import io.writeopia.di.AppConnectionInjection
import io.writeopia.persistence.room.injection.AppRoomDaosInjection
import io.writeopia.sdk.network.injector.WriteopiaConnectionInjector

// Todo: Fix this
actual class AuthCoreInjectionNeo(
    // Change this to use a different persistence
    private val appsDaosInjection: AppDaosInjection = AppRoomDaosInjection.singleton(),
    private val appConnectionInjection: AppConnectionInjection = AppConnectionInjection.singleton(),
    private val connectionInjector: WriteopiaConnectionInjector =
        WriteopiaConnectionInjector.singleton()
) {

    actual fun provideAuthRepository(): AuthRepository =
        RoomAuthRepository(
            appsDaosInjection.provideUserDao(),
            appsDaosInjection.provideTokenDao(),
            appsDaosInjection.provideWorkspaceDao()
        )

    actual fun provideAuthApi(): AuthApi =
        AuthApi(
            client = appConnectionInjection.provideHttpClient(),
            baseUrl = connectionInjector.baseUrl()
        )

    actual companion object {
        private var instance: AuthCoreInjectionNeo? = null

        actual fun singleton(): AuthCoreInjectionNeo =
            instance ?: AuthCoreInjectionNeo().also {
                instance = it
            }
    }
}
