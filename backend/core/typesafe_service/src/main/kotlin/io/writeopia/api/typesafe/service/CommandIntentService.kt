package io.writeopia.api.typesafe.service

import io.writeopia.api.typesafe.client.TypeSafeClient
import io.writeopia.api.typesafe.client.TypeSafeResult
import io.writeopia.api.typesafe.model.SystemOneUsage
import io.writeopia.app.dto.ai.AiCommandIntent
import io.writeopia.app.dto.ai.AiCommandIntentResponse
import io.writeopia.app.dto.ai.AiCommandRequest
import io.writeopia.connection.logger

/**
 * Asks Jev whether a free command replaces the lines the writer selected or adds to the
 * document. Replacing is the destructive answer, so it needs at least [replaceThreshold] of probability; below that the
 * text stays and the answer is added after it. The threshold comes from the
 * AI_COMMAND_REPLACE_THRESHOLD environment variable, so it can change without a new image.
 */
class CommandIntentService(
    private val client: TypeSafeClient = TypeSafeClient(),
    private val replaceThreshold: Double = replaceThresholdFromEnv(),
) {

    init {
        logger.info("Command intent configured with replace threshold {}", replaceThreshold)
    }

    fun isAvailable(): Boolean = client.isAvailable()

    suspend fun decide(request: AiCommandRequest): Pair<AiCommandIntentResponse, SystemOneUsage> {
        if (request.command.isBlank()) {
            return AiCommandIntentResponse(error = "The command is empty") to SystemOneUsage()
        }

        // With nothing selected there is nothing to replace: the answer can only be added.
        if (request.selectedText.isBlank()) {
            return AiCommandIntentResponse(intent = AiCommandIntent.ADD) to SystemOneUsage()
        }

        if (!isAvailable()) {
            return AiCommandIntentResponse(
                error = "The command intent is not configured. Set TYPESAFE_API_KEY."
            ) to SystemOneUsage()
        }

        val started = System.currentTimeMillis()
        val questions = mapOf(CommandIntentQuestions.QUESTION_ID to CommandIntentQuestions.question())

        return when (val result = client.ask(CommandIntentQuestions.stateFor(request), questions)) {
            is TypeSafeResult.Success -> {
                val answer = result.response.answers[CommandIntentQuestions.QUESTION_ID]
                val replaceProbability = answer?.probabilities?.get(CommandIntentQuestions.REPLACE)
                    ?: if (answer?.choice == CommandIntentQuestions.REPLACE) 1.0 else 0.0
                val intent = if (replaceProbability >= replaceThreshold) {
                    AiCommandIntent.REPLACE
                } else {
                    AiCommandIntent.ADD
                }

                logger.info(
                    "Command intent in {} ms: {} (replace {}%)",
                    System.currentTimeMillis() - started,
                    intent,
                    (replaceProbability * 100).toInt()
                )

                AiCommandIntentResponse(
                    intent = intent,
                    replaceProbability = replaceProbability
                ) to (result.response.usage ?: SystemOneUsage())
            }

            is TypeSafeResult.Failure -> {
                logger.warn(
                    "Command intent failed in {} ms: {}",
                    System.currentTimeMillis() - started,
                    result.message
                )
                AiCommandIntentResponse(error = result.message) to SystemOneUsage()
            }
        }
    }

    companion object {
        const val THRESHOLD_ENV = "AI_COMMAND_REPLACE_THRESHOLD"
        const val DEFAULT_REPLACE_THRESHOLD = 0.5

        fun replaceThresholdFromEnv(value: String? = System.getenv(THRESHOLD_ENV)): Double =
            value?.trim()?.toDoubleOrNull()?.takeIf { it in 0.0..1.0 } ?: DEFAULT_REPLACE_THRESHOLD
    }
}
