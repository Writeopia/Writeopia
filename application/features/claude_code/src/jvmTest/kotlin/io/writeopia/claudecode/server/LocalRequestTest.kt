package io.writeopia.claudecode.server

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** No secret: what keeps web pages out is the Origin and the Host of the request. */
class LocalRequestTest {

    @Test
    fun `only this machine at the port is a local host`() {
        assertTrue(ClaudeCodeServer.isLocalHost("127.0.0.1:47615", 47615))
        assertTrue(ClaudeCodeServer.isLocalHost("localhost:47615", 47615))
        assertTrue(ClaudeCodeServer.isLocalHost("[::1]:47615", 47615))
        // A site pointing its own name at 127.0.0.1 (DNS rebinding) sends its name.
        assertFalse(ClaudeCodeServer.isLocalHost("evil.example:47615", 47615))
        assertFalse(ClaudeCodeServer.isLocalHost("127.0.0.1", 47615))
        assertFalse(ClaudeCodeServer.isLocalHost("127.0.0.1:80", 47615))
    }

    @Test
    fun `only pages of this machine are local origins`() {
        assertTrue(ClaudeCodeServer.isLocalOrigin("http://localhost:3000"))
        assertTrue(ClaudeCodeServer.isLocalOrigin("http://127.0.0.1"))
        assertFalse(ClaudeCodeServer.isLocalOrigin("https://evil.example"))
        assertFalse(ClaudeCodeServer.isLocalOrigin("http://127.0.0.1.evil.example"))
    }
}
