package io.writeopia.api.media.service

import io.writeopia.api.core.auth.dto.AccountDeletionEventPayload
import io.writeopia.api.core.auth.models.AccountDeletionTopics
import io.writeopia.buckets.BucketConfig
import io.writeopia.buckets.GcpBucketImageStorageService
import io.writeopia.connection.logger
import io.writeopia.pubsub.PubsubPublisher
import io.writeopia.sdk.serialization.json.writeopiaJson

/**
 * Media-side handler for the account-deletion saga's account-deletion-requested event. Media
 * is stored per-user (uploads/$userId/...), not per-workspace, so this is just a single
 * prefix delete regardless of how many workspaces the user was in - see
 * GcpBucketImageStorageService.deleteAllUnderPrefix.
 *
 * The caller (AccountDeletionEventsRouting) only acks the inbound Pub/Sub message once this
 * returns successfully (GCS deletes AND the completion publish both done) - any exception
 * here must propagate so the message stays unacked and Pub/Sub redelivers.
 */
object AccountDeletionMediaService {
    suspend fun handleAccountDeletionRequested(
        userId: String,
        debugMode: Boolean = false,
    ) {
        logger.info("[AccountDeletion] media cleanup started for user $userId")

        if (!debugMode) {
            val bucketName = BucketConfig.imagesBucketName(debugMode)
            GcpBucketImageStorageService.deleteAllUnderPrefix(bucketName, "uploads/$userId/")
        }

        val payload = writeopiaJson.encodeToString(
            AccountDeletionEventPayload.serializer(),
            AccountDeletionEventPayload(userId = userId)
        )
        PubsubPublisher.publish(
            topicId = AccountDeletionTopics.MEDIA_COMPLETED,
            orderingKey = userId,
            payload = payload,
            debugMode = debugMode,
        )

        logger.info(
            "[AccountDeletion] outbox event ${AccountDeletionTopics.MEDIA_COMPLETED} " +
                "published for user $userId"
        )
    }
}
