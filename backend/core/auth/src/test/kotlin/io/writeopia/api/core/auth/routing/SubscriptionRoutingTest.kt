package io.writeopia.api.core.auth.routing

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.writeopia.api.core.auth.configureTestPersistence
import io.writeopia.api.core.auth.subscription.SubscriptionPlan
import io.writeopia.sdk.serialization.response.SubscriptionResponse
import io.writeopia.sql.WriteopiaDbBackend
import kotlinx.serialization.json.Json
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SubscriptionRoutingTest {

    private lateinit var db: WriteopiaDbBackend
    private val userId = "subscription-test-${UUID.randomUUID()}"
    private val plan = SubscriptionPlan(monthlyPriceCents = 799, currency = "EUR")
    private val json = Json { ignoreUnknownKeys = true }

    @BeforeTest
    fun setUp() {
        db = configureTestPersistence()
        db.userEntityQueries.insertUser(
            id = userId,
            name = "Test User",
            username = "subscription${userId.takeLast(8)}",
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
    fun `it should answer 401 without a token`() = testApplication {
        subscriptionModule(debugMode = false)

        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/auth/subscription").status)
    }

    @Test
    fun `a free user is not premium and gets the monthly price`() = testApplication {
        subscriptionModule(debugMode = false)

        val response = client.get("/api/auth/subscription") {
            header("X-Forwarded-Authorization", bearer(userId))
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("no-store", response.headers[HttpHeaders.CacheControl])
        val body = json.decodeFromString<SubscriptionResponse>(response.bodyAsText())
        assertFalse(body.premium)
        assertEquals("FREE", body.tier)
        assertEquals(799, body.monthlyPriceCents)
        assertEquals("EUR", body.currency)
    }

    @Test
    fun `a premium user is premium`() = testApplication {
        db.userEntityQueries.updateAccountType("PREMIUM", userId)
        subscriptionModule(debugMode = false)

        val response = client.get("/api/auth/subscription") {
            header("X-Forwarded-Authorization", bearer(userId))
        }

        val body = json.decodeFromString<SubscriptionResponse>(response.bodyAsText())
        assertTrue(body.premium)
        assertEquals("PREMIUM", body.tier)
    }

    @Test
    fun `every user is premium in debug mode`() = testApplication {
        subscriptionModule(debugMode = true)

        val response = client.get("/api/auth/subscription") {
            header("X-Forwarded-Authorization", bearer(userId))
        }

        assertTrue(json.decodeFromString<SubscriptionResponse>(response.bodyAsText()).premium)
    }

    @Test
    fun `the plan is read from the environment`() {
        val env = mapOf("SUBSCRIPTION_MONTHLY_PRICE_CENTS" to "1299", "SUBSCRIPTION_CURRENCY" to "brl")

        assertEquals(SubscriptionPlan(1299, "BRL"), SubscriptionPlan.fromEnv(env::get))
        assertEquals(
            SubscriptionPlan(SubscriptionPlan.DEFAULT_MONTHLY_PRICE_CENTS, SubscriptionPlan.DEFAULT_CURRENCY),
            SubscriptionPlan.fromEnv { null }
        )
        assertFailsWith<IllegalArgumentException> { SubscriptionPlan.fromEnv(mapOf("SUBSCRIPTION_CURRENCY" to "dollars")::get) }
    }

    private fun ApplicationTestBuilder.subscriptionModule(debugMode: Boolean) {
        application {
            install(ContentNegotiation) { json() }
            routing { subscriptionRoute(db, debugMode, plan) }
        }
    }

    private fun bearer(userId: String): String =
        "Bearer " + JWT.create().withClaim("userId", userId).sign(Algorithm.none())
}
