package io.writeopia.core.folders.di

import io.writeopia.di.AppConnectionInjection
import io.writeopia.sdk.network.injector.WriteopiaConnectionInjector
import io.writeopia.sdk.network.subscription.SubscriptionApi

class SubscriptionInjection private constructor(
    private val appConnectionInjection: AppConnectionInjection = AppConnectionInjection.singleton(),
    private val connectionInjector: WriteopiaConnectionInjector =
        WriteopiaConnectionInjector.singleton(),
) {

    fun provideSubscriptionApi(): SubscriptionApi =
        SubscriptionApi(
            appConnectionInjection.provideHttpClient(),
            connectionInjector.baseUrl()
        )

    companion object {
        private var instance: SubscriptionInjection? = null

        fun singleton() = instance ?: SubscriptionInjection().also {
            instance = it
        }
    }
}
