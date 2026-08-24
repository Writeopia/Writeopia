package io.writeopia.api.genai.service

import com.google.genai.kotlin.Client
import io.writeopia.api.genai.model.AiGenerateResponse
import io.writeopia.connection.logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

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
        return client ?: Client(
            project = projectId,
            location = location,
            enterprise = true
        ).also { client = it }
    }

    fun isAvailable(): Boolean {
        return projectId.isNotBlank()
    }

    suspend fun generate(prompt: String, modelName: String? = null): AiGenerateResponse {
        return withContext(Dispatchers.IO) {
            try {
                if (!isAvailable()) {
                    return@withContext AiGenerateResponse(
                        error = "GenAI is not configured. Please set GOOGLE_CLOUD_PROJECT environment variable."
                    )
                }

                val targetModel = modelName?.takeIf { it.isNotBlank() } ?: defaultModel
                val genAiClient = getClient()

                val response = genAiClient.models.generateContent(
                    model = targetModel,
                    text = prompt
                )

                val responseText = response.text

                AiGenerateResponse(
                    response = responseText,
                    done = true
                )
            } catch (e: Exception) {
                logger.error("Error generating content with GenAI", e)
                AiGenerateResponse(
                    error = e.message ?: "Unknown error occurred"
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

            val responseFlow = genAiClient.models.generateContentStream(
                model = targetModel,
                text = prompt
            )

            val accumulatedText = StringBuilder()

            responseFlow.collect { response ->
                val chunk = response.text

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

    fun streamSummary(text: String, modelName: String? = null): Flow<AiGenerateResponse> {
        return streamGenerate("$SUMMARY_PROMPT:\n```\n$text\n```", modelName)
    }

    suspend fun generateActionPoints(text: String, modelName: String? = null): AiGenerateResponse {
        return generate("$ACTIONS_POINTS_PROMPT:\n```\n$text\n```", modelName)
    }

    fun streamActionPoints(text: String, modelName: String? = null): Flow<AiGenerateResponse> {
        return streamGenerate("$ACTIONS_POINTS_PROMPT:\n```\n$text\n```", modelName)
    }

    suspend fun generateFaq(text: String, modelName: String? = null): AiGenerateResponse {
        return generate("$FAQ_PROMPT:\n```\n$text\n```", modelName)
    }

    fun streamFaq(text: String, modelName: String? = null): Flow<AiGenerateResponse> {
        return streamGenerate("$FAQ_PROMPT:\n```\n$text\n```", modelName)
    }

    suspend fun generateTags(text: String, modelName: String? = null): AiGenerateResponse {
        return generate("$TAGS_PROMPT:\n```\n$text\n```", modelName)
    }

    fun streamTags(text: String, modelName: String? = null): Flow<AiGenerateResponse> {
        return streamGenerate("$TAGS_PROMPT:\n```\n$text\n```", modelName)
    }
}
