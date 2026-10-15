package io.writeopia.api.genai.service

import io.writeopia.sdk.models.presentation.PresentationPrompt
import com.google.genai.Client
import com.google.genai.types.EmbedContentConfig
import com.google.genai.types.GenerateContentConfig
import com.google.genai.types.ImageConfig
import com.google.genai.types.Tool
import com.google.genai.types.UrlContext
import io.writeopia.api.genai.model.AiGenerateResponse
import io.writeopia.api.genai.model.GeneratedImage
import io.writeopia.api.genai.model.ImageGeneration
import io.writeopia.api.genai.model.TokenUsage
import io.writeopia.connection.logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

data class GenAiResult(
    val text: String,
    val tokenUsage: TokenUsage
)

private const val SUMMARY_PROMPT =
    "Summarize the following text while preserving its key points and main ideas. Use at most 12 lines. Keep the summary concise and clear. If the text contains multiple sections, highlight the most important aspects of each. Maintain the original tone and intent where possible. Detect the language of the text and write in the same language"

private const val ACTIONS_POINTS_PROMPT =
    "Extract key action points from the following text. Create just an introduction and the list of action items with at most 10. Don't add conclusions or introductions. Use the language of the text"

private const val FAQ_PROMPT =
    "Generate a list of frequently asked questions (FAQs) based on the following text. Include clear and concise answers that help users understand key points. Prioritize the most relevant and common concerns. Detect the language of the text and write in the same language"

private const val TAGS_PROMPT =
    "Generate a list of relevant tags based on the following text. The tags should capture key topics, themes, and important concepts. Use concise, single-word tags that accurately represent the content. Produce at most 10 tags. Detect the language of the text and write in the same language."

private const val DOCUMENT_TAG_PROMPT =
    "Make up one tag for the document below, for a workspace where documents carry tags from a " +
        "shared catalogue. The tag names a subject, project or kind of document that this document " +
        "and others like it share: a broad category of at most three words, like \"Meetings\", " +
        "\"Recipes\" or \"Project Atlas\", not a title for this one document. Also write a " +
        "one-sentence explanation of what the tag means, so people and other tools know which " +
        "documents should carry it. Do not reuse or paraphrase a name from the existing tags. " +
        "Use the language of the text. Answer only with JSON of the form " +
        "{\"name\": \"...\", \"description\": \"...\"}."

class GenAiService(
    private val projectId: String = System.getenv("GOOGLE_CLOUD_PROJECT") ?: "",
    private val location: String = System.getenv("GOOGLE_CLOUD_LOCATION") ?: "us-central1",
    private val defaultModel: String = System.getenv("GENAI_MODEL") ?: "gemini-2.0-flash",
    /** The Gemini model that answers with pictures. Its usage metadata counts the image tokens. */
    private val imageModel: String = System.getenv("GENAI_IMAGE_MODEL") ?: DEFAULT_IMAGE_MODEL,
    /** The model that turns text into vectors, for the embeddings of documents and folders. */
    val embeddingModel: String = System.getenv("GENAI_EMBEDDING_MODEL") ?: DEFAULT_EMBEDDING_MODEL,
    /** How many dimensions [embeddingModel] answers with. Must match the `vector(n)` columns. */
    val embeddingDimension: Int = System.getenv("GENAI_EMBEDDING_DIMENSION")?.toIntOrNull()
        ?: DEFAULT_EMBEDDING_DIMENSION
) {
    private var client: Client? = null

    private fun getClient(): Client {
        return client ?: Client.builder()
            .project(projectId)
            .location(location)
            .enterprise(true)
            .build()
            .also { client = it }
    }

    fun isAvailable(): Boolean {
        return projectId.isNotBlank()
    }

    suspend fun generate(prompt: String, modelName: String? = null): AiGenerateResponse {
        val result = generateWithUsage(prompt, modelName)
        return result.first
    }

    suspend fun generateWithUsage(
        prompt: String,
        modelName: String? = null,
        config: GenerateContentConfig? = null,
    ): Pair<AiGenerateResponse, TokenUsage> {
        return withContext(Dispatchers.IO) {
            try {
                if (!isAvailable()) {
                    return@withContext Pair(
                        AiGenerateResponse(
                            error = "GenAI is not configured. Please set GOOGLE_CLOUD_PROJECT environment variable."
                        ),
                        TokenUsage(0, 0, 0)
                    )
                }

                val targetModel = modelName?.takeIf { it.isNotBlank() } ?: defaultModel
                val genAiClient = getClient()

                val response = genAiClient.models.generateContent(
                    targetModel,
                    prompt,
                    config
                )

                val usage = response.usageMetadata().orElse(null)
                val tokenUsage = TokenUsage(
                    inputTokens = usage?.promptTokenCount()?.orElse(0) ?: 0,
                    outputTokens = usage?.candidatesTokenCount()?.orElse(0) ?: 0,
                    totalTokens = usage?.totalTokenCount()?.orElse(0) ?: 0
                )

                val responseText = response.text()

                Pair(
                    AiGenerateResponse(
                        response = responseText,
                        done = true
                    ),
                    tokenUsage
                )
            } catch (e: Exception) {
                logger.error("Error generating content with GenAI", e)
                Pair(
                    AiGenerateResponse(
                        error = e.message ?: "Unknown error occurred"
                    ),
                    TokenUsage(0, 0, 0)
                )
            }
        }
    }

    /**
     * Streams content generation with a callback for token usage when complete.
     * The onUsage callback is invoked with the final token usage after streaming completes.
     */
    fun streamGenerateWithUsage(
        prompt: String,
        modelName: String? = null,
        onUsage: (TokenUsage) -> Unit = {}
    ): Flow<AiGenerateResponse> = flow {
        try {
            if (!isAvailable()) {
                emit(
                    AiGenerateResponse(
                        error = "GenAI is not configured. Please set GOOGLE_CLOUD_PROJECT environment variable."
                    )
                )
                return@flow
            }

            val targetModel = modelName?.takeIf { it.isNotBlank() } ?: defaultModel
            val genAiClient = getClient()

            val responseStream = genAiClient.models.generateContentStream(
                targetModel,
                prompt,
                null
            )

            val accumulatedText = StringBuilder()
            var finalUsage = TokenUsage(0, 0, 0)

            // ResponseStream is iterable
            responseStream.use { stream ->
                for (response in stream) {
                    val chunk = response.text()

                    // Capture usage metadata from each response (last one has the total)
                    response.usageMetadata().ifPresent { usage ->
                        finalUsage = TokenUsage(
                            inputTokens = usage.promptTokenCount().orElse(0),
                            outputTokens = usage.candidatesTokenCount().orElse(0),
                            totalTokens = usage.totalTokenCount().orElse(0)
                        )
                    }

                    if (chunk != null) {
                        accumulatedText.append(chunk)
                        emit(
                            AiGenerateResponse(
                                response = accumulatedText.toString(),
                                done = false
                            )
                        )
                    }
                }
            }

            // Report final usage
            onUsage(finalUsage)

            emit(
                AiGenerateResponse(
                    response = accumulatedText.toString(),
                    done = true
                )
            )
        } catch (e: Exception) {
            logger.error("Error streaming content with GenAI", e)
            emit(
                AiGenerateResponse(
                    error = e.message ?: "Unknown error occurred"
                )
            )
        }
    }.flowOn(Dispatchers.IO)

    fun streamGenerate(prompt: String, modelName: String? = null): Flow<AiGenerateResponse> =
        streamGenerateWithUsage(prompt, modelName)

    suspend fun generateSummary(text: String, modelName: String? = null): AiGenerateResponse {
        return generate("$SUMMARY_PROMPT:\n```\n$text\n```", modelName)
    }

    suspend fun generateSummaryWithUsage(text: String, modelName: String? = null): Pair<AiGenerateResponse, TokenUsage> {
        return generateWithUsage("$SUMMARY_PROMPT:\n```\n$text\n```", modelName)
    }

    fun streamSummary(text: String, modelName: String? = null): Flow<AiGenerateResponse> {
        return streamGenerate("$SUMMARY_PROMPT:\n```\n$text\n```", modelName)
    }

    fun streamSummaryWithUsage(text: String, modelName: String? = null, onUsage: (TokenUsage) -> Unit): Flow<AiGenerateResponse> {
        return streamGenerateWithUsage("$SUMMARY_PROMPT:\n```\n$text\n```", modelName, onUsage)
    }

    /** The Markdown of the slides of a presentation of `text` (the Markdown of a document). */
    suspend fun generatePresentation(text: String, modelName: String? = null): AiGenerateResponse {
        return generate(PresentationPrompt.forDocument(text), modelName)
    }

    suspend fun generatePresentationWithUsage(text: String, modelName: String? = null): Pair<AiGenerateResponse, TokenUsage> {
        return generateWithUsage(PresentationPrompt.forDocument(text), modelName)
    }

    suspend fun generateActionPoints(text: String, modelName: String? = null): AiGenerateResponse {
        return generate("$ACTIONS_POINTS_PROMPT:\n```\n$text\n```", modelName)
    }

    suspend fun generateActionPointsWithUsage(text: String, modelName: String? = null): Pair<AiGenerateResponse, TokenUsage> {
        return generateWithUsage("$ACTIONS_POINTS_PROMPT:\n```\n$text\n```", modelName)
    }

    fun streamActionPoints(text: String, modelName: String? = null): Flow<AiGenerateResponse> {
        return streamGenerate("$ACTIONS_POINTS_PROMPT:\n```\n$text\n```", modelName)
    }

    fun streamActionPointsWithUsage(text: String, modelName: String? = null, onUsage: (TokenUsage) -> Unit): Flow<AiGenerateResponse> {
        return streamGenerateWithUsage("$ACTIONS_POINTS_PROMPT:\n```\n$text\n```", modelName, onUsage)
    }

    suspend fun generateFaq(text: String, modelName: String? = null): AiGenerateResponse {
        return generate("$FAQ_PROMPT:\n```\n$text\n```", modelName)
    }

    suspend fun generateFaqWithUsage(text: String, modelName: String? = null): Pair<AiGenerateResponse, TokenUsage> {
        return generateWithUsage("$FAQ_PROMPT:\n```\n$text\n```", modelName)
    }

    fun streamFaq(text: String, modelName: String? = null): Flow<AiGenerateResponse> {
        return streamGenerate("$FAQ_PROMPT:\n```\n$text\n```", modelName)
    }

    fun streamFaqWithUsage(text: String, modelName: String? = null, onUsage: (TokenUsage) -> Unit): Flow<AiGenerateResponse> {
        return streamGenerateWithUsage("$FAQ_PROMPT:\n```\n$text\n```", modelName, onUsage)
    }

    suspend fun generateTags(text: String, modelName: String? = null): AiGenerateResponse {
        return generate("$TAGS_PROMPT:\n```\n$text\n```", modelName)
    }

    suspend fun generateTagsWithUsage(text: String, modelName: String? = null): Pair<AiGenerateResponse, TokenUsage> {
        return generateWithUsage("$TAGS_PROMPT:\n```\n$text\n```", modelName)
    }

    fun streamTags(text: String, modelName: String? = null): Flow<AiGenerateResponse> {
        return streamGenerate("$TAGS_PROMPT:\n```\n$text\n```", modelName)
    }

    fun streamTagsWithUsage(text: String, modelName: String? = null, onUsage: (TokenUsage) -> Unit): Flow<AiGenerateResponse> {
        return streamGenerateWithUsage("$TAGS_PROMPT:\n```\n$text\n```", modelName, onUsage)
    }

    /**
     * A new tag for a document, as JSON the model is told to answer with. [existingTagNames] are
     * the names of the catalogue, which the answer must not repeat.
     */
    suspend fun generateDocumentTagWithUsage(
        documentTitle: String,
        documentText: String,
        existingTagNames: List<String>,
        modelName: String? = null,
    ): Pair<AiGenerateResponse, TokenUsage> {
        val existing = if (existingTagNames.isEmpty()) {
            "none"
        } else {
            existingTagNames.joinToString(separator = ", ") { name -> "\"$name\"" }
        }
        val prompt = "$DOCUMENT_TAG_PROMPT\n\nExisting tags: $existing\n\nTitle: $documentTitle\n\n```\n$documentText\n```"
        val config = GenerateContentConfig.builder()
            .responseMimeType("application/json")
            .build()

        return generateWithUsage(prompt, modelName, config)
    }

    fun streamGenerateBaseWithUsage(prompt: String, modelName: String? = null, onUsage: (TokenUsage) -> Unit): Flow<AiGenerateResponse> {
        return streamGenerateWithUsage(prompt, modelName, onUsage)
    }

    /**
     * Answers [prompt] with the model allowed to read the web pages the prompt names, through
     * Gemini's URL context tool. When the model or the tool refuses, the prompt is sent again
     * without the tool, so the model answers from what it knows about the address.
     */
    suspend fun generateWithUrlContextAndUsage(
        prompt: String,
        modelName: String? = null
    ): Pair<AiGenerateResponse, TokenUsage> {
        val config = GenerateContentConfig.builder()
            .tools(Tool.builder().urlContext(UrlContext.builder().build()).build())
            .build()

        val withTool = generateWithUsage(prompt, modelName, config)
        if (withTool.first.error == null && !withTool.first.response.isNullOrBlank()) return withTool

        logger.warn("GenAI could not answer with URL context, answering without it: {}", withTool.first.error)
        return generateWithUsage(prompt, modelName)
    }

    /**
     * Asks [imageModel] for one picture of [prompt]. Gemini's image models answer through the
     * same generateContent call as the text ones, with the picture as inline data, and report
     * the tokens of the picture in the usage metadata, so the image counts in the quota like
     * text does. [aspectRatio], like "16:9", shapes the picture; null leaves it to the model.
     */
    suspend fun generateImageWithUsage(
        prompt: String,
        modelName: String? = null,
        aspectRatio: String? = null
    ): ImageGeneration =
        withContext(Dispatchers.IO) {
            try {
                if (!isAvailable()) {
                    return@withContext ImageGeneration(
                        error = "GenAI is not configured. Please set GOOGLE_CLOUD_PROJECT environment variable."
                    )
                }

                val targetModel = modelName?.takeIf { it.isNotBlank() } ?: imageModel
                val config = GenerateContentConfig.builder()
                    .responseModalities("TEXT", "IMAGE")
                    .apply {
                        if (aspectRatio != null) imageConfig(ImageConfig.builder().aspectRatio(aspectRatio))
                    }
                    .build()

                val response = getClient().models.generateContent(targetModel, prompt, config)

                val usage = response.usageMetadata().orElse(null)
                val tokenUsage = TokenUsage(
                    inputTokens = usage?.promptTokenCount()?.orElse(0) ?: 0,
                    outputTokens = usage?.candidatesTokenCount()?.orElse(0) ?: 0,
                    totalTokens = usage?.totalTokenCount()?.orElse(0) ?: 0
                )

                val image = response.parts().orEmpty()
                    .mapNotNull { part -> part.inlineData().orElse(null) }
                    .firstNotNullOfOrNull { blob ->
                        blob.data().orElse(null)?.let { bytes ->
                            GeneratedImage(
                                bytes = bytes,
                                mimeType = blob.mimeType().orElse(null) ?: "image/png"
                            )
                        }
                    }

                if (image == null) {
                    logger.warn(
                        "GenAI image model {} answered without a picture: {}",
                        targetModel,
                        response.text()?.take(200)
                    )
                }

                ImageGeneration(
                    image = image,
                    error = if (image == null) "The model answered without a picture" else null,
                    tokenUsage = tokenUsage
                )
            } catch (e: Exception) {
                logger.error("Error generating an image with GenAI", e)
                ImageGeneration(error = e.message ?: "Unknown error occurred")
            }
        }

    /**
     * The vector of [text] from [embeddingModel], with [embeddingDimension] values, or null when
     * the model is not configured, the text is blank or the call fails. [taskType] tells the
     * model whether the text is a document to be found or a query looking for one.
     */
    suspend fun embed(text: String, taskType: String = "RETRIEVAL_DOCUMENT"): FloatArray? =
        withContext(Dispatchers.IO) {
            try {
                if (!isAvailable() || text.isBlank()) return@withContext null

                val config = EmbedContentConfig.builder()
                    .taskType(taskType)
                    .outputDimensionality(embeddingDimension)
                    .build()

                val response = getClient().models.embedContent(embeddingModel, text, config)
                val values = response.embeddings().orElse(null)
                    ?.firstOrNull()
                    ?.values()
                    ?.orElse(null)

                if (values.isNullOrEmpty()) {
                    logger.warn("GenAI embedding model {} answered without a vector", embeddingModel)
                    return@withContext null
                }

                FloatArray(values.size) { index -> values[index] }
            } catch (e: Exception) {
                logger.error("Error embedding text with GenAI", e)
                null
            }
        }

    companion object {
        const val DEFAULT_IMAGE_MODEL = "gemini-2.5-flash-image"
        const val DEFAULT_EMBEDDING_MODEL = "gemini-embedding-001"
        const val DEFAULT_EMBEDDING_DIMENSION = 768
    }
}
