package io.writeopia.api.core.auth.utils

import kotlin.test.Test
import kotlin.test.assertEquals

class GoogleOAuthEnvTest {

    @Test
    fun `parses client secrets as id colon secret pairs`() {
        val parsed = GoogleOAuthEnv.parseClientSecrets(" web-id:web-secret , desktop-id:desk:ret ,broken, :x, y: ")

        assertEquals(mapOf("web-id" to "web-secret", "desktop-id" to "desk:ret"), parsed)
    }

    @Test
    fun `empty or missing env yields no secrets`() {
        assertEquals(emptyMap(), GoogleOAuthEnv.parseClientSecrets(null))
        assertEquals(emptyMap(), GoogleOAuthEnv.parseClientSecrets(""))
    }
}
