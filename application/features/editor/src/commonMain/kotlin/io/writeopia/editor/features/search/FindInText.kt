package io.writeopia.editor.features.search

object FindInText {

    private val separators = Regex("[^\\p{L}\\p{N}]+")

    /**
     * Finds the start and end indices of all occurrences of the words of a query within a given
     * text. The words are the ones the searches match (`FtsQuery`, `TsQuery`): runs of letters
     * and digits, ignoring case, at most [MAX_WORDS] of them. Every word must occur somewhere in
     * the text, inside another word too; then every occurrence of each is returned.
     *
     * @param text The string to search within.
     * @param query The words to search for.
     * @return The occurrences in order of position, each a pair of start and end indices.
     * The end index is exclusive, meaning it is the index of the character immediately following the match.
     */
    fun findInText(text: String, query: String): List<Pair<Int, Int>> {
        val words = words(query)
        if (words.isEmpty() || text.isEmpty()) return emptyList()

        val occurrences = words.map { word -> occurrences(text, word) }
        if (occurrences.any { found -> found.isEmpty() }) return emptyList()

        return occurrences.flatten()
            .distinct()
            .sortedWith(compareBy({ (start, _) -> start }, { (_, end) -> end }))
    }

    /** The words of [query]: letters and digits only, in order, at most [MAX_WORDS] of them. */
    fun words(query: String): List<String> =
        query.split(separators)
            .filter { word -> word.isNotEmpty() }
            .map { word -> word.take(MAX_WORD_LENGTH) }
            .take(MAX_WORDS)

    private fun occurrences(text: String, word: String): List<Pair<Int, Int>> {
        val results = mutableListOf<Pair<Int, Int>>()
        var startIndex = 0
        while (true) {
            val foundIndex = text.indexOf(word, startIndex, ignoreCase = true)
            if (foundIndex == -1) break
            results.add(Pair(foundIndex, foundIndex + word.length))
            startIndex = foundIndex + 1
        }
        return results
    }

    const val MAX_WORDS = 8
    const val MAX_WORD_LENGTH = 64
}
