package io.writeopia.auth.core.di

import android.content.SharedPreferences
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import io.writeopia.auth.core.manager.AuthRepository
import io.writeopia.auth.core.manager.FirebaseAuthRepository
import io.writeopia.auth.core.repository.SharedPrefsAuthRepository
import io.writeopia.common.utils.di.SharedPreferencesInjector

actual class AuthCoreInjectionNeo private constructor(
    private val sharedPreferences: SharedPreferences
) {

    private val auth: FirebaseAuth = Firebase.auth

    actual fun provideAccountManager(): io.writeopia.auth.core.manager.AuthRepository = FirebaseAuthRepository(auth)

    actual fun provideAuthRepository(): AuthRepository =
        SharedPrefsAuthRepository(sharedPreferences)

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
