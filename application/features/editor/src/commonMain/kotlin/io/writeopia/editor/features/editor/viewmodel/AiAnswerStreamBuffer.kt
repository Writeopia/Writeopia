package io.writeopia.editor.features.editor.viewmodel

/**
 * Splits an accumulated AI stream into separate blocks whenever a line (trimmed of leading
 * whitespace) starts with "#". This allows a single streamed AI answer to be rendered as
 * multiple AI_ANSWER StorySteps instead of one ever-growing block.
 *
 * A new block only starts once the current block has some non-heading content. Otherwise, a
 * heading immediately followed by another heading would produce a block with only a title and
 * nothing else, so it is kept together with the following heading(s) instead.
 */
object AiAnswerStreamBuffer {

    fun splitIntoBlocks(fullText: String): List<String> {
        val lines = fullText.split("\n")
        val blocks = mutableListOf<String>()
        val current = StringBuilder()
        var currentHasContent = false

        for (line in lines) {
            val isHeading = line.trimStart().startsWith("#")

            if (isHeading && current.isNotEmpty() && currentHasContent) {
                blocks.add(current.toString())
                current.clear()
                currentHasContent = false
            }

            if (current.isNotEmpty()) {
                current.append("\n")
            }

            current.append(line)

            if (!isHeading) {
                currentHasContent = true
            }
        }

        blocks.add(current.toString())

        return blocks
    }
}
