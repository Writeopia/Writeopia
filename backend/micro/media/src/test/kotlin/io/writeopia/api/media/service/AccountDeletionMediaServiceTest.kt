package io.writeopia.api.media.service

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.writeopia.api.core.auth.models.AccountDeletionTopics
import io.writeopia.buckets.BucketConfig
import io.writeopia.buckets.GcpBucketImageStorageService
import io.writeopia.pubsub.PubsubPublisher
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

/**
 * GcpBucketImageStorageService, PubsubPublisher and BucketConfig are all Kotlin objects with
 * no injectable seam here, so this mocks them statically via mockkObject rather than adding
 * one (unlike AccountDeletionWorkspaceService, which does take publish as a parameter).
 */
class AccountDeletionMediaServiceTest {

    @BeforeTest
    fun setUp() {
        mockkObject(BucketConfig)
        mockkObject(GcpBucketImageStorageService)
        mockkObject(PubsubPublisher)

        every { BucketConfig.imagesBucketName(any()) } returns "test-bucket"
        coEvery { GcpBucketImageStorageService.deleteAllUnderPrefix(any(), any()) } returns Unit
        coEvery { PubsubPublisher.publish(any(), any(), any(), any()) } returns Unit
    }

    @AfterTest
    fun tearDown() {
        unmockkObject(BucketConfig)
        unmockkObject(GcpBucketImageStorageService)
        unmockkObject(PubsubPublisher)
    }

    @Test
    fun `deletes everything under the user's media prefix and publishes completion`() = runTest {
        val userId = "user-${System.nanoTime()}"

        AccountDeletionMediaService.handleAccountDeletionRequested(userId, debugMode = false)

        coVerify(exactly = 1) {
            GcpBucketImageStorageService.deleteAllUnderPrefix("test-bucket", "uploads/$userId/")
        }
        coVerify(exactly = 1) {
            PubsubPublisher.publish(
                topicId = AccountDeletionTopics.MEDIA_COMPLETED,
                orderingKey = userId,
                payload = any(),
                debugMode = false,
            )
        }
    }
}
