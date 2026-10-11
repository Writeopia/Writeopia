package io.writeopia.api.ai.service

import io.writeopia.app.dto.ai.AiImageRequest

/**
 * The prompt of the image model for a paragraph. The whole document goes with it, cut at
 * [DOCUMENT_LIMIT] characters, so the picture fits the subject and tone of the text and not only
 * the few words of the paragraph.
 */
object ImagePrompt {
    const val DOCUMENT_LIMIT = 20_000

    fun forParagraph(request: AiImageRequest): String {
        val title = request.documentTitle.trim()
        val document = request.documentText.trim().take(DOCUMENT_LIMIT)

        return buildString {
            append("Create one illustration for the paragraph below, taken from a document")
            if (title.isNotEmpty()) append(" titled \"").append(title).append('"')
            append(". ")
            append("Depict what the paragraph describes, in a clean editorial style that fits a written ")
            append("document. Do not write any text, letters, numbers or watermarks in the image. ")
            append("Use the rest of the document only to understand the subject, the setting and the tone.")
            append("\n\nParagraph:\n").append(request.paragraph.trim())
            if (document.isNotEmpty()) append("\n\nDocument:\n").append(document)
        }
    }
}
