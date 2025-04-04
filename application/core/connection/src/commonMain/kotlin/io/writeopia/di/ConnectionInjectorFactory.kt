package io.writeopia.di

import io.writeopia.auth.core.token.AppBearerTokenHandler
import io.writeopia.sdk.network.injector.WriteopiaConnectionInjector

object ConnectionInjectorFactory {
    private var instance: WriteopiaConnectionInjector? = null

    fun singleton(): WriteopiaConnectionInjector =
        instance ?: WriteopiaConnectionInjector(
            baseUrl = "https://writeopia.io/api",
            bearerTokenHandler = AppBearerTokenHandler,
            apiLogger = ApiLogger,
            disableWebsocket = false,
        ).also {
            instance = it
        }
}
