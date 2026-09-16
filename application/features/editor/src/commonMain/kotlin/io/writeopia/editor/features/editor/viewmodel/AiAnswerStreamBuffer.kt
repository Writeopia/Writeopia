package io.writeopia.editor.features.editor.viewmodel

/**
 * Splits an accumulated AI stream into separate blocks whenever a line (trimmed of leading
 * whitespace) starts with "#". This allows a single streamed AI answer to be rendered as
 * multiple AI_ANSWER StorySteps instead of one ever-growing block.
 */
object AiAnswerStreamBuffer {

    fun splitIntoBlocks(fullText: String): List<String> {
        val lines = fullText.split("\n")
        val blocks = mutableListOf<String>()
        val current = StringBuilder()

        for (line in lines) {
            val isHeading = line.trimStart().startsWith("#")

            if (isHeading && current.isNotEmpty()) {
                blocks.add(current.toString())
                current.clear()
            }

            if (current.isNotEmpty()) {
                current.append("\n")
            }

            current.append(line)
        }

        blocks.add(current.toString())

        return blocks
    }
}
