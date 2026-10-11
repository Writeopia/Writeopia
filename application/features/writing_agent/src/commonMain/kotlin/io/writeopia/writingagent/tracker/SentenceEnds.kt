package io.writeopia.writingagent.tracker

/**
 * Counts the sentence ends of a paragraph: ".", "!", "?" and ":". The colon counts because it
 * announces what comes next (a list, a snippet, a quote), which is exactly when a suggestion
 * helps. The agent only asks for suggestions again when this count changes, so it is consulted
 * once per sentence instead of once per key.
 */
object SentenceEnds {

    private val enders = setOf('.', '!', '?', ':')

    fun count(text: String?): Int = text?.count { it in enders } ?: 0
}
