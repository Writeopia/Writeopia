package io.writeopia.api.genai.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DocumentTagParserTest {

    @Test
    fun `reads the json the model is asked for`() {
        val tag = DocumentTagParser.parse("""{"name": "Meetings", "description": "Notes taken during team meetings."}""")

        assertEquals(GeneratedDocumentTag("Meetings", "Notes taken during team meetings."), tag)
    }

    @Test
    fun `reads json wrapped in a code fence`() {
        val tag = DocumentTagParser.parse("```json\n{\"name\":\"Recipes\",\"description\":\"Dishes to cook.\"}\n```")

        assertEquals(GeneratedDocumentTag("Recipes", "Dishes to cook."), tag)
    }

    @Test
    fun `falls back to a name and explanation line`() {
        assertEquals(
            GeneratedDocumentTag("Travel", "Trips and itineraries"),
            DocumentTagParser.parse("Travel: Trips and itineraries"),
        )
        assertEquals(GeneratedDocumentTag("Travel", ""), DocumentTagParser.parse("**Travel**"))
    }

    @Test
    fun `answers without a name are rejected and long values are trimmed`() {
        assertNull(DocumentTagParser.parse(""))
        assertNull(DocumentTagParser.parse(null))
        assertNull(DocumentTagParser.parse("""{"name": "", "description": "x"}"""))

        val long = DocumentTagParser.parse("""{"name": "${"n".repeat(80)}", "description": "${"d".repeat(400)}"}""")
        assertEquals(DocumentTagParser.MAX_NAME_LENGTH, long?.name?.length)
        assertEquals(DocumentTagParser.MAX_DESCRIPTION_LENGTH, long?.description?.length)
    }
}
