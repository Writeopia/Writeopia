package io.writeopia.api.embeddings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EmbeddingRefreshPolicyTest {

    private val policy = EmbeddingRefreshPolicy(minChangedChars = 20, changedRatio = 0.15, cooldownMillis = 30_000)
    private val model = "fake"
    private val now = 1_000_000L

    private val body = (1..10).joinToString("\n") { "Paragraph number $it of the document, long enough to count." }

    private fun previous(
        title: String = "Title",
        text: String = body,
        embeddedAt: Long = now - 60_000,
        model: String = this.model,
    ) = StoredDocumentEmbedding(
        documentId = "doc",
        workspaceId = "ws",
        model = model,
        vector = floatArrayOf(1f),
        sourceTitle = title,
        sourceText = text,
        embeddedAt = embeddedAt,
    )

    @Test
    fun `a blank document is never embedded`() {
        assertFalse(policy.shouldReembed(null, "", "  \n", model, now))
    }

    @Test
    fun `the first embedding is not held back`() {
        assertTrue(policy.shouldReembed(null, "Title", body, model, now))
    }

    @Test
    fun `an embedding of another model is replaced at once`() {
        assertTrue(policy.shouldReembed(previous(model = "old", embeddedAt = now), "Title", body, model, now))
    }

    @Test
    fun `the same text is not embedded again`() {
        assertFalse(policy.shouldReembed(previous(), "Title", body, model, now))
    }

    @Test
    fun `a small change is not embedded again`() {
        val edited = body.replace("number 3 of", "number three of")

        assertFalse(policy.shouldReembed(previous(), "Title", edited, model, now))
    }

    @Test
    fun `a big change inside the cooldown waits`() {
        val rewritten = (1..10).joinToString("\n") { "Something else entirely, line $it, rewritten from scratch." }

        assertFalse(policy.shouldReembed(previous(embeddedAt = now - 5_000), "Title", rewritten, model, now))
    }

    @Test
    fun `a big change after the cooldown is embedded again`() {
        val rewritten = (1..10).joinToString("\n") { "Something else entirely, line $it, rewritten from scratch." }

        assertTrue(policy.shouldReembed(previous(embeddedAt = now - 30_000), "Title", rewritten, model, now))
    }

    @Test
    fun `a change of at least the ratio is big`() {
        // Two of ten similar lines replaced: ~20% of the characters.
        val edited = body.lines().mapIndexed { index, line ->
            if (index < 2) "A different line that replaces what was here before, number $index." else line
        }.joinToString("\n")

        assertTrue(policy.shouldReembed(previous(), "Title", edited, model, now))
    }

    @Test
    fun `a title change is big`() {
        assertTrue(policy.shouldReembed(previous(), "Another title", body, model, now))
    }

    @Test
    fun `a title change inside the cooldown waits too`() {
        assertFalse(policy.shouldReembed(previous(embeddedAt = now - 1_000), "Another title", body, model, now))
    }

    @Test
    fun `reordered paragraphs are not a change`() {
        val reordered = body.lines().reversed().joinToString("\n")

        assertEquals(0, EmbeddingRefreshPolicy.changedChars(body, reordered))
        assertFalse(policy.shouldReembed(previous(), "Title", reordered, model, now))
    }

    @Test
    fun `changed characters count the larger side of the difference`() {
        assertEquals(5, EmbeddingRefreshPolicy.changedChars("a\nbb\nccc", "a\nbb\nccc\nddddd"))
        assertEquals(3, EmbeddingRefreshPolicy.changedChars("a\nbb\nccc", "a\nbb"))
        assertEquals(0, EmbeddingRefreshPolicy.changedChars("same", "same"))
    }

    @Test
    fun `the minimum protects short documents from the ratio`() {
        val short = "One line."
        val edited = "One line!"

        // 9 chars changed is 100% of the text, still below the 20-char minimum.
        assertFalse(policy.shouldReembed(previous(text = short), "Title", edited, model, now))
    }
}
