package io.writeopia.auth.core.di

actual class AuthCoreInjectionNeo {
    actual fun provideAccountManager(): io.writeopia.auth.core.manager.AuthRepository =
        io.writeopia.auth.core.manager.SqlDelightAuthRepository()

    actual companion object {
        private var instance: AuthCoreInjectionNeo? = null

        actual fun singleton(): AuthCoreInjectionNeo =
            instance ?: AuthCoreInjectionNeo().also {
                instance = it
            }
    }
}
