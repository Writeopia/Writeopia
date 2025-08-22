package io.writeopia.editor.features.search

object FindInText {

    /**
     * Finds the start and end indices of all occurrences of a query string within a given text.
     *
     * @param text The string to search within.
     * @param query The string to search for.
     * @return A list of pairs, where each pair represents the start and end indices of a found occurrence.
     * The end index is exclusive, meaning it is the index of the character immediately following the match.
     */
    fun findInText(text: String, query: String): List<Pair<Int, Int>> {
        val results = mutableListOf<Pair<Int, Int>>()
        if (query.isEmpty()) {
            return results
        }

        var startIndex = 0
        while (true) {
            val foundIndex = text.indexOf(query, startIndex)
            if (foundIndex == -1) {
                break
            }
            val endIndex = foundIndex + query.length
            results.add(Pair(foundIndex, endIndex))
            startIndex = foundIndex + 1
        }

        return results
    }
}
