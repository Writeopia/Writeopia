package io.writeopia.sqldelight.fts

/**
 * Turns what a user typed into a `MATCH` expression for the FTS4 table of the story steps.
 *
 * Only runs of letters and digits are kept, lowercased (FTS4 reads `OR`, `AND`, `NOT` and
 * `NEAR` in capitals as operators), each one matched by prefix (`tok*`), all of them required
 * (FTS4's implicit AND between terms). With quotes, `-`, `*` and the other operator characters
 * gone, the expression is always valid, whatever the user typed. The backend builds its
 * `tsquery` from the same tokens, so the two searches agree on what matches.
 */
object FtsQuery {

    private val separators = Regex("[^\\p{L}\\p{N}]+")

    /** The tokens of [raw]: letters and digits only, lowercased, in order, at most [maxTokens] of them. */
    fun tokens(raw: String, maxTokens: Int = MAX_TOKENS): List<String> =
        raw.split(separators)
            .filter { token -> token.isNotEmpty() }
            .map { token -> token.take(MAX_TOKEN_LENGTH).lowercase() }
            .take(maxTokens)

    /** The MATCH expression for [raw], or null when it holds no letter or digit (then there is nothing to search). */
    fun fts4Match(raw: String, maxTokens: Int = MAX_TOKENS): String? =
        tokens(raw, maxTokens)
            .takeIf { tokens -> tokens.isNotEmpty() }
            ?.joinToString(" ") { token -> "$token*" }

    /** True when [text] holds every token of [raw] (by prefix, case-insensitive). */
    fun matches(text: String, raw: String): Boolean {
        val tokens = tokens(raw)
        if (tokens.isEmpty()) return false
        val words = text.split(separators).filter { word -> word.isNotEmpty() }.map { word -> word.lowercase() }
        return tokens.all { token -> words.any { word -> word.startsWith(token) } }
    }

    const val MAX_TOKENS = 8
    const val MAX_TOKEN_LENGTH = 64
}
