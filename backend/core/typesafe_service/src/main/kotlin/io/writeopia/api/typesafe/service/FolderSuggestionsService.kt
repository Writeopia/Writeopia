package io.writeopia.api.typesafe.service

import io.writeopia.api.typesafe.client.TypeSafeClient
import io.writeopia.api.typesafe.client.TypeSafeResult
import io.writeopia.api.typesafe.model.SystemOneUsage
import io.writeopia.app.dto.writingagent.FolderCandidate
import io.writeopia.app.dto.writingagent.FolderSuggestionDto
import io.writeopia.app.dto.writingagent.FolderSuggestionsRequest
import io.writeopia.app.dto.writingagent.FolderSuggestionsResponse
import io.writeopia.connection.logger

/**
 * Files a document that sits in the root folder: asks Jev how likely the document belongs to
 * each candidate folder and answers with the ones at or above [threshold], best first, at most
 * [limit]. The candidates are the ones the backend ranked for the document, else the ones the
 * app sent. When none passes, the answer is empty and the app only offers a new folder. The
 * threshold and the limit are the ones of the writing suggestions.
 */
class FolderSuggestionsService(
    private val client: TypeSafeClient = TypeSafeClient(),
    private val threshold: Double = SuggestionSelector.thresholdFromEnv(),
    private val limit: Int = SuggestionSelector.limitFromEnv(),
) {

    fun isAvailable(): Boolean = client.isAvailable()

    suspend fun suggest(
        request: FolderSuggestionsRequest,
        folders: List<FolderCandidate> = request.folders,
    ): Pair<FolderSuggestionsResponse, SystemOneUsage> {
        if (!isAvailable()) {
            return FolderSuggestionsResponse(
                error = "The writing agent is not configured. Set TYPESAFE_API_KEY."
            ) to SystemOneUsage()
        }

        val candidates = FolderSuggestionQuestions.candidates(folders)
        if (candidates.isEmpty() || (request.documentText.isBlank() && request.documentTitle.isBlank())) {
            return FolderSuggestionsResponse() to SystemOneUsage()
        }

        val state = FolderSuggestionQuestions.stateFor(request, candidates)
        val questions = FolderSuggestionQuestions.questionsFor(request, candidates)

        logger.info("Folder agent asking Jev about {} folders", questions.size)
        val started = System.currentTimeMillis()

        return when (val result = client.ask(state, questions)) {
            is TypeSafeResult.Success -> {
                val probabilities = result.response.answers.mapNotNull { (id, answer) ->
                    val folder = FolderSuggestionQuestions.indexOf(id)?.let(candidates::getOrNull)
                    val probability = answer.noul
                    if (folder != null && probability != null) folder to probability else null
                }

                logger.info(
                    "Folder agent answers in {} ms: {}",
                    System.currentTimeMillis() - started,
                    probabilities.joinToString { (folder, p) -> "${folder.name}=${(p * 100).toInt()}%" }
                )

                val suggestions = probabilities
                    .filter { (_, probability) -> probability >= threshold }
                    .sortedByDescending { (_, probability) -> probability }
                    .take(limit)
                    .map { (folder, probability) -> FolderSuggestionDto(folder.id, probability, folderName = folder.name) }

                FolderSuggestionsResponse(suggestions = suggestions) to (result.response.usage ?: SystemOneUsage())
            }

            is TypeSafeResult.Failure -> {
                logger.warn("Folder agent failed in {} ms: {}", System.currentTimeMillis() - started, result.message)
                FolderSuggestionsResponse(error = result.message) to SystemOneUsage()
            }
        }
    }
}
