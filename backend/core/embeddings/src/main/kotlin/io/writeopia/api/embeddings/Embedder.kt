package io.writeopia.api.embeddings

import io.writeopia.api.genai.service.GenAiService

/** What a text is embedded for. Retrieval models answer better when documents and queries are told apart. */
enum class EmbeddingPurpose(val taskType: String) {
    DOCUMENT("RETRIEVAL_DOCUMENT"),
    QUERY("RETRIEVAL_QUERY"),
}

/** Turns a text into a vector. Vectors of one [model] with one [dimension] can be compared. */
interface Embedder {
    val model: String
    val dimension: Int

    /** The vector of [text], or null when the model cannot answer (not configured, failure, blank text). */
    suspend fun embed(text: String, purpose: EmbeddingPurpose): FloatArray?
}

/** Gemini's embedding model, through the backend's [GenAiService]. */
class GeminiEmbedder(private val genAiService: GenAiService) : Embedder {
    override val model: String = genAiService.embeddingModel
    override val dimension: Int = genAiService.embeddingDimension

    override suspend fun embed(text: String, purpose: EmbeddingPurpose): FloatArray? =
        genAiService.embed(text, purpose.taskType)
}
