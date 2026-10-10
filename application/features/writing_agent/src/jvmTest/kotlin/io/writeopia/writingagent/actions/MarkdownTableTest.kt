package io.writeopia.writingagent.actions

import kotlin.test.Test
import kotlin.test.assertEquals

class MarkdownTableTest {

    @Test
    fun `it reads the header and the rows and drops the separator`() {
        val rows = MarkdownTable.parse(
            """
            Here is the table:
            | Name | Price |
            |------|------:|
            | Milk | 2.50 |
            | Eggs | 3.00 |
            """.trimIndent()
        )

        assertEquals(
            listOf(
                listOf("Name", "Price"),
                listOf("Milk", "2.50"),
                listOf("Eggs", "3.00"),
            ),
            rows
        )
    }

    @Test
    fun `it reads nothing from prose`() {
        assertEquals(emptyList(), MarkdownTable.parse("No table here."))
    }
}
