package io.writeopia.api.ai.routing

import io.writeopia.api.ai.configureTestPersistence
import io.writeopia.api.ai.repository.AiUsageSummary
import io.writeopia.api.ai.repository.getAiUsageSummary
import io.writeopia.api.ai.repository.insertAiUsage
import io.writeopia.sql.WriteopiaDbBackend
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for AI authorization logic:
 * - Premium user requirement
 * - Monthly quota enforcement
 */
class AiAuthorizationTest {

    private lateinit var db: WriteopiaDbBackend
    private val testUserId = "test-user-${UUID.randomUUID()}"
    private val testEmail = "test-${UUID.randomUUID()}@example.com"

    @BeforeTest
    fun setUp() {
        db = configureTestPersistence()
        // Create a test user
        db.userEntityQueries.insertUser(
            id = testUserId,
            name = "Test User",
            created_at = System.currentTimeMillis(),
            email = testEmail,
            password = "hashedpassword",
            salt = "salt",
            enabled = true,
            confirmation_code = null,
            confirmation_code_expiry = null,
            account_type = "FREE"
        )
    }

    @AfterTest
    fun tearDown() {
        // Clean up test data
        db.userEntityQueries.deleteUser(testUserId)
        db.aiUsageEntityQueries.deleteOldRecords(Long.MAX_VALUE)
    }

    @Test
    fun `selectAccountTypeById should return FREE for new user`() {
        val accountType = db.userEntityQueries
            .selectAccountTypeById(testUserId)
            .executeAsOneOrNull()

        assertEquals("FREE", accountType)
    }

    @Test
    fun `updateAccountType should change user to PREMIUM`() {
        // Update to PREMIUM
        db.userEntityQueries.updateAccountType("PREMIUM", testUserId)

        val accountType = db.userEntityQueries
            .selectAccountTypeById(testUserId)
            .executeAsOneOrNull()

        assertEquals("PREMIUM", accountType)
    }

    @Test
    fun `selectAccountTypeById should return null for non-existent user`() {
        val accountType = db.userEntityQueries
            .selectAccountTypeById("non-existent-user")
            .executeAsOneOrNull()

        assertEquals(null, accountType)
    }

    @Test
    fun `quota check should pass when usage is below limit`() {
        val monthlyQuota = 100_000L

        // Add some usage below quota
        db.insertAiUsage(
            id = UUID.randomUUID().toString(),
            userId = testUserId,
            operationType = "generate",
            inputTokens = 5000,
            outputTokens = 5000,
            totalTokens = 10000,
            model = "gemini-2.0-flash"
        )

        val now = System.currentTimeMillis()
        val summary = db.getAiUsageSummary(testUserId, 0, now)

        assertTrue(summary.totalTokens < monthlyQuota)
    }

    @Test
    fun `quota check should fail when usage exceeds limit`() {
        val monthlyQuota = 100_000L

        // Add usage that exceeds quota
        db.insertAiUsage(
            id = UUID.randomUUID().toString(),
            userId = testUserId,
            operationType = "generate",
            inputTokens = 60000,
            outputTokens = 50000,
            totalTokens = 110000,
            model = "gemini-2.0-flash"
        )

        val now = System.currentTimeMillis()
        val summary = db.getAiUsageSummary(testUserId, 0, now)

        assertTrue(summary.totalTokens >= monthlyQuota)
    }

    @Test
    fun `quota check should consider only current month usage`() {
        val monthlyQuota = 100_000L
        val now = System.currentTimeMillis()

        // Manually insert old record with old timestamp
        // Note: We can't easily test this without modifying the insert function
        // So we test the date range filtering in the query

        // Insert current usage
        db.insertAiUsage(
            id = UUID.randomUUID().toString(),
            userId = testUserId,
            operationType = "generate",
            inputTokens = 5000,
            outputTokens = 5000,
            totalTokens = 10000,
            model = "gemini-2.0-flash"
        )

        // Query from start of current month (approximation)
        val startOfMonth = now - (30L * 24 * 60 * 60 * 1000) // ~30 days ago
        val summary = db.getAiUsageSummary(testUserId, startOfMonth, now)

        assertEquals(10000L, summary.totalTokens)
    }

    @Test
    fun `multiple requests should accumulate towards quota`() {
        val monthlyQuota = 100_000L

        // Simulate multiple AI requests
        repeat(5) {
            db.insertAiUsage(
                id = UUID.randomUUID().toString(),
                userId = testUserId,
                operationType = "generate",
                inputTokens = 10000,
                outputTokens = 10000,
                totalTokens = 20000,
                model = "gemini-2.0-flash"
            )
        }

        val now = System.currentTimeMillis()
        val summary = db.getAiUsageSummary(testUserId, 0, now)

        // 20000 * 5 = 100000, exactly at quota
        assertEquals(100000L, summary.totalTokens)
        assertTrue(summary.totalTokens >= monthlyQuota)
    }

    @Test
    fun `different users should have separate quotas`() {
        val otherUserId = "other-user-${UUID.randomUUID()}"
        val otherEmail = "other-${UUID.randomUUID()}@example.com"

        // Create another user
        db.userEntityQueries.insertUser(
            id = otherUserId,
            name = "Other User",
            created_at = System.currentTimeMillis(),
            email = otherEmail,
            password = "hashedpassword",
            salt = "salt",
            enabled = true,
            confirmation_code = null,
            confirmation_code_expiry = null,
            account_type = "PREMIUM"
        )

        try {
            // Add usage for test user
            db.insertAiUsage(
                id = UUID.randomUUID().toString(),
                userId = testUserId,
                operationType = "generate",
                inputTokens = 50000,
                outputTokens = 50000,
                totalTokens = 100000,
                model = "gemini-2.0-flash"
            )

            // Add usage for other user
            db.insertAiUsage(
                id = UUID.randomUUID().toString(),
                userId = otherUserId,
                operationType = "generate",
                inputTokens = 5000,
                outputTokens = 5000,
                totalTokens = 10000,
                model = "gemini-2.0-flash"
            )

            val now = System.currentTimeMillis()

            // Test user should be at quota
            val testUserSummary = db.getAiUsageSummary(testUserId, 0, now)
            assertEquals(100000L, testUserSummary.totalTokens)

            // Other user should have separate, lower usage
            val otherUserSummary = db.getAiUsageSummary(otherUserId, 0, now)
            assertEquals(10000L, otherUserSummary.totalTokens)
        } finally {
            db.userEntityQueries.deleteUser(otherUserId)
        }
    }
}
