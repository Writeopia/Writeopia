package io.writeopia.api.ai.repository

import io.writeopia.api.ai.configureTestPersistence
import io.writeopia.sql.WriteopiaDbBackend
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class AiUsageRepositoryTest {

    private lateinit var db: WriteopiaDbBackend
    private val testUserId = "test-user-${UUID.randomUUID()}"

    @BeforeTest
    fun setUp() {
        db = configureTestPersistence()
    }

    @AfterTest
    fun tearDown() {
        // Clean up test data
        db.aiUsageEntityQueries.deleteOldRecords(Long.MAX_VALUE)
    }

    @Test
    fun `insertAiUsage should store usage record`() {
        val id = UUID.randomUUID().toString()

        db.insertAiUsage(
            id = id,
            userId = testUserId,
            operationType = "generate",
            inputTokens = 100,
            outputTokens = 200,
            totalTokens = 300,
            model = "gemini-2.0-flash"
        )

        val now = System.currentTimeMillis()
        val summary = db.getAiUsageSummary(testUserId, 0, now)

        assertEquals(100L, summary.totalInputTokens)
        assertEquals(200L, summary.totalOutputTokens)
        assertEquals(300L, summary.totalTokens)
        assertEquals(1L, summary.requestCount)
    }

    @Test
    fun `getAiUsageSummary should aggregate multiple records`() {
        // Insert multiple usage records
        repeat(3) { i ->
            db.insertAiUsage(
                id = UUID.randomUUID().toString(),
                userId = testUserId,
                operationType = "summary",
                inputTokens = 100 * (i + 1),
                outputTokens = 50 * (i + 1),
                totalTokens = 150 * (i + 1),
                model = "gemini-2.0-flash"
            )
        }

        val now = System.currentTimeMillis()
        val summary = db.getAiUsageSummary(testUserId, 0, now)

        // 100 + 200 + 300 = 600
        assertEquals(600L, summary.totalInputTokens)
        // 50 + 100 + 150 = 300
        assertEquals(300L, summary.totalOutputTokens)
        // 150 + 300 + 450 = 900
        assertEquals(900L, summary.totalTokens)
        assertEquals(3L, summary.requestCount)
    }

    @Test
    fun `getAiUsageSummary should return zeros for user with no usage`() {
        val nonExistentUserId = "non-existent-user-${UUID.randomUUID()}"
        val now = System.currentTimeMillis()

        val summary = db.getAiUsageSummary(nonExistentUserId, 0, now)

        assertEquals(0L, summary.totalInputTokens)
        assertEquals(0L, summary.totalOutputTokens)
        assertEquals(0L, summary.totalTokens)
        assertEquals(0L, summary.requestCount)
    }

    @Test
    fun `getAiUsageSummary should filter by date range`() {
        val now = System.currentTimeMillis()
        val oneHourAgo = now - 3600_000
        val twoHoursAgo = now - 7200_000

        // Insert record that should be included
        db.insertAiUsage(
            id = UUID.randomUUID().toString(),
            userId = testUserId,
            operationType = "generate",
            inputTokens = 100,
            outputTokens = 200,
            totalTokens = 300,
            model = "gemini-2.0-flash"
        )

        // Query for records in the last hour - should find our record
        val summaryIncluded = db.getAiUsageSummary(testUserId, oneHourAgo, now + 1000)
        assertEquals(1L, summaryIncluded.requestCount)
        assertEquals(300L, summaryIncluded.totalTokens)

        // Query for records from 2 hours ago to 1 hour ago - should not find our recent record
        val summaryExcluded = db.getAiUsageSummary(testUserId, twoHoursAgo, oneHourAgo)
        assertEquals(0L, summaryExcluded.requestCount)
    }

    @Test
    fun `getAiUsageSummary should only return usage for specified user`() {
        val otherUserId = "other-user-${UUID.randomUUID()}"

        // Insert usage for test user
        db.insertAiUsage(
            id = UUID.randomUUID().toString(),
            userId = testUserId,
            operationType = "generate",
            inputTokens = 100,
            outputTokens = 200,
            totalTokens = 300,
            model = "gemini-2.0-flash"
        )

        // Insert usage for another user
        db.insertAiUsage(
            id = UUID.randomUUID().toString(),
            userId = otherUserId,
            operationType = "generate",
            inputTokens = 500,
            outputTokens = 500,
            totalTokens = 1000,
            model = "gemini-2.0-flash"
        )

        val now = System.currentTimeMillis()

        // Verify test user only sees their own usage
        val testUserSummary = db.getAiUsageSummary(testUserId, 0, now)
        assertEquals(300L, testUserSummary.totalTokens)
        assertEquals(1L, testUserSummary.requestCount)

        // Verify other user only sees their own usage
        val otherUserSummary = db.getAiUsageSummary(otherUserId, 0, now)
        assertEquals(1000L, otherUserSummary.totalTokens)
        assertEquals(1L, otherUserSummary.requestCount)
    }

    @Test
    fun `insertAiUsage should store different operation types`() {
        val operations = listOf("generate", "summary", "action-points", "faq", "tags")

        operations.forEach { operation ->
            db.insertAiUsage(
                id = UUID.randomUUID().toString(),
                userId = testUserId,
                operationType = operation,
                inputTokens = 100,
                outputTokens = 100,
                totalTokens = 200,
                model = "gemini-2.0-flash"
            )
        }

        val now = System.currentTimeMillis()
        val summary = db.getAiUsageSummary(testUserId, 0, now)

        assertEquals(5L, summary.requestCount)
        assertEquals(1000L, summary.totalTokens) // 200 * 5
    }
}
