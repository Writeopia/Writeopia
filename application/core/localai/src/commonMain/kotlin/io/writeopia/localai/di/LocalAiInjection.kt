package io.writeopia.localai.di

import io.writeopia.localai.LocalAiRepository
import io.writeopia.localai.llama.LlamaEngine
import io.writeopia.localai.persistence.LocalAiDao
import io.writeopia.localai.persistence.LocalAiSqlDao
import io.writeopia.sql.WriteopiaDb

class LocalAiInjection private constructor(
    private val writeopiaDb: WriteopiaDb? = null
) {
    private var localAiDaoInstance: LocalAiDao? = null
    private var llamaEngineInstance: LlamaEngine? = null

    private fun provideDao(): LocalAiDao = localAiDaoInstance ?: run {
        localAiDaoInstance = LocalAiSqlDao(writeopiaDb?.localAiEntityQueries)
        localAiDaoInstance!!
    }

    fun provideLlamaEngine(): LlamaEngine = llamaEngineInstance ?: run {
        llamaEngineInstance = LlamaEngine()
        llamaEngineInstance!!
    }

    fun provideRepository() = LocalAiRepository(
        localAiDao = provideDao(),
        llamaEngine = provideLlamaEngine()
    )

    companion object {
        private var instance: LocalAiInjection? = null

        fun singleton(writeopiaDb: WriteopiaDb? = null) = instance ?: LocalAiInjection(
            writeopiaDb = writeopiaDb
        ).also { instance = it }
    }
}
