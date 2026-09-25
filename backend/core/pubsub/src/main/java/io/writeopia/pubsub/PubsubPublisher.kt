package io.writeopia.pubsub

import com.google.cloud.pubsub.v1.Publisher
import com.google.protobuf.ByteString
import com.google.pubsub.v1.PubsubMessage
import com.google.pubsub.v1.TopicName
import io.writeopia.connection.logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * Thin wrapper around the Pub/Sub client for direct (non-outbox) publishes - used by the
 * account-deletion saga's completion legs (documents -> auth, media -> auth), which are
 * deliberately NOT routed through the transactional outbox/Debezium: their reliability
 * comes entirely from standard Pub/Sub at-least-once redelivery of the inbound message
 * that triggered them (the caller only acks that inbound message after this publish call
 * succeeds), not from any DB-transactional atomicity with the publish itself.
 */
object PubsubPublisher {
    private val projectId: String? get() = System.getenv("GCP_PROJECT")

    // One Publisher per topic, reused across calls - each holds its own gRPC channel/batching
    // and is meant to be long-lived, not recreated per publish.
    private val publishers = ConcurrentHashMap<String, Publisher>()

    private fun publisherFor(topicId: String): Publisher =
        publishers.computeIfAbsent(topicId) { id ->
            val project = projectId
                ?: throw IllegalStateException("Environment variable 'GCP_PROJECT' is not set.")
            Publisher.newBuilder(TopicName.of(project, id)).build()
        }

    /**
     * Publishes [payload] to [topicId], keyed by [orderingKey] (set as the "key" message
     * attribute; topics in this saga don't use Pub/Sub ordering keys, this is just for
     * traceability in logs/dead-letter inspection).
     *
     * In [debugMode] (local/test), this only logs - no real Pub/Sub call is made, matching
     * the rest of this codebase's debugMode convention (see GcpBucketImageStorageService).
     */
    suspend fun publish(
        topicId: String,
        orderingKey: String,
        payload: String,
        debugMode: Boolean = false,
    ) {
        if (debugMode) {
            logger.info("[Pub/Sub] (debugMode, not sent) topic=$topicId key=$orderingKey payload=$payload")
            return
        }

        withContext(Dispatchers.IO) {
            val message = PubsubMessage.newBuilder()
                .setData(ByteString.copyFromUtf8(payload))
                .putAttributes("key", orderingKey)
                .build()

            // .get() blocks this IO-dispatcher thread until Pub/Sub acks the publish or the
            // call fails - a failure here must propagate so the caller's push handler errors
            // out and leaves the inbound message unacked (see class doc).
            publisherFor(topicId).publish(message).get()
        }
    }
}
