package io.writeopia.auth.core.di

import io.writeopia.auth.core.manager.AuthRepository

actual class AuthCoreInjectionNeo {
    actual fun provideAccountManager(): io.writeopia.auth.core.manager.AuthRepository =
        io.writeopia.auth.core.manager.SqlDelightRepository()

    actual companion object {
        private var instance: AuthCoreInjectionNeo? = null

        actual fun singleton(): AuthCoreInjectionNeo =
            instance ?: AuthCoreInjectionNeo().also {
                instance = it
            }
    }
}
