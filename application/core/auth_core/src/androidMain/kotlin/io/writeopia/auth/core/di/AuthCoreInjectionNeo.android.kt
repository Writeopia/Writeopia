package io.writeopia.auth.core.di

import android.content.SharedPreferences
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import io.writeopia.auth.core.data.AuthApi
import io.writeopia.auth.core.manager.AuthRepository
import io.writeopia.common.utils.di.SharedPreferencesInjector

actual class AuthCoreInjectionNeo private constructor(
    private val sharedPreferences: SharedPreferences
) {

    private val auth: FirebaseAuth = Firebase.auth

    actual fun provideAuthApi(): AuthApi {
        TODO("Not yet implemented")
    }

    actual fun provideAuthRepository(): AuthRepository {
        TODO("Not yet implemented")
    }

    actual companion object {
        private var instance: AuthCoreInjectionNeo? = null

        actual fun singleton(): AuthCoreInjectionNeo =
            instance ?: AuthCoreInjectionNeo(
                SharedPreferencesInjector.singleton().sharedPreferences
            ).also {
                instance = it
            }
    }
}
