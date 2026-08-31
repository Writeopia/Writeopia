package io.writeopia.di

import io.ktor.client.HttpClient
import io.ktor.client.plugins.logging.DEFAULT
import io.ktor.client.plugins.logging.Logger
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule

class AppConnectionInjection private constructor(
    private val json: Json = Json {
        serializersModule = SerializersModule {
            ignoreUnknownKeys = true
        }
    },
    private val apiLogger: Logger = Logger.DEFAULT
) {
    private var httpClient: HttpClient? = null

//    private var _tokenJwt: String? = null
//    private fun token() = _tokenJwt
//
//    fun setJwtToken(token: String) {
//        _tokenJwt = token
//    }

    fun provideJson() = json

    fun provideHttpClient(): HttpClient = httpClient ?:
    ApiInjectorDefaults.httpClient(json, apiLogger).also {
        httpClient = it
    }


    companion object {
        private var instance: AppConnectionInjection? = null

        fun singleton(): AppConnectionInjection = instance ?: AppConnectionInjection().also {
            instance = it
        }
    }
}

expect object ApiInjectorDefaults {
    fun httpClient(
        json: Json,
        apiLogger: Logger
    ): HttpClient
}
