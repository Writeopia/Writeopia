@file:OptIn(ExperimentalTime::class)

package io.writeopia.editor.features.presentation.repository

import io.writeopia.LocalAiRepository
import io.writeopia.core.presentations.PresentationException
import io.writeopia.core.presentations.PresentationGenerator
import io.writeopia.core.presentations.PresentationsStore
import io.writeopia.sdk.import.markdown.PresentationMarkdownParser
import io.writeopia.sdk.models.presentation.Presentation
import io.writeopia.sdk.models.presentation.PresentationPrompt
import io.writeopia.sdk.models.utils.ResultData
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.currentCoroutineContext
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Generates a presentation with the AI on this machine (Ollama): the same prompt as the
 * backend, the Markdown read by the shared parser and the result saved in the local store.
 */
class LocalPresentationGenerator(
    private val localAiRepository: LocalAiRepository,
    private val store: PresentationsStore,
    private val userId: suspend () -> String,
    private val documentTitle: () -> String,
    private val documentMarkdown: () -> String
) : PresentationGenerator {

    override suspend fun generatePresentation(documentId: String, workspaceId: String): ResultData<Presentation> =
        try {
            val userId = userId()
            val model = localAiRepository.getSelectedModel(userId)
                ?: throw PresentationException("Pick a model of the local AI first.")
            val url = localAiRepository.getConfiguredUrl(userId)
                ?: throw PresentationException("The local AI has no address configured.")

            val document = documentMarkdown().take(DOCUMENT_LIMIT)
            val answer = localAiRepository.generateReply(model, PresentationPrompt.forDocument(document), url)
            currentCoroutineContext().ensureActive()

            val slides = PresentationMarkdownParser.parse(answer)
            if (slides.isEmpty()) throw PresentationException("The AI didn't return any slide.")

            val presentation = Presentation(
                documentId = documentId,
                workspaceId = workspaceId,
                userId = userId,
                title = slides.first().title.ifBlank { documentTitle() },
                createdAt = Clock.System.now(),
                slides = slides
            )
            when (val saved = store.savePresentation(presentation)) {
                is ResultData.Error -> ResultData.Error(saved.exception)
                else -> ResultData.Complete(presentation)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ResultData.Error(e)
        }

    companion object {
        /** Characters of the document sent to the AI, so a huge document doesn't time out. */
        const val DOCUMENT_LIMIT = 20_000
    }
}
