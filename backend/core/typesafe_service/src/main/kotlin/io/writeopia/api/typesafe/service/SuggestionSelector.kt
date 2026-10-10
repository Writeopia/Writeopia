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
