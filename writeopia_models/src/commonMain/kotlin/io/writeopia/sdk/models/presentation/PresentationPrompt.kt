package io.writeopia.sdk.models.presentation

/**
 * The prompt that asks a model for the slides of a presentation, in the Markdown
 * `PresentationMarkdownParser` reads: one slide per `---`, a heading as the title. Shared by the
 * backend (cloud AI) and the apps (local AI), so both make the same presentation.
 */
object PresentationPrompt {
    val instructions: String = """
        Create a slide presentation from the document below. Write it in Markdown, following these rules:
        - Every slide starts with a heading line ("## Title of the slide").
        - Below the heading, write a short description of the slide: a few sentences, "- " items for a list or "[] " items for a checklist. Keep every slide short enough to be read at a glance.
        - Every slide ends with a line containing only "---".
        - Make roughly one slide per section of the document. When sections are too small, merge them into one slide; when a section is too long, split it. The presentation must make sense on its own.
        - Start with a title slide and end with a closing slide. Use between 3 and 12 slides.
        - Use the language of the document.
        - Answer with the Markdown of the slides only, without comments about the task and without wrapping it in a code block.

        Example of the format:
        ## Why the sky is blue

        Sunlight scatters in the atmosphere, and blue light scatters the most.
        ---

        ## What we will see

        [] The physics of scattering
        - Sunsets and their colors
        ---

        ## Thank you!
        The presentation is over.
    """.trimIndent()

    /** The whole prompt for a document given as Markdown. */
    fun forDocument(documentMarkdown: String): String =
        "$instructions\n\nThe document:\n```\n$documentMarkdown\n```"
}
