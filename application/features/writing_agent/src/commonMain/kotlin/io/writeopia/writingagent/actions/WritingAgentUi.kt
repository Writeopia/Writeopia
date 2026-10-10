package io.writeopia.writingagent.actions

/**
 * What only the screen can do for an action: open the platform's image picker, or navigate to
 * the drawing screen. The screen hands this to the suggestions box.
 */
interface WritingAgentUi {
    /** Opens the image picker and calls [onPicked] with the path of the picked image. */
    fun pickImage(onPicked: (String) -> Unit)

    /** Opens the drawing screen. The drawing is added to the document when it is saved. */
    fun openDrawing()

    companion object {
        /** A UI that can't pick images or draw, for tests and platforms without the screens. */
        val None: WritingAgentUi = object : WritingAgentUi {
            override fun pickImage(onPicked: (String) -> Unit) {}

            override fun openDrawing() {}
        }
    }
}
