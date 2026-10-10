package io.writeopia.writingagent.actions

import kotlin.test.Test
import kotlin.test.assertEquals

class MarkdownListTest {

    @Test
    fun `it reads dashes, stars, numbers and checkboxes and drops the prose`() {
        val items = MarkdownList.parse(
            """
            Sure, here is the list:
            - Milk
            * Eggs
            1. Bread
            2) Butter
            - [ ] Call the plumber
            -
            """.trimIndent()
        )

        assertEquals(listOf("Milk", "Eggs", "Bread", "Butter", "Call the plumber"), items)
    }

    @Test
    fun `it reads nothing from prose`() {
        assertEquals(emptyList(), MarkdownList.parse("No list here."))
    }
}
