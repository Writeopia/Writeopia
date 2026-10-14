package io.writeopia.ui.utils

/**
 * Tells whether a text is a single web address, as pasted from a browser.
 */
object LinkDetector {

    private val urlRegex = Regex("^https?://[^\\s]+$", RegexOption.IGNORE_CASE)

    /**
     * True when [text], trimmed, is one web address and nothing else.
     */
    fun isLink(text: String): Boolean {
        val trimmed = text.trim()
        if (!urlRegex.matches(trimmed)) return false

        // The address needs a host after the scheme.
        val afterScheme = trimmed.substringAfter("://")
        return afterScheme.substringBefore('/').substringBefore('?').substringBefore('#').isNotBlank()
    }

    /**
     * True when an empty step received a whole link at once, which is what a paste does.
     * Typing an address key by key never goes from empty to a full link in one input.
     */
    fun isPastedLink(previousText: String?, newText: String): Boolean =
        previousText.isNullOrBlank() && isLink(newText)
}
