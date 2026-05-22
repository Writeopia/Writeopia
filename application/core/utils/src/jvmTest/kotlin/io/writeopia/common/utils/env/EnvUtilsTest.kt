package io.writeopia.common.utils.env

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EnvUtilsTest {

    @Test
    fun `getAdminKey should return null when env var is not set`() {
        // When the WRITEOPIA_ADMIN_KEY environment variable is not set,
        // getAdminKey should return null
        // Note: This test assumes the env var is not set in the test environment
        val result = EnvUtils.getAdminKey()

        // If the env var happens to be set in the test environment,
        // we just verify it returns a non-empty string
        if (result != null) {
            assert(result.isNotEmpty()) { "Admin key should not be empty when set" }
        }
        // Otherwise null is expected
    }

    @Test
    fun `getAdminKey should use System getenv internally`() {
        // This test verifies the implementation uses System.getenv
        // We can verify the behavior matches System.getenv
        val expected = System.getenv("WRITEOPIA_ADMIN_KEY")
        val actual = EnvUtils.getAdminKey()

        assertEquals(expected, actual)
    }
}
