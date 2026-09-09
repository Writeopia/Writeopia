package io.writeopia.genai.di

import io.writeopia.di.AppConnectionInjection
import io.writeopia.genai.api.GenAiApi
import io.writeopia.genai.repository.GenAiRepository
import io.writeopia.sdk.network.injector.WriteopiaConnectionInjector

class GenAiInjection private constructor(
    private val appConnectionInjection: AppConnectionInjection,
    private val baseUrl: String,
    private val defaultModel: String? = null
) {
    private var apiInstance: GenAiApi? = null
    private var repositoryInstance: GenAiRepository? = null

    fun provideGenAiApi(): GenAiApi = apiInstance ?: GenAiApi(
        // Use WriteopiaConnectionInjector's httpClient which has bearer token authentication
        client = WriteopiaConnectionInjector.singleton().httpClient(),
        json = appConnectionInjection.provideJson(),
        baseUrl = baseUrl
    ).also {
        apiInstance = it
    }

    fun provideRepository(): GenAiRepository = repositoryInstance ?: GenAiRepository(
        genAiApi = provideGenAiApi(),
        defaultModel = defaultModel
    ).also {
        repositoryInstance = it
    }

    companion object {
        private var instance: GenAiInjection? = null

        fun initialize(
            appConnectionInjection: AppConnectionInjection = AppConnectionInjection.singleton(),
            baseUrl: String,
            defaultModel: String? = null
        ): GenAiInjection = GenAiInjection(
            appConnectionInjection = appConnectionInjection,
            baseUrl = baseUrl,
            defaultModel = defaultModel
        ).also {
            instance = it
        }

        fun singleton(): GenAiInjection? = instance
    }
}
