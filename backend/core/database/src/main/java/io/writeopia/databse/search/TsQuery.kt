package io.writeopia.databse.search

/**
 * Builds the `tsquery` string for `to_tsquery('simple', ...)` from the lexemes PostgreSQL's
 * own parser found in what the user typed (`to_tsvector('simple', text)`, see
 * [StoryStepSearchStore.lexemes]): the words are split the way the index splits them, so an
 * email, a decimal, a version or a path is searched as the one lexeme it was indexed as.
 *
 * Each lexeme is quoted, with its quotes and backslashes escaped, and matched by prefix
 * (`'lex':*`); all of them are required (`&`). Quoted that way, `to_tsquery` can't fail on
 * the input, whatever the user typed.
 */
object TsQuery {

    /** The tsquery for [lexemes], or null when there is none (then there is nothing to search). */
    fun build(lexemes: List<String>, maxTokens: Int = MAX_TOKENS): String? =
        lexemes
            .filter { lexeme -> lexeme.isNotEmpty() }
            .map { lexeme -> lexeme.take(MAX_TOKEN_LENGTH) }
            .take(maxTokens)
            .takeIf { tokens -> tokens.isNotEmpty() }
            ?.joinToString(" & ") { lexeme -> "'${quoted(lexeme)}':*" }

    /** [lexeme] safe inside the quotes of a tsquery: `'` and `\\` doubled. */
    private fun quoted(lexeme: String): String =
        lexeme.replace("\\", "\\\\").replace("'", "''")

    const val MAX_TOKENS = 8
    const val MAX_TOKEN_LENGTH = 64
}
