package io.writeopia.api.core.auth.service

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GoogleUsernameGeneratorTest {

    @Test
    fun `uses the sanitized lowercase local part of the email`() {
        assertEquals("ana.silva".replace(".", ""), GoogleUsernameGenerator.baseUsername("Ana.Silva@gmail.com"))
        assertEquals("john_doe-1", GoogleUsernameGenerator.baseUsername("John_Doe-1@example.org"))
    }

    @Test
    fun `pads short local parts to the minimum length`() {
        assertEquals("abuser", GoogleUsernameGenerator.baseUsername("ab@x.io"))
        assertEquals("user", GoogleUsernameGenerator.baseUsername("+++@x.io"))
    }

    @Test
    fun `clips long local parts to the maximum length`() {
        val local = "a".repeat(45)
        assertEquals(30, GoogleUsernameGenerator.baseUsername("$local@x.io").length)
    }

    @Test
    fun `appends a numeric suffix when the base is taken`() {
        val taken = setOf("ana", "ana-2")
        assertEquals("ana-3", GoogleUsernameGenerator.generate("ana@x.io") { it in taken })
    }

    @Test
    fun `suffixed usernames stay within the maximum length`() {
        val local = "b".repeat(30)
        val taken = setOf(local)
        val generated = GoogleUsernameGenerator.generate("$local@x.io") { it in taken }

        assertEquals(30, generated.length)
        assertTrue(generated.endsWith("-2"))
    }

    @Test
    fun `falls back to a random suffix after too many collisions`() {
        val generated = GoogleUsernameGenerator.generate("ana@x.io") { !it.matches(Regex("ana-\\d{6}")) }

        assertTrue(generated.matches(Regex("ana-\\d{6}")), generated)
    }
}
