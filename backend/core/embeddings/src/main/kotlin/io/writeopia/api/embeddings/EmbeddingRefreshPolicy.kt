package io.writeopia.api.embeddings

import kotlin.math.ceil
import kotlin.math.max

/**
 * Decides when a document is embedded again. Typing is synced to the backend every few hundred
 * milliseconds, so an embedding per sync would cost a model call per keystroke. Instead a
 * document is embedded again only after a big change, and at most once per [cooldownMillis].
 *
 * A change is big when the title changed, or when the body changed by at least
 * [changedRatio] of the text that was embedded (and at least [minChangedChars], so a short
 * document is not re-embedded on every word). The first embedding, and one with another
 * model, is never held back.
 */
class EmbeddingRefreshPolicy(
    val minChangedChars: Int = DEFAULT_MIN_CHANGED_CHARS,
    val changedRatio: Double = DEFAULT_CHANGED_RATIO,
    val cooldownMillis: Long = DEFAULT_COOLDOWN_MILLIS,
) {

    /** True when the document should be embedded now, given what was embedded before (null for never). */
    fun shouldReembed(
        previous: StoredDocumentEmbedding?,
        title: String,
        body: String,
        model: String,
        nowMillis: Long,
    ): Boolean {
        if (title.isBlank() && body.isBlank()) return false
        if (previous == null || previous.model != model) return true
        if (!cooldownElapsed(previous.embeddedAt, nowMillis)) return false
        if (title.trim() != previous.sourceTitle.trim()) return true
        if (body == previous.sourceText) return false

        val threshold = max(minChangedChars, ceil(changedRatio * previous.sourceText.length).toInt())
        return changedChars(previous.sourceText, body) >= threshold
    }

    /** The cheap check, made before loading a document: has the cooldown since [sinceMillis] (the last embedding or check) passed? */
    fun cooldownElapsed(sinceMillis: Long, nowMillis: Long): Boolean = nowMillis - sinceMillis >= cooldownMillis

    companion object {
        const val DEFAULT_MIN_CHANGED_CHARS = 200
        const val DEFAULT_CHANGED_RATIO = 0.15
        const val DEFAULT_COOLDOWN_MILLIS = 5 * 60_000L

        /**
         * How many characters changed between two texts, counted by lines: the characters of the
         * lines only [old] has, against those only [new] has, whichever is more. Lines that moved
         * don't count, so reordering paragraphs is not a change of content.
         */
        fun changedChars(old: String, new: String): Int {
            if (old == new) return 0
            val oldCounts = old.lines().groupingBy { it }.eachCount()
            val newCounts = new.lines().groupingBy { it }.eachCount()

            var removed = 0
            var added = 0
            (oldCounts.keys + newCounts.keys).forEach { line ->
                val difference = (oldCounts[line] ?: 0) - (newCounts[line] ?: 0)
                when {
                    difference > 0 -> removed += difference * line.length
                    difference < 0 -> added += -difference * line.length
                }
            }

            return max(removed, added)
        }
    }
}
