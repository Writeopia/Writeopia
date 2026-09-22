package io.writeopia.api.core.auth.service

import io.writeopia.api.core.auth.configureTestPersistence
import io.writeopia.api.core.auth.models.AccountDeletionEventTypes
import io.writeopia.api.core.auth.models.AccountDeletionTopics
import io.writeopia.api.core.auth.models.UserStatus
import io.writeopia.api.core.auth.repository.getAccountDeletionByUserId
import io.writeopia.api.core.auth.repository.getUserById
import io.writeopia.api.core.auth.repository.getUserStatus
import io.writeopia.api.core.auth.repository.insertUser
import io.writeopia.sql.WriteopiaDbBackend
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Covers the properties the account-deletion saga specifically depends on for correctness:
 * requestDeletion's atomicity/idempotency (no exception-based race handling - see its doc),
 * and checkAndFinalize's guarded finalize (exactly-once, even if both completion legs race).
 */
class AccountDeletionServiceTest {

    private lateinit var db: WriteopiaDbBackend
    private val userId = "test-user-${UUID.randomUUID()}"
    private val userEmail = "test-${UUID.randomUUID()}@example.com"

    @BeforeTest
    fun setUp() {
        db = configureTestPersistence()
        db.insertUser(
            id = userId,
            name = "Test User",
            email = userEmail,
            password = "hashedpassword",
            salt = "salt",
            status = UserStatus.ACTIVE,
        )
    }

    @AfterTest
    fun tearDown() {
        db.outboxEventQueries.deleteByAggregateId(userId)
        db.accountDeletionQueries.deleteByUserId(userId)
        db.userEntityQueries.deleteUser(userId)
    }

    @Test
    fun `requestDeletion flips user status, creates the tracking row, and inserts exactly one REQUESTED outbox event`() {
        val deletion = AccountDeletionService.requestDeletion(userId, db)

        assertNotNull(deletion, "requestDeletion should return the new tracking row")
        assertEquals(userId, deletion.userId)
        assertEquals(userEmail, deletion.userEmail)
        assertNull(deletion.workspacesCompletedAt)
        assertNull(deletion.mediaCompletedAt)
        assertNull(deletion.completedAt)

        assertEquals(UserStatus.DELETION_PENDING, db.getUserStatus(userId))

        val outboxEvents = db.outboxEventQueries.selectByAggregateId(userId).executeAsList()
        assertEquals(1, outboxEvents.size, "exactly one outbox event should be inserted")
        assertEquals(AccountDeletionTopics.REQUESTED, outboxEvents[0].topic)
        assertEquals(AccountDeletionEventTypes.REQUESTED, outboxEvents[0].event_type)
    }

    @Test
    fun `requestDeletion is idempotent - a repeated call returns the same row and does not insert a second outbox event`() {
        val first = AccountDeletionService.requestDeletion(userId, db)
        val second = AccountDeletionService.requestDeletion(userId, db)

        assertNotNull(first)
        assertNotNull(second)
        assertEquals(first.requestedAt, second.requestedAt, "the second call must not overwrite the original row")

        val outboxEvents = db.outboxEventQueries.selectByAggregateId(userId).executeAsList()
        assertEquals(1, outboxEvents.size, "a racing/repeated request must not double-fire the outbox event")
    }

    @Test
    fun `requestDeletion returns null for a user that does not exist`() {
        val result = AccountDeletionService.requestDeletion("no-such-user-${UUID.randomUUID()}", db)
        assertNull(result)
    }

    @Test
    fun `finalize does not happen until both legs are complete`() {
        AccountDeletionService.requestDeletion(userId, db)

        AccountDeletionService.handleWorkspacesCompleted(userId, db)

        val afterOneLeg = db.getAccountDeletionByUserId(userId)
        assertNotNull(afterOneLeg)
        assertNotNull(afterOneLeg.workspacesCompletedAt)
        assertNull(afterOneLeg.mediaCompletedAt)
        assertNotEquals("COMPLETED", afterOneLeg.status)
        assertNotNull(db.getUserById(userId), "user must still exist - only one leg has completed")
    }

    @Test
    fun `when both legs complete, the user is deleted, status flips to COMPLETED, and exactly one FINALIZED outbox event is inserted`() {
        AccountDeletionService.requestDeletion(userId, db)

        AccountDeletionService.handleWorkspacesCompleted(userId, db)
        AccountDeletionService.handleMediaCompleted(userId, db)

        val deletion = db.getAccountDeletionByUserId(userId)
        assertNotNull(deletion)
        assertEquals("COMPLETED", deletion.status)
        assertNotNull(deletion.completedAt)
        assertNull(db.getUserById(userId), "user_entity row should be hard-deleted once finalized")

        val finalizedEvents = db.outboxEventQueries.selectByAggregateId(userId).executeAsList()
            .filter { it.event_type == AccountDeletionEventTypes.FINALIZED }
        assertEquals(1, finalizedEvents.size)
        assertEquals(AccountDeletionTopics.FINALIZED, finalizedEvents[0].topic)
    }

    @Test
    fun `checkAndFinalize is race-safe - calling it again after both legs are already done is a harmless no-op`() {
        AccountDeletionService.requestDeletion(userId, db)
        AccountDeletionService.handleWorkspacesCompleted(userId, db)
        AccountDeletionService.handleMediaCompleted(userId, db)

        // Simulates the two completion push endpoints racing: both see both legs done and
        // both call checkAndFinalize. Only the first should have actually done anything -
        // this call must not throw (e.g. re-deleting an already-deleted user) and must not
        // insert a second FINALIZED event.
        AccountDeletionService.checkAndFinalize(userId, db)

        val finalizedEvents = db.outboxEventQueries.selectByAggregateId(userId).executeAsList()
            .filter { it.event_type == AccountDeletionEventTypes.FINALIZED }
        assertEquals(1, finalizedEvents.size, "the guarded UPDATE must prevent a second finalize")
    }

    @Test
    fun `handleWorkspacesCompleted is idempotent - a repeated call does not move the completed timestamp`() {
        AccountDeletionService.requestDeletion(userId, db)
        AccountDeletionService.handleWorkspacesCompleted(userId, db)
        val firstTimestamp = db.getAccountDeletionByUserId(userId)?.workspacesCompletedAt
        assertNotNull(firstTimestamp)

        AccountDeletionService.handleWorkspacesCompleted(userId, db)
        val secondTimestamp = db.getAccountDeletionByUserId(userId)?.workspacesCompletedAt

        assertEquals(firstTimestamp, secondTimestamp)
    }

    @Test
    fun `checkAndFinalize does nothing when called before requestDeletion`() {
        AccountDeletionService.checkAndFinalize(userId, db)

        assertTrue(db.outboxEventQueries.selectByAggregateId(userId).executeAsList().isEmpty())
        assertNotNull(db.getUserById(userId))
    }
}
