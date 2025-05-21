package io.writeopia.api.core.auth.hash

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HashUtilsTest {

    @Test
    fun `it should be possible to compare passwords correctly`() {
        val password = "thisISaPASSword"
        val salt = HashUtils.generateSalt()

        val hash = HashUtils.hashPassword(password, salt)

        assertTrue {
            HashUtils.verifyPassword(password, salt.toBase64(), hash.toBase64())
        }

        assertFalse {
            HashUtils.verifyPassword("wrongone", salt.toBase64(), hash.toBase64())
        }
    }
}
