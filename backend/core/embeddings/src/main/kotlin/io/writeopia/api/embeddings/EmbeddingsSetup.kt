package io.writeopia.api.embeddings

import app.cash.sqldelight.db.SqlDriver
import io.writeopia.api.genai.service.GenAiService
import io.writeopia.connection.logger

/** The embedder and the store a service uses, or nothing when embeddings are off. */
data class Embeddings(val embedder: Embedder, val store: EmbeddingStore)

/**
 * Builds the embeddings of a service from its environment: Gemini through [GenAiService] and
 * pgvector through the service's database driver. Answers null, and says why in the log, when
 * embeddings are disabled, the cloud AI is not configured, or the database has no pgvector,
 * so the callers simply skip them.
 */
object EmbeddingsSetup {
    const val ENABLED_ENV = "WRITEOPIA_EMBEDDINGS_ENABLED"

    fun enabledFromEnv(value: String? = System.getenv(ENABLED_ENV)): Boolean = value?.toBoolean() ?: true

    fun create(
        genAiService: GenAiService?,
        driver: SqlDriver,
        enabled: Boolean = enabledFromEnv(),
    ): Embeddings? {
        if (!enabled) {
            logger.info("Embeddings disabled by $ENABLED_ENV")
            return null
        }
        if (genAiService == null || !genAiService.isAvailable()) {
            logger.info("Embeddings off: the cloud AI is not configured")
            return null
        }

        val store = PgVectorEmbeddingStore(driver, genAiService.embeddingDimension)
        val available = try {
            store.isAvailable()
        } catch (e: Exception) {
            logger.warn("Embeddings off: could not check for pgvector: {}", e.message)
            return null
        }
        if (!available) {
            logger.warn("Embeddings off: the vector extension is not installed in the database")
            return null
        }

        logger.info(
            "Embeddings on: model {} with {} dimensions",
            genAiService.embeddingModel,
            genAiService.embeddingDimension
        )
        return Embeddings(GeminiEmbedder(genAiService), store)
    }
}
