package io.writeopia.common.utils.di

import android.content.SharedPreferences

class SharedPreferencesInjector private constructor(val sharedPreferences: SharedPreferences) {

    companion object {
        private var instance: SharedPreferencesInjector? = null

        fun init(sharedPreferences: SharedPreferences) {
            instance = SharedPreferencesInjector(sharedPreferences)
        }

        fun singleton() = instance ?: throw IllegalStateException("SharedPreferencesInjector not initialized!")
    }
}
