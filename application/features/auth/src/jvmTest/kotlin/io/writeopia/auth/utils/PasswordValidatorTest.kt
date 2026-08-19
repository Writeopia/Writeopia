package io.writeopia.auth.utils

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PasswordValidatorTest {

    @Test
    fun `empty password returns NONE strength`() {
        val result = PasswordValidator.validate("")

        assertEquals(PasswordStrength.NONE, result.strength)
        assertFalse(result.hasMinLength)
        assertFalse(result.hasSpecialChar)
    }

    @Test
    fun `short password without special char returns WEAK`() {
        val result = PasswordValidator.validate("abc")

        assertEquals(PasswordStrength.WEAK, result.strength)
        assertFalse(result.hasMinLength)
        assertFalse(result.hasSpecialChar)
    }

    @Test
    fun `short password with special char returns MEDIUM`() {
        val result = PasswordValidator.validate("abc!")

        assertEquals(PasswordStrength.MEDIUM, result.strength)
        assertFalse(result.hasMinLength)
        assertTrue(result.hasSpecialChar)
    }

    @Test
    fun `8 char password without special char returns MEDIUM`() {
        val result = PasswordValidator.validate("abcdefgh")

        assertEquals(PasswordStrength.MEDIUM, result.strength)
        assertTrue(result.hasMinLength)
        assertFalse(result.hasSpecialChar)
    }

    @Test
    fun `8 char password with special char returns STRONG`() {
        val result = PasswordValidator.validate("abcdefg!")

        assertEquals(PasswordStrength.STRONG, result.strength)
        assertTrue(result.hasMinLength)
        assertTrue(result.hasSpecialChar)
    }

    @Test
    fun `long password with special char returns STRONG`() {
        val result = PasswordValidator.validate("MyPassword!")

        assertEquals(PasswordStrength.STRONG, result.strength)
        assertTrue(result.hasMinLength)
        assertTrue(result.hasSpecialChar)
    }

    @Test
    fun `various special characters are recognized`() {
        val specialChars = "!@#\$%^&*()_+-=[]{}|;':\",./<>?`~"

        for (char in specialChars) {
            val result = PasswordValidator.validate("abcdefg$char")
            assertTrue(result.hasSpecialChar, "Special char '$char' should be recognized")
            assertEquals(PasswordStrength.STRONG, result.strength, "Password with '$char' should be STRONG")
        }
    }

    @Test
    fun `7 char password is not long enough`() {
        val result = PasswordValidator.validate("abcdefg")

        assertFalse(result.hasMinLength)
        assertEquals(PasswordStrength.WEAK, result.strength)
    }

    @Test
    fun `exactly 8 char password meets length requirement`() {
        val result = PasswordValidator.validate("abcdefgh")

        assertTrue(result.hasMinLength)
    }
}
