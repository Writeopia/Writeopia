package io.writeopia.genai.di

import io.writeopia.di.AppConnectionInjection
import io.writeopia.genai.api.GenAiApi
import io.writeopia.genai.repository.GenAiRepository

class GenAiInjection private constructor(
    private val appConnectionInjection: AppConnectionInjection,
    private val baseUrl: String,
    private val getAuthToken: suspend () -> String?,
    private val defaultModel: String? = null
) {
    private var apiInstance: GenAiApi? = null
    private var repositoryInstance: GenAiRepository? = null

    private fun provideApi(): GenAiApi = apiInstance ?: GenAiApi(
        client = appConnectionInjection.provideHttpClient(),
        json = appConnectionInjection.provideJson(),
        baseUrl = baseUrl,
        getAuthToken = getAuthToken
    ).also {
        apiInstance = it
    }

    fun provideRepository(): GenAiRepository = repositoryInstance ?: GenAiRepository(
        genAiApi = provideApi(),
        defaultModel = defaultModel
    ).also {
        repositoryInstance = it
    }

    companion object {
        private var instance: GenAiInjection? = null

        fun initialize(
            appConnectionInjection: AppConnectionInjection = AppConnectionInjection.singleton(),
            baseUrl: String,
            getAuthToken: suspend () -> String?,
            defaultModel: String? = null
        ): GenAiInjection = GenAiInjection(
            appConnectionInjection = appConnectionInjection,
            baseUrl = baseUrl,
            getAuthToken = getAuthToken,
            defaultModel = defaultModel
        ).also {
            instance = it
        }

        fun singleton(): GenAiInjection? = instance
    }
}
