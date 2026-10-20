package io.writeopia.api.ai.command

import io.writeopia.app.dto.ai.AiCommandIntent
import io.writeopia.app.dto.ai.AiCommandRequest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AiCommandPromptTest {

    private val request = AiCommandRequest(
        command = "make it shorter",
        selectedText = "A long paragraph about the weather.",
        documentTitle = "Diary",
        documentText = "Monday.\nA long paragraph about the weather.\nTuesday.",
    )

    @Test
    fun `a replacement asks for the new version of the selected lines only`() {
        val prompt = AiCommandPrompt.forRequest(request, AiCommandIntent.REPLACE)

        assertContains(prompt, "new version of them, and nothing else")
        assertContains(prompt, "SELECTED LINES:\n```\nA long paragraph about the weather.")
        assertContains(prompt, "DOCUMENT:\n```\nMonday.\nA long paragraph about the weather.\nTuesday.")
        assertContains(prompt, "DOCUMENT TITLE: Diary")
        assertTrue(prompt.trimEnd().endsWith("COMMAND: make it shorter"))
    }

    @Test
    fun `an addition goes after the selected lines`() {
        val prompt = AiCommandPrompt.forRequest(request, AiCommandIntent.ADD)

        assertContains(prompt, "inserts right after the SELECTED LINES")
        assertFalse(prompt.contains("takes the place of the selected lines"))
    }

    @Test
    fun `without a selection the prompt does not mention one`() {
        val prompt = AiCommandPrompt.forRequest(request.copy(selectedText = ""), AiCommandIntent.ADD)

        assertContains(prompt, "inserts in the document where the writer is")
        assertFalse(prompt.contains("SELECTED LINES"))
        assertFalse(prompt.contains("selected lines"))
    }

    @Test
    fun `a replacement without a selection is written as an addition`() {
        val prompt = AiCommandPrompt.forRequest(request.copy(selectedText = ""), AiCommandIntent.REPLACE)

        assertContains(prompt, "inserts in the document where the writer is")
        assertFalse(prompt.contains("SELECTED LINES"))
    }

    @Test
    fun `a rewrite asks for the whole document back with the blocks in place`() {
        val prompt = AiCommandPrompt.forRequest(
            request.copy(selectedText = "", documentText = "Monday.\n[[writeopia-block-1]]\nTuesday."),
            AiCommandIntent.REWRITE
        )

        assertContains(prompt, "answer with the WHOLE document again")
        assertContains(prompt, "keep every such line exactly as it is")
        assertContains(prompt, "DOCUMENT:\n```\nMonday.\n[[writeopia-block-1]]\nTuesday.")
        assertFalse(prompt.contains("SELECTED LINES"))
    }

    @Test
    fun `a long document is cut around the selection`() {
        val filler = "x".repeat(AiCommandPrompt.DOCUMENT_LIMIT)
        val document = "$filler\nThe selected line.\n$filler"
        val prompt = AiCommandPrompt.forRequest(
            request.copy(selectedText = "The selected line.", documentText = document),
            AiCommandIntent.REPLACE
        )

        assertContains(prompt, "The selected line.")
        assertFalse(prompt.contains(filler))
    }

    @Test
    fun `the document cut keeps the beginning when the selection is not found`() {
        val document = (1..5000).joinToString("\n") { "line $it" }
        val cut = request.copy(selectedText = "not there", documentText = document)
            .documentAround(limit = 100)

        assertEquals(document.take(100), cut)
    }
}
