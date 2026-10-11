package io.writeopia.api.typesafe.service

import io.writeopia.app.dto.writingagent.WritingSuggestionAction
import io.writeopia.app.dto.writingagent.WritingSuggestionDto

/**
 * Picks what to show the user: the actions at or above [threshold], best first, at most [limit].
 * Nothing is shown when nothing passes the threshold.
 */
object SuggestionSelector {

    const val DEFAULT_THRESHOLD = 0.8
    const val DEFAULT_LIMIT = 3

    const val THRESHOLD_ENV = "WRITING_SUGGESTIONS_THRESHOLD"
    const val LIMIT_ENV = "WRITING_SUGGESTIONS_LIMIT"

    /**
     * The threshold from the WRITING_SUGGESTIONS_THRESHOLD environment variable, a probability
     * from 0 to 1, or [DEFAULT_THRESHOLD] when it is missing or not a valid probability.
     */
    fun thresholdFromEnv(value: String? = System.getenv(THRESHOLD_ENV)): Double =
        value?.trim()?.toDoubleOrNull()?.takeIf { it in 0.0..1.0 } ?: DEFAULT_THRESHOLD

    /** The limit from the WRITING_SUGGESTIONS_LIMIT environment variable, or [DEFAULT_LIMIT]. */
    fun limitFromEnv(value: String? = System.getenv(LIMIT_ENV)): Int =
        value?.trim()?.toIntOrNull()?.takeIf { it > 0 } ?: DEFAULT_LIMIT

    fun select(
        probabilities: Map<WritingSuggestionAction, Double>,
        threshold: Double = DEFAULT_THRESHOLD,
        limit: Int = DEFAULT_LIMIT,
    ): List<WritingSuggestionDto> =
        probabilities.entries
            .filter { (_, probability) -> probability >= threshold }
            .sortedByDescending { (_, probability) -> probability }
            .take(limit)
            .map { (action, probability) -> WritingSuggestionDto(action, probability) }
}
