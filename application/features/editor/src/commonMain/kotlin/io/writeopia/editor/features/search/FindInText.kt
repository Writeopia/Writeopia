package io.writeopia.editor.features.search

object FindInText {

    private val whitespace = Regex("\\s+")

    /**
     * Finds the start and end indices of all occurrences of the words of a query within a given
     * text. Each whitespace-separated word of the query is searched on its own, ignoring case,
     * the way the full-text searches match: a step holding the words apart is still a match.
     *
     * @param text The string to search within.
     * @param query The words to search for.
     * @return The occurrences in order of position, each a pair of start and end indices.
     * The end index is exclusive, meaning it is the index of the character immediately following the match.
     */
    fun findInText(text: String, query: String): List<Pair<Int, Int>> {
        val words = query.split(whitespace).filter { word -> word.isNotEmpty() }
        if (words.isEmpty() || text.isEmpty()) return emptyList()

        return words.flatMap { word -> occurrences(text, word) }
            .distinct()
            .sortedWith(compareBy({ (start, _) -> start }, { (_, end) -> end }))
    }

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
}
