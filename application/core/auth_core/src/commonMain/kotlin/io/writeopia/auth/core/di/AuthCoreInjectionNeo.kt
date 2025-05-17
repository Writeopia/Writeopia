package io.writeopia.auth.core.di

import io.writeopia.auth.core.data.AuthApi
import io.writeopia.auth.core.manager.AuthRepository

expect class AuthCoreInjectionNeo {

    fun provideAuthRepository(): AuthRepository

    fun provideAuthApi(): AuthApi

    companion object {
        fun singleton(): AuthCoreInjectionNeo
    }
}
