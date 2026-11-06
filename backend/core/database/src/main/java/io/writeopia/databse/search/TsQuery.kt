package io.writeopia.databse.search

/**
 * Turns what a user typed into a `tsquery` string for `to_tsquery('simple', ...)`.
 *
 * Only runs of letters and digits are kept, each one quoted and matched by prefix
 * (`'tok':*`), and all of them are required (`&`). With the operator characters gone and every
 * lexeme quoted, `to_tsquery` can't fail on the input, whatever the user typed. The `simple`
 * configuration lowercases the lexemes (Unicode included) and has no stop words, so every token
 * counts and documents in any language are matched the same way.
 */
object TsQuery {

    private val separators = Regex("[^\\p{L}\\p{N}]+")

    /** The tokens of [raw]: letters and digits only, in order, at most [maxTokens] of them. */
    fun tokens(raw: String, maxTokens: Int = MAX_TOKENS): List<String> =
        raw.split(separators)
            .filter { token -> token.isNotEmpty() }
            .map { token -> token.take(MAX_TOKEN_LENGTH) }
            .take(maxTokens)

    /** The tsquery for [raw], or null when it holds no letter or digit (then there is nothing to search). */
    fun build(raw: String, maxTokens: Int = MAX_TOKENS): String? =
        tokens(raw, maxTokens)
            .takeIf { tokens -> tokens.isNotEmpty() }
            ?.joinToString(" & ") { token -> "'$token':*" }

    const val MAX_TOKENS = 8
    const val MAX_TOKEN_LENGTH = 64
}
