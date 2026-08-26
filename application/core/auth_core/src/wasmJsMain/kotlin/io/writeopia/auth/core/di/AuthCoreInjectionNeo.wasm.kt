package io.writeopia.auth.core.di

import io.writeopia.auth.core.data.AuthApi
import io.writeopia.auth.core.manager.AuthRepository
import io.writeopia.auth.core.manager.InMemoryAuthRepository
import io.writeopia.di.AppConnectionInjection
import io.writeopia.sdk.network.injector.WriteopiaConnectionInjector

actual class AuthCoreInjectionNeo(
    private val appConnectionInjection: AppConnectionInjection = AppConnectionInjection.singleton(),
    private val connectionInjector: WriteopiaConnectionInjector =
        WriteopiaConnectionInjector.singleton()
) {

    actual fun provideAuthRepository(): AuthRepository = InMemoryAuthRepository()

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
