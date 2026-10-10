package io.writeopia.writingagent.actions

/**
 * Reads the Markdown table the AI answers with, so it can fill a spreadsheet. The rows are the
 * lines between pipes; the separator line (`|---|---|`) is dropped.
 */
object MarkdownTable {

    fun parse(markdown: String): List<List<String>> =
        markdown.lines()
            .map { it.trim() }
            .filter { it.startsWith("|") }
            .map { line -> line.removePrefix("|").removeSuffix("|").split("|").map { it.trim() } }
            .filterNot { cells -> cells.all { cell -> cell.isEmpty() || cell.all { it == '-' || it == ':' } } }
            .filter { cells -> cells.any { it.isNotEmpty() } }
}
