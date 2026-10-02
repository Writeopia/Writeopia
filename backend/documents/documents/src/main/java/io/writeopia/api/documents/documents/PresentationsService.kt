@file:OptIn(ExperimentalTime::class)

package io.writeopia.api.documents.documents

import io.writeopia.api.documents.documents.repository.getDocumentWithContentById
import io.writeopia.api.documents.documents.repository.savePresentation
import io.writeopia.api.genai.service.GenAiService
import io.writeopia.sdk.export.DocumentToMarkdown
import io.writeopia.sdk.import.markdown.PresentationMarkdownParser
import io.writeopia.sdk.models.presentation.Presentation
import io.writeopia.sql.WriteopiaDbBackend
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Generates presentations from documents: the AI writes the slides as Markdown, the shared
 * parser reads them, and the result is saved before it's sent to the client. The apps only show
 * what comes back.
 */
object PresentationsService {

    sealed class GenerateResult {
        data class Success(val presentation: Presentation) : GenerateResult()

        /** The document isn't in the workspace (maps to HTTP 404). */
        data class NotFound(val message: String) : GenerateResult()

        /** The document can't be turned into slides (maps to HTTP 400). */
        data class InvalidRequest(val message: String) : GenerateResult()

        /** The AI or the parsing failed (maps to HTTP 500). */
        data class Error(val message: String) : GenerateResult()

        data object GenAiUnavailable : GenerateResult()
    }

    suspend fun generatePresentation(
        documentId: String,
        workspaceId: String,
        userId: String,
        model: String?,
        genAiService: GenAiService,
        writeopiaDb: WriteopiaDbBackend
    ): GenerateResult {
        if (!genAiService.isAvailable()) {
            return GenerateResult.GenAiUnavailable
        }

        val document = writeopiaDb.getDocumentWithContentById(documentId, workspaceId)
            ?: return GenerateResult.NotFound("Document not found: $documentId")

        val markdown = DocumentToMarkdown.parse(document.content).trim()
        if (markdown.isBlank()) {
            return GenerateResult.InvalidRequest("The document has no text to make a presentation from")
        }
        if (markdown.length > MAX_DOCUMENT_LENGTH) {
            return GenerateResult.InvalidRequest(
                "The document is too long for a presentation: ${markdown.length} characters. " +
                    "Maximum allowed is $MAX_DOCUMENT_LENGTH characters"
            )
        }

        val aiResponse = genAiService.generatePresentation(markdown, model)
        aiResponse.error?.let { return GenerateResult.Error(it) }
        val slidesMarkdown = aiResponse.response
        if (slidesMarkdown.isNullOrBlank()) {
            return GenerateResult.Error("AI generated an empty presentation")
        }

        val slides = PresentationMarkdownParser.parse(slidesMarkdown)
        if (slides.isEmpty()) {
            return GenerateResult.Error("AI didn't return any slide")
        }

        val presentation = Presentation(
            documentId = documentId,
            workspaceId = workspaceId,
            userId = userId,
            title = slides.first().title.ifBlank { document.title },
            createdAt = Clock.System.now(),
            slides = slides
        )
        writeopiaDb.savePresentation(presentation)

        return GenerateResult.Success(presentation)
    }

    /** Keeps a huge document from exceeding the model context. */
    private const val MAX_DOCUMENT_LENGTH = 100_000
}
