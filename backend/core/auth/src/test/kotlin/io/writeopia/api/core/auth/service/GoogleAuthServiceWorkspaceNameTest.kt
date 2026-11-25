package io.writeopia.api.core.auth.service

import kotlin.test.Test
import kotlin.test.assertEquals

class GoogleAuthServiceWorkspaceNameTest {

    @Test
    fun `uses the first name`() {
        assertEquals("Ana's Workspace", GoogleAuthService.defaultWorkspaceName("Ana Silva"))
    }

    @Test
    fun `falls back when the name is blank`() {
        assertEquals("My Workspace", GoogleAuthService.defaultWorkspaceName("   "))
    }

    @Test
    fun `clips very long names to the workspace limit`() {
        val name = GoogleAuthService.defaultWorkspaceName("A".repeat(40))
        assertEquals(30, name.length)
    }
}
