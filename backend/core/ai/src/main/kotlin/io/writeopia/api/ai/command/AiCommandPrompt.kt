package io.writeopia.api.ai.command

import io.writeopia.app.dto.ai.AiCommandIntent
import io.writeopia.app.dto.ai.AiCommandRequest

/**
 * The prompt Gemini answers for a free command: the document, the lines the writer selected
 * when there are any, and the command. It changes with the intent: a replacement must be only
 * the new version of the selected lines, an addition only the new content, and a rewrite (no
 * selection) the whole document again, so the editor can put the answer in the document as it
 * is.
 */
object AiCommandPrompt {

    /** The document is cut here, around the selection. The selection and the command never are. */
    const val DOCUMENT_LIMIT = 24_000

    fun forRequest(request: AiCommandRequest, intent: AiCommandIntent): String {
        val hasSelection = request.selectedText.isNotBlank()
        val document = request.documentAround(DOCUMENT_LIMIT)

        return buildString {
            appendLine("You are a writing assistant inside a text editor. The writer typed a command about their document.")
            appendLine()

            when {
                intent == AiCommandIntent.REWRITE -> {
                    appendLine("The DOCUMENT is in Markdown, one paragraph per line. A line like [[writeopia-block-3]] stands for a block you cannot see (a table, an image, a drawing): keep every such line exactly as it is, in its place.")
                    appendLine("Apply the command to the document and answer with the WHOLE document again, in the same Markdown, one paragraph per line, keeping every line the command does not affect exactly as it is, in the same order.")
                    appendLine("Do not explain what you did, do not add a title, and do not wrap the answer in a code fence: answer with the document only.")
                }

                intent == AiCommandIntent.REPLACE && hasSelection -> {
                    appendLine("Apply the command to the SELECTED LINES and answer with the new version of them, and nothing else.")
                    appendLine("The new version takes the place of the selected lines in the document, so keep what the command does not ask to change: the language, the meaning, the formatting and the line breaks between paragraphs.")
                }

                hasSelection -> {
                    appendLine("Answer the command with new content, which the editor inserts right after the SELECTED LINES.")
                    appendLine("Do not repeat the selected lines and do not explain what you did: answer with the new content only.")
                }

                else -> {
                    appendLine("Answer the command with new content, which the editor inserts in the document where the writer is.")
                    appendLine("Do not repeat the document and do not explain what you did: answer with the new content only.")
                }
            }

            appendLine("Use the language of the document. Use Markdown only for headings (#), bullet lists (-), checklists (- [ ]) and bold (**), never for tables or code fences unless the command asks for code.")
            appendLine()

            if (request.documentTitle.isNotBlank()) {
                appendLine("DOCUMENT TITLE: ${request.documentTitle}")
                appendLine()
            }

            if (document.isNotBlank()) {
                appendLine("DOCUMENT:")
                appendLine("```")
                appendLine(document)
                appendLine("```")
                appendLine()
            }

            if (hasSelection) {
                appendLine("SELECTED LINES:")
                appendLine("```")
                appendLine(request.selectedText)
                appendLine("```")
                appendLine()
            }

            appendLine("COMMAND: ${request.command.trim()}")
        }
    }
}
