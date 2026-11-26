package io.writeopia.api.core.auth.models

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GoogleIdentityTest {

    private fun identity(email: String, hostedDomain: String? = null) =
        GoogleIdentity(
            subject = "sub",
            email = email,
            emailVerified = true,
            name = null,
            audience = "client",
            hostedDomain = hostedDomain,
        )

    @Test
    fun `google is authoritative for gmail and for any workspace account`() {
        assertTrue(identity("ana@gmail.com").isEmailAuthoritative)
        assertTrue(identity("Ana@GoogleMail.com").isEmailAuthoritative)
        assertTrue(identity("ana@acme.io", hostedDomain = "acme.io").isEmailAuthoritative)
        // A secondary or alias domain of the same Workspace organisation.
        assertTrue(identity("ana@acme-labs.io", hostedDomain = "acme.io").isEmailAuthoritative)
    }

    @Test
    fun `a consumer account's other address was only verified once`() {
        assertFalse(identity("ana@acme.io").isEmailAuthoritative)
        assertFalse(identity("ana@acme.io", hostedDomain = " ").isEmailAuthoritative)
        assertFalse(identity("ana@gmail.com.evil.io").isEmailAuthoritative)
    }
}
