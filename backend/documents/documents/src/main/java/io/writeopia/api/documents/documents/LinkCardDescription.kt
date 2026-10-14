package io.writeopia.api.documents.documents

/**
 * The description a link card shows under its address: what the AI answered, tidied and cut to
 * [MAX_WORDS] words.
 */
object LinkCardDescription {

    const val MAX_WORDS = 60

    /**
     * The AI prompt for the page at [url].
     */
    fun prompt(url: String): String =
        "Visit the web page at $url and describe what it is about in at most $MAX_WORDS words. " +
            "Answer with one plain paragraph: no title, no markdown, no quotes, no preamble. " +
            "Write in the language of the page. If the page can not be read, describe what the " +
            "address suggests the page is about in the same way."

    /**
     * [answer] as one paragraph of at most [MAX_WORDS] words, or null when the answer is blank.
     */
    fun tidy(answer: String?): String? {
        if (answer.isNullOrBlank()) return null

        val words = answer
            .replace(Regex("\\s+"), " ")
            .trim()
            .trim('"', '“', '”', '\'')
            .split(' ')
            .filter { it.isNotBlank() }

        if (words.isEmpty()) return null

        return words.take(MAX_WORDS).joinToString(" ")
    }
}
