package io.writeopia.sdk.model.story

/**
 * Class the descriptions the position of the text cursor.
 *
 * @property start the start of the cursor in the paragraph
 * @property end the end of the cursor in the paragraph
 * @property position the position of the paragraph (which line the cursor is)
 */
data class Selection(
    val start: Int,
    val end: Int,
    val position: Int,
    val fromEnd: Boolean = false
) {
    fun key() = if (start == end) hashCode() else 0

    fun sortedPositions(): Pair<Int, Int> = if (start < end) start to end else end to start

    companion object {
        fun start(): Selection = Selection(0, 0, 0)

        fun end(position: Int): Selection = Selection(Int.MAX_VALUE, Int.MAX_VALUE, position)

        fun fromPosition(cursorPosition: Int, stepPosition: Int) =
            Selection(cursorPosition, cursorPosition, stepPosition)

        fun fromLastLine(cursorPosition: Int, stepPosition: Int) =
            Selection(cursorPosition, cursorPosition, stepPosition, fromEnd = true)
    }
}
