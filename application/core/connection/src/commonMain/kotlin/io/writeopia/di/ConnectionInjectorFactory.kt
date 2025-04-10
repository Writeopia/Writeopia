package io.writeopia.di

import io.writeopia.sdk.network.injector.WriteopiaConnectionInjector

object ConnectionInjectorFactory {
    private var instance: WriteopiaConnectionInjector? = null

    fun singleton(): WriteopiaConnectionInjector =
        instance ?: WriteopiaConnectionInjector.singleton().also {
            instance = it
        }
}
