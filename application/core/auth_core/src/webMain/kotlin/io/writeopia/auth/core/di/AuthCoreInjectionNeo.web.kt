package io.writeopia.auth.core.di

import io.writeopia.auth.core.data.AuthApi
import io.writeopia.auth.core.manager.AuthRepository
import io.writeopia.auth.core.manager.SqlDelightAuthRepository
import io.writeopia.di.AppConnectionInjection
import io.writeopia.sdk.network.injector.WriteopiaConnectionInjector
import io.writeopia.sql.WriteopiaDb
import io.writeopia.sqldelight.di.WriteopiaDbInjector

actual class AuthCoreInjectionNeo(
    private val writeopiaDb: WriteopiaDb? = WriteopiaDbInjector.singleton()?.database,
    private val appConnectionInjection: AppConnectionInjection = AppConnectionInjection.singleton(),
    private val connectionInjector: WriteopiaConnectionInjector =
        WriteopiaConnectionInjector.singleton()
) {

    actual fun provideAuthRepository(): AuthRepository = SqlDelightAuthRepository(writeopiaDb)

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
