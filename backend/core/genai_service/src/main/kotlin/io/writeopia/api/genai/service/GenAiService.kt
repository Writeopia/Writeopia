package io.writeopia.api.genai.service

import com.google.genai.Client
import io.writeopia.api.genai.model.AiGenerateResponse
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

class GenAiService(
    private val projectId: String = System.getenv("GOOGLE_CLOUD_PROJECT") ?: "",
    private val location: String = System.getenv("GOOGLE_CLOUD_LOCATION") ?: "us-central1",
    private val defaultModel: String = System.getenv("GENAI_MODEL") ?: "gemini-2.0-flash"
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

    suspend fun generateWithUsage(prompt: String, modelName: String? = null): Pair<AiGenerateResponse, TokenUsage> {
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
                    null
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

    fun streamGenerate(prompt: String, modelName: String? = null): Flow<AiGenerateResponse> = flow {
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

            // ResponseStream is iterable
            responseStream.use { stream ->
                for (response in stream) {
                    val chunk = response.text()

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

    suspend fun generateSummary(text: String, modelName: String? = null): AiGenerateResponse {
        return generate("$SUMMARY_PROMPT:\n```\n$text\n```", modelName)
    }

    suspend fun generateSummaryWithUsage(text: String, modelName: String? = null): Pair<AiGenerateResponse, TokenUsage> {
        return generateWithUsage("$SUMMARY_PROMPT:\n```\n$text\n```", modelName)
    }

    fun streamSummary(text: String, modelName: String? = null): Flow<AiGenerateResponse> {
        return streamGenerate("$SUMMARY_PROMPT:\n```\n$text\n```", modelName)
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

    suspend fun generateFaq(text: String, modelName: String? = null): AiGenerateResponse {
        return generate("$FAQ_PROMPT:\n```\n$text\n```", modelName)
    }

    suspend fun generateFaqWithUsage(text: String, modelName: String? = null): Pair<AiGenerateResponse, TokenUsage> {
        return generateWithUsage("$FAQ_PROMPT:\n```\n$text\n```", modelName)
    }

    fun streamFaq(text: String, modelName: String? = null): Flow<AiGenerateResponse> {
        return streamGenerate("$FAQ_PROMPT:\n```\n$text\n```", modelName)
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
}
