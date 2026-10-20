package io.writeopia.api.ai.command

import io.writeopia.app.dto.ai.AiCommandIntent
import io.writeopia.app.dto.ai.AiCommandRequest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AiCommandPromptTest {

    private val request = AiCommandRequest(
        command = "make it shorter",
        targetText = "A long paragraph about the weather.",
        documentTitle = "Diary",
        textBefore = "Monday.",
        textAfter = "Tuesday.",
    )

    @Test
    fun `a replacement asks for the new version of the target only`() {
        val prompt = AiCommandPrompt.forRequest(request, AiCommandIntent.REPLACE)

        assertContains(prompt, "new version of it, and nothing else")
        assertContains(prompt, "TARGET TEXT:\n```\nA long paragraph about the weather.")
        assertContains(prompt, "TEXT BEFORE (for context only):\n```\nMonday.")
        assertContains(prompt, "TEXT AFTER (for context only):\n```\nTuesday.")
        assertContains(prompt, "DOCUMENT TITLE: Diary")
        assertTrue(prompt.trimEnd().endsWith("COMMAND: make it shorter"))
    }

    @Test
    fun `an addition goes after the target`() {
        val prompt = AiCommandPrompt.forRequest(request, AiCommandIntent.ADD)

        assertContains(prompt, "inserts right after the TARGET TEXT")
        assertFalse(prompt.contains("takes the place of the target text"))
    }

    @Test
    fun `without a target the addition goes to the end of the document`() {
        val prompt = AiCommandPrompt.forRequest(
            request.copy(targetText = "", textAfter = ""),
            AiCommandIntent.ADD
        )

        assertContains(prompt, "inserts at the end of the document")
        assertFalse(prompt.contains("TARGET TEXT"))
        assertFalse(prompt.contains("TEXT AFTER"))
    }

    @Test
    fun `the context is cut but the target is not`() {
        val long = "x".repeat(AiCommandPrompt.CONTEXT_LIMIT + 100)
        val prompt = AiCommandPrompt.forRequest(
            request.copy(targetText = long, textBefore = "a$long", textAfter = "${long}z"),
            AiCommandIntent.REPLACE
        )

        assertContains(prompt, "TARGET TEXT:\n```\n$long")
        assertFalse(prompt.contains("```\na" + "x".repeat(AiCommandPrompt.CONTEXT_LIMIT)))
        assertFalse(prompt.contains("x".repeat(AiCommandPrompt.CONTEXT_LIMIT) + "z"))
    }
}
