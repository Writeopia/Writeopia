package io.writeopia.api.ai.service

import io.writeopia.api.ai.routing.orImageCost
import io.writeopia.api.genai.model.TokenUsage
import io.writeopia.app.dto.ai.AiImageRequest
import io.writeopia.sdk.serialization.request.HeaderImageGenerateRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ImagePromptTest {

    @Test
    fun `the prompt carries the title, the paragraph and the document`() {
        val prompt = ImagePrompt.forParagraph(
            AiImageRequest(
                paragraph = "The lighthouse stood alone on the cliff.",
                documentTitle = "Coastal Journal",
                documentText = "Day one. The lighthouse stood alone on the cliff. Day two..."
            )
        )

        assertTrue(prompt.contains("titled \"Coastal Journal\""))
        assertTrue(prompt.contains("Paragraph:\nThe lighthouse stood alone on the cliff."))
        assertTrue(prompt.contains("Document:\nDay one."))
        assertTrue(prompt.contains("Do not write any text"))
    }

    @Test
    fun `a document that is too long is cut`() {
        val prompt = ImagePrompt.forParagraph(
            AiImageRequest(paragraph = "A tree.", documentText = "x".repeat(ImagePrompt.DOCUMENT_LIMIT + 500))
        )

        assertEquals(ImagePrompt.DOCUMENT_LIMIT, prompt.substringAfter("Document:\n").length)
    }

    @Test
    fun `without a title or document only the paragraph is sent`() {
        val prompt = ImagePrompt.forParagraph(AiImageRequest(paragraph = "A tree."))

        assertFalse(prompt.contains("titled"))
        assertFalse(prompt.contains("Document:"))
        assertTrue(prompt.endsWith("Paragraph:\nA tree."))
    }

    @Test
    fun `reported tokens are kept and missing ones become the image cost`() {
        assertEquals(TokenUsage(10, 1290, 1300), TokenUsage(10, 1290, 1300).orImageCost(cost = 500))
        assertEquals(TokenUsage(0, 500, 500), TokenUsage(0, 0, 0).orImageCost(cost = 500))
    }

    @Test
    fun `the header prompt carries the title and the document, without text in the picture`() {
        val prompt = ImagePrompt.forHeader(
            HeaderImageGenerateRequest(
                workspaceId = "workspace",
                documentTitle = "Coastal Journal",
                documentText = "Day one. The lighthouse stood alone on the cliff."
            )
        )

        assertTrue(prompt.contains("titled \"Coastal Journal\""))
        assertTrue(prompt.contains("Document:\nDay one."))
        assertTrue(prompt.contains("Do not write any text"))
    }

    @Test
    fun `a header for an empty document asks only for the cover`() {
        val prompt = ImagePrompt.forHeader(HeaderImageGenerateRequest(workspaceId = "workspace"))

        assertFalse(prompt.contains("titled"))
        assertFalse(prompt.contains("Document:"))
    }

    @Test
    fun `a header document that is too long is cut`() {
        val prompt = ImagePrompt.forHeader(
            HeaderImageGenerateRequest(workspaceId = "w", documentText = "x".repeat(ImagePrompt.DOCUMENT_LIMIT + 10))
        )

        assertEquals(ImagePrompt.DOCUMENT_LIMIT, prompt.substringAfter("Document:\n").length)
    }
}
