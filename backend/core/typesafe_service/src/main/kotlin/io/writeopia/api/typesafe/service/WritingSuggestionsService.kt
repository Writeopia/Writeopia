package io.writeopia.api.typesafe.service

import io.writeopia.api.typesafe.client.TypeSafeClient
import io.writeopia.api.typesafe.client.TypeSafeResult
import io.writeopia.api.typesafe.model.SystemOneUsage
import io.writeopia.app.dto.writingagent.WritingSuggestionAction
import io.writeopia.app.dto.writingagent.WritingSuggestionScope
import io.writeopia.app.dto.writingagent.WritingSuggestionsRequest
import io.writeopia.app.dto.writingagent.WritingSuggestionsResponse
import io.writeopia.connection.logger

/**
 * The writing agent: asks Jev which of the editor's actions the writer wants, and keeps only the
 * confident ones. The threshold and the limit live here, in code, so they can change without
 * touching the questions.
 */
class WritingSuggestionsService(
    private val client: TypeSafeClient = TypeSafeClient(),
    private val threshold: Double = SuggestionSelector.DEFAULT_THRESHOLD,
    private val limit: Int = SuggestionSelector.DEFAULT_LIMIT,
) {

    fun isAvailable(): Boolean = client.isAvailable()

    suspend fun suggest(request: WritingSuggestionsRequest): Pair<WritingSuggestionsResponse, SystemOneUsage> {
        if (!isAvailable()) {
            return WritingSuggestionsResponse(
                error = "The writing agent is not configured. Set TYPESAFE_API_KEY."
            ) to SystemOneUsage()
        }

        if (request.scope == WritingSuggestionScope.WRITING && request.text.isBlank()) {
            return WritingSuggestionsResponse() to SystemOneUsage()
        }

        if (request.scope == WritingSuggestionScope.DOCUMENT_OPENED && request.documentText.isNullOrBlank()) {
            return WritingSuggestionsResponse() to SystemOneUsage()
        }

        val state = WritingSuggestionQuestions.stateFor(request)
        val questions = WritingSuggestionQuestions.questionsFor(request.scope)

        return when (val result = client.ask(state, questions)) {
            is TypeSafeResult.Success -> {
                val probabilities = result.response.answers.mapNotNull { (id, answer) ->
                    val action = WritingSuggestionAction.entries.firstOrNull { it.name == id }
                    val probability = answer.noul
                    if (action != null && probability != null) action to probability else null
                }.toMap()

                logger.info(
                    "Writing agent answers for {}: {}",
                    request.scope,
                    probabilities.entries.joinToString { (action, p) -> "$action=${(p * 100).toInt()}%" }
                )

                val suggestions = SuggestionSelector.select(probabilities, threshold, limit)
                WritingSuggestionsResponse(suggestions = suggestions) to (result.response.usage ?: SystemOneUsage())
            }

            is TypeSafeResult.Failure -> {
                WritingSuggestionsResponse(error = result.message) to SystemOneUsage()
            }
        }
    }
}
