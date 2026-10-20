package io.writeopia.api.ai.command

import io.writeopia.app.dto.ai.AiCommandIntent
import io.writeopia.app.dto.ai.AiCommandRequest

/**
 * The prompt Gemini answers for a free command. It changes with the intent Jev decided: a
 * replacement must be only the new version of the target text, an addition must be only the new
 * content, so the editor can put the answer in the document as it is.
 */
object AiCommandPrompt {

    /** The text around the target is cut here, the target and the command never are. */
    const val CONTEXT_LIMIT = 12_000

    fun forRequest(request: AiCommandRequest, intent: AiCommandIntent): String {
        val before = request.textBefore.takeLast(CONTEXT_LIMIT)
        val after = request.textAfter.take(CONTEXT_LIMIT)
        val hasTarget = request.targetText.isNotBlank()

        return buildString {
            appendLine("You are a writing assistant inside a text editor. The writer typed a command about their document.")
            appendLine()

            when (intent) {
                AiCommandIntent.REPLACE -> {
                    appendLine("Apply the command to the TARGET TEXT and answer with the new version of it, and nothing else.")
                    appendLine("The new version takes the place of the target text in the document, so keep what the command does not ask to change: the language, the meaning, the formatting and the line breaks between paragraphs.")
                }

                AiCommandIntent.ADD -> {
                    if (hasTarget) {
                        appendLine("Answer the command with new content, which the editor inserts right after the TARGET TEXT.")
                        appendLine("Do not repeat the target text and do not explain what you did: answer with the new content only.")
                    } else {
                        appendLine("Answer the command with new content, which the editor inserts at the end of the document.")
                        appendLine("Do not repeat the document and do not explain what you did: answer with the new content only.")
                    }
                }
            }

            appendLine("Use the language of the document. Use Markdown only for headings (#), bullet lists (-), checklists (- [ ]) and bold (**), never for tables or code fences unless the command asks for code.")
            appendLine()

            if (request.documentTitle.isNotBlank()) {
                appendLine("DOCUMENT TITLE: ${request.documentTitle}")
                appendLine()
            }

            if (before.isNotBlank()) {
                appendLine("TEXT BEFORE (for context only):")
                appendLine("```")
                appendLine(before)
                appendLine("```")
                appendLine()
            }

            if (hasTarget) {
                appendLine("TARGET TEXT:")
                appendLine("```")
                appendLine(request.targetText)
                appendLine("```")
                appendLine()
            }

            if (after.isNotBlank()) {
                appendLine("TEXT AFTER (for context only):")
                appendLine("```")
                appendLine(after)
                appendLine("```")
                appendLine()
            }

            appendLine("COMMAND: ${request.command.trim()}")
        }
    }
}
