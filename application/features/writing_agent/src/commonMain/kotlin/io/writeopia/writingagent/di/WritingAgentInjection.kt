package io.writeopia.writingagent.di

import io.writeopia.sdk.network.injector.WriteopiaConnectionInjector
import io.writeopia.writingagent.api.KtorWritingAgentApi
import io.writeopia.writingagent.api.WritingAgentApi

/**
 * Builds the API of the writing agent. It is initialized where the cloud AI is, with the same
 * backend URL; the editor asks for the singleton and does without the agent when there is none.
 */
class WritingAgentInjection private constructor(private val baseUrl: String) {

    private var api: WritingAgentApi? = null

    fun provideApi(): WritingAgentApi = api ?: KtorWritingAgentApi(
        // The connection injector's client carries the bearer token of the session.
        client = WriteopiaConnectionInjector.singleton().httpClient(),
        baseUrl = baseUrl,
    ).also {
        api = it
    }

    companion object {
        private var instance: WritingAgentInjection? = null

        fun initialize(baseUrl: String): WritingAgentInjection =
            WritingAgentInjection(baseUrl).also { instance = it }

        fun singleton(): WritingAgentInjection? = instance
    }
}
