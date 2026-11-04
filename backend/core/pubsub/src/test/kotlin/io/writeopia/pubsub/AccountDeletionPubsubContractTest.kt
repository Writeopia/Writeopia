package io.writeopia.pubsub

import kotlinx.serialization.json.Json
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins the account-deletion Pub/Sub contract that other services and the external infra
 * (pubsub.tf, Debezium outbox router) depend on: the push envelope round-trips into the
 * shared payload, and the topic/event/aggregate names stay literal.
 */
class AccountDeletionPubsubContractTest {

    @Test
    fun `decodes a pubsub push envelope into the account deletion payload`() {
        val payload = """{"userId":"user-1"}"""
        val encoded = Base64.getEncoder().encodeToString(payload.encodeToByteArray())
        val pushBody = """{"message":{"data":"$encoded"}}"""

        val decoded = decodePubsubPushData(pushBody)

        assertEquals(
            AccountDeletionEventPayload(userId = "user-1"),
            Json.decodeFromString(AccountDeletionEventPayload.serializer(), decoded),
        )
    }

    @Test
    fun `topic and outbox names match the external contract`() {
        assertEquals("account-deletion-requested", AccountDeletionTopics.REQUESTED)
        assertEquals("account-deletion-workspaces-completed", AccountDeletionTopics.WORKSPACES_COMPLETED)
        assertEquals("account-deletion-media-completed", AccountDeletionTopics.MEDIA_COMPLETED)
        assertEquals("account-deletion-finalized", AccountDeletionTopics.FINALIZED)
        assertEquals("AccountDeletionRequested", AccountDeletionEventTypes.REQUESTED)
        assertEquals("AccountDeletionFinalized", AccountDeletionEventTypes.FINALIZED)
        assertEquals("user", ACCOUNT_DELETION_AGGREGATE_TYPE)
    }
}
