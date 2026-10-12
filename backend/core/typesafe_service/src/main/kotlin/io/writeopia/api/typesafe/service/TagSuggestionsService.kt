package io.writeopia.api.typesafe.service

import io.writeopia.api.typesafe.client.TypeSafeClient
import io.writeopia.api.typesafe.client.TypeSafeResult
import io.writeopia.api.typesafe.model.SystemOneUsage
import io.writeopia.app.dto.writingagent.TagSuggestionDto
import io.writeopia.app.dto.writingagent.TagSuggestionsRequest
import io.writeopia.app.dto.writingagent.TagSuggestionsResponse
import io.writeopia.connection.logger

/**
 * Tags a document: asks Jev how likely the document should carry each tag of the workspace
 * catalogue and answers with the ones at or above [threshold], best first, at most [limit]. When
 * none passes, the answer is empty and the app offers a new tag instead. The threshold is the one
 * of the writing suggestions; the limit is its own, since a document often carries several tags.
 */
class TagSuggestionsService(
    private val client: TypeSafeClient = TypeSafeClient(),
    private val threshold: Double = SuggestionSelector.thresholdFromEnv(),
    private val limit: Int = limitFromEnv(),
) {

    fun isAvailable(): Boolean = client.isAvailable()

    suspend fun suggest(request: TagSuggestionsRequest): Pair<TagSuggestionsResponse, SystemOneUsage> {
        if (!isAvailable()) {
            return TagSuggestionsResponse(
                error = "The writing agent is not configured. Set TYPESAFE_API_KEY."
            ) to SystemOneUsage()
        }

        val candidates = TagSuggestionQuestions.candidates(request)
        if (candidates.isEmpty() || (request.documentText.isBlank() && request.documentTitle.isBlank())) {
            return TagSuggestionsResponse() to SystemOneUsage()
        }

        val state = TagSuggestionQuestions.stateFor(request)
        val questions = TagSuggestionQuestions.questionsFor(request)

        logger.info("Tag agent asking Jev about {} tags", questions.size)
        val started = System.currentTimeMillis()

        return when (val result = client.ask(state, questions)) {
            is TypeSafeResult.Success -> {
                val probabilities = result.response.answers.mapNotNull { (id, answer) ->
                    val tag = TagSuggestionQuestions.indexOf(id)?.let(candidates::getOrNull)
                    val probability = answer.noul
                    if (tag != null && probability != null) tag to probability else null
                }

                logger.info(
                    "Tag agent answers in {} ms: {}",
                    System.currentTimeMillis() - started,
                    probabilities.joinToString { (tag, p) -> "${tag.name}=${(p * 100).toInt()}%" }
                )

                val suggestions = probabilities
                    .filter { (_, probability) -> probability >= threshold }
                    .sortedByDescending { (_, probability) -> probability }
                    .take(limit)
                    .map { (tag, probability) -> TagSuggestionDto(tag.id, probability) }

                TagSuggestionsResponse(suggestions = suggestions) to (result.response.usage ?: SystemOneUsage())
            }

            is TypeSafeResult.Failure -> {
                logger.warn("Tag agent failed in {} ms: {}", System.currentTimeMillis() - started, result.message)
                TagSuggestionsResponse(error = result.message) to SystemOneUsage()
            }
        }
    }

    companion object {
        const val DEFAULT_LIMIT = 5
        const val LIMIT_ENV = "TAG_SUGGESTIONS_LIMIT"

        /** The limit from the TAG_SUGGESTIONS_LIMIT environment variable, or [DEFAULT_LIMIT]. */
        fun limitFromEnv(value: String? = System.getenv(LIMIT_ENV)): Int =
            value?.trim()?.toIntOrNull()?.takeIf { it > 0 } ?: DEFAULT_LIMIT
    }
}
