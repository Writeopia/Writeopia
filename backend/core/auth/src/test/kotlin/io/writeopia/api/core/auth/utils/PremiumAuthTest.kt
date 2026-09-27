package io.writeopia.api.core.auth.utils

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.writeopia.api.core.auth.configureTestPersistence
import io.writeopia.sql.WriteopiaDbBackend
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PremiumAuthTest {

    private lateinit var db: WriteopiaDbBackend
    private val userId = "premium-test-${UUID.randomUUID()}"

    @BeforeTest
    fun setUp() {
        db = configureTestPersistence()
        db.userEntityQueries.insertUser(
            id = userId,
            name = "Test User",
            username = "testuser",
            created_at = System.currentTimeMillis(),
            email = "$userId@example.com",
            password = "hashedpassword",
            salt = "salt",
            confirmation_code = null,
            confirmation_code_expiry = null,
            account_type = "FREE",
            status = "ACTIVE"
        )
    }

    @AfterTest
    fun tearDown() {
        db.userEntityQueries.deleteUser(userId)
    }

    @Test
    fun `isPremiumUser is false for free and unknown users`() {
        assertFalse(db.isPremiumUser(userId))
        assertFalse(db.isPremiumUser("non-existent-user"))
    }

    @Test
    fun `isPremiumUser is true for premium users`() {
        db.userEntityQueries.updateAccountType("PREMIUM", userId)

        assertTrue(db.isPremiumUser(userId))
    }

    @Test
    fun `requirePremiumUserId responds 401 without a token`() = testApplication {
        premiumRoute(debugMode = false)

        assertEquals(HttpStatusCode.Unauthorized, client.get("/premium").status)
    }

    @Test
    fun `requirePremiumUserId responds 403 for free users`() = testApplication {
        premiumRoute(debugMode = false)

        val response = client.get("/premium") { header("X-Forwarded-Authorization", bearer(userId)) }

        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `requirePremiumUserId allows premium users`() = testApplication {
        db.userEntityQueries.updateAccountType("PREMIUM", userId)
        premiumRoute(debugMode = false)

        val response = client.get("/premium") { header("X-Forwarded-Authorization", bearer(userId)) }

        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `requirePremiumUserId skips the premium check in debug mode`() = testApplication {
        premiumRoute(debugMode = true)

        val response = client.get("/premium") { header("X-Forwarded-Authorization", bearer(userId)) }

        assertEquals(HttpStatusCode.OK, response.status)
    }

    private fun io.ktor.server.testing.ApplicationTestBuilder.premiumRoute(debugMode: Boolean) {
        application {
            routing {
                get("/premium") {
                    call.requirePremiumUserId(db, debugMode) ?: return@get
                    call.respond(HttpStatusCode.OK)
                }
            }
        }
    }

    private fun bearer(userId: String): String =
        "Bearer " + JWT.create().withClaim("userId", userId).sign(Algorithm.none())
}
