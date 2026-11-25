package io.writeopia.tutorials

/**
 * Hidden documents. Each version of Writeopia is named after a Brazilian writer, and tapping the
 * version label ten times opens the document about the current one.
 */
object EasterEggs {

    /** Name of the writer the current version is named after, as the version label shows it. */
    const val VERSION_WRITER = "Bandeira, Manuel"

    /**
     * The document about the writer of the current version, as the JSON of a `DocumentApi`.
     *
     * [newId] is called once per document and once per step, so every copy of the document is
     * made of new rows and opening the easter egg twice never overwrites the first copy.
     */
    fun versionWriterDocument(newId: () -> String): String {
        val title = "Manuel Bandeira"

        val paragraphs = listOf(
            Paragraph(
                "Manuel Carneiro de Sousa Bandeira Filho was born in Recife on April 19, 1886, " +
                    "and died in Rio de Janeiro on October 13, 1968. He is one of the great " +
                    "poets of Brazilian Modernism, and also a translator, literary critic, " +
                    "chronicler and professor."
            ),
            Paragraph("A life with a short horizon", heading = true),
            Paragraph(
                "As a young man he studied architecture in São Paulo, but at about 18 he was " +
                    "diagnosed with tuberculosis, which in those days was a sentence. He left " +
                    "his studies and spent years looking for a cure in sanatoriums and in the " +
                    "mountains, always expecting an early death. He lived for more than 80 years. " +
                    "That closeness to death, treated with irony and tenderness instead of " +
                    "drama, marks much of his work."
            ),
            Paragraph("The poet", heading = true),
            Paragraph(
                "His first books, Cinza das Horas (1917), Carnaval (1919) and O Ritmo " +
                    "Dissoluto (1924), still carry the weight of the Symbolists and Parnassians. " +
                    "In 1922 his poem Os Sapos, a satire of the Parnassian poets, was read at the " +
                    "Week of Modern Art in São Paulo, and it became an anthem of the movement. " +
                    "With Libertinagem (1930) he found his own voice: free verse, everyday " +
                    "subjects, colloquial speech and humor."
            ),
            Paragraph(
                "Later books include Estrela da Manhã (1936), Lira dos Cinquent'Anos (1940), " +
                    "Belo Belo (1948) and Opus 10 (1952). His memoir Itinerário de Pasárgada " +
                    "(1954) tells how the poems came to be."
            ),
            Paragraph("Pasárgada", heading = true),
            Paragraph(
                "Pasárgada was an ancient Persian capital, which Bandeira read about as a boy. " +
                    "In Vou-me Embora pra Pasárgada it became a place of imagination, where the " +
                    "poet is a friend of the king and can have whatever he wants. It is one of the " +
                    "best-known Brazilian poems, and “going to Pasárgada” means " +
                    "escaping to a place where everything is possible."
            ),
            Paragraph("Other work and recognition", heading = true),
            Paragraph(
                "Bandeira translated poetry and plays from Spanish, German, English and French, " +
                    "wrote chronicles and criticism, and taught Brazilian literature at the " +
                    "Colégio Pedro II and at the Faculdade Nacional de Filosofia. He joined " +
                    "the Brazilian Academy of Letters in 1940, in seat 24."
            ),
            Paragraph("Why him?", heading = true),
            Paragraph(
                "Writing is an act of paying attention to small things, such as a " +
                    "street in Recife, a sad afternoon, the daily news. Bandeira showed that a " +
                    "plain sentence can be enough. That fits an editor that wants to stay out of " +
                    "the way of the words. Congratulations on finding this page!"
            ),
        )

        val steps = buildString {
            append(step(newId(), "title", 11, title, position = 0, color = -4144960))

            paragraphs.forEachIndexed { index, paragraph ->
                append(",\n")
                append(
                    step(
                        newId(),
                        "message",
                        0,
                        paragraph.text,
                        position = index + 1,
                        tag = if (paragraph.heading) "H2" else null
                    )
                )
            }
        }

        return """
{
    "id": "${newId()}",
    "title": "$title",
    "workspaceId": "disconnected_user",
    "content": [
$steps
    ]
}
"""
    }

    private class Paragraph(val text: String, val heading: Boolean = false)

    private fun step(
        id: String,
        typeName: String,
        typeNumber: Int,
        text: String,
        position: Int,
        tag: String? = null,
        color: Int? = null,
    ): String {
        val tags = tag?.let {
            """,
            "tags": [{"tag": "$it", "position": 0}]"""
        } ?: ""

        val decoration = color?.let {
            """,
            "decoration": {"backgroundColor": $it}"""
        } ?: ""

        return """        {
            "id": "$id",
            "type": {"name": "$typeName", "number": $typeNumber},
            "text": "${text.escapeJson()}"$tags$decoration,
            "position": $position
        }"""
    }

    private fun String.escapeJson(): String =
        replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
}
