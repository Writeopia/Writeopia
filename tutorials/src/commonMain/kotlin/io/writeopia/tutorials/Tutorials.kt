package io.writeopia.tutorials

object Tutorials {

    fun allTutorialsDocuments(): Sequence<String> =
        sequence {
            yieldAll(
                listOf(
                    welcomeTutorial(),
                    aiTutorial(),
                    savingNotesTutorial(),
                    commandsTutorial(),
                    videoTutorial()
                ).reversed()
            )
        }
}
