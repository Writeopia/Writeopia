package io.writeopia.writingagent.tracker

/**
 * Counts the sentence ends of a paragraph: ".", "!" and "?". The agent only asks for suggestions
 * again when this count changes, so it is consulted once per sentence instead of once per key.
 */
object SentenceEnds {

    private val enders = setOf('.', '!', '?')

    fun count(text: String?): Int = text?.count { it in enders } ?: 0
}
