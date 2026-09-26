package io.writeopia.pubsub

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.Base64

/**
 * The JSON envelope Cloud Pub/Sub wraps every push request body in
 * (https://cloud.google.com/pubsub/docs/push). `message.data` is base64 of the actual
 * event payload - for this saga, that's always the same JSON shape Debezium's outbox-event-
 * router forwards verbatim from outbox_event.payload, or that a direct publish() call sent,
 * so every internal event endpoint decodes it the same way and then parses the result into
 * its own domain payload type (e.g. AccountDeletionEventPayload).
 */
@Serializable
private data class PubsubPushEnvelope(
    val message: PubsubPushMessage,
    val subscription: String? = null,
)

@Serializable
private data class PubsubPushMessage(
    val data: String,
    val messageId: String? = null,
    val publishTime: String? = null,
)

private val envelopeJson = Json { ignoreUnknownKeys = true }

/** Decodes a Pub/Sub push request body down to the raw (still-JSON-encoded) event payload string. */
fun decodePubsubPushData(requestBody: String): String {
    val envelope = envelopeJson.decodeFromString(PubsubPushEnvelope.serializer(), requestBody)
    return String(Base64.getDecoder().decode(envelope.message.data))
}
