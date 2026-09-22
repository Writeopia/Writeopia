package io.writeopia.api.core.auth.service

import io.writeopia.api.core.auth.configureTestPersistence
import io.writeopia.api.core.auth.models.AccountDeletion
import io.writeopia.api.core.auth.models.AccountDeletionEventTypes
import io.writeopia.api.core.auth.models.UserStatus
import io.writeopia.api.core.auth.repository.getAccountDeletionByUserId
import io.writeopia.api.core.auth.repository.getUserById
import io.writeopia.api.core.auth.repository.insertUser
import io.writeopia.sql.WriteopiaDbBackend
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AccountDeletionReconciliationServiceTest {

    private lateinit var db: WriteopiaDbBackend
    private val userId = "test-user-${UUID.randomUUID()}"
    private val userEmail = "test-${UUID.randomUUID()}@example.com"

    private val oneDayMs = 24 * 60 * 60 * 1000L

    @BeforeTest
    fun setUp() {
        db = configureTestPersistence()
        db.insertUser(
            id = userId,
            name = "Test User",
            username = "user_name",
            email = userEmail,
            password = "hashedpassword",
            salt = "salt",
            status = UserStatus.DELETION_PENDING,
        )
    }

    @AfterTest
    fun tearDown() {
        db.outboxEventQueries.deleteByAggregateId(userId)
        db.accountDeletionQueries.deleteByUserId(userId)
        db.userEntityQueries.deleteUser(userId)
    }

    private fun insertAccountDeletionDirect(
        requestedAt: Long,
        workspacesCompletedAt: Long? = null,
        mediaCompletedAt: Long? = null,
        status: String = AccountDeletion.STATUS_REQUESTED,
    ) {
        db.accountDeletionQueries.insert(
            user_id = userId,
            user_email = userEmail,
            user_name = "Test User",
            status = status,
            requested_at = requestedAt,
            workspaces_completed_at = workspacesCompletedAt,
            media_completed_at = mediaCompletedAt,
            completed_at = null,
        )
    }

    @Test
    fun `a stuck deletion with neither leg done gets nudged with a fresh REQUESTED outbox event`() {
        val requestedAt = System.currentTimeMillis() - (2 * oneDayMs)
        insertAccountDeletionDirect(requestedAt = requestedAt)

        val actedOn = AccountDeletionReconciliationService.reconcileStuckDeletions(db)

        assertEquals(1, actedOn)
        val events = db.outboxEventQueries.selectByAggregateId(userId).executeAsList()
        assertEquals(1, events.size)
        assertEquals(AccountDeletionEventTypes.REQUESTED, events[0].event_type)

        // Not finalized - reconciliation only nudges, it doesn't know the legs are actually done.
        assertNotNull(db.getUserById(userId))
        assertEquals(AccountDeletion.STATUS_REQUESTED, db.getAccountDeletionByUserId(userId)?.status)
    }

    @Test
    fun `a stuck deletion where both legs already completed gets finalized instead of nudged`() {
        val requestedAt = System.currentTimeMillis() - (2 * oneDayMs)
        insertAccountDeletionDirect(
            requestedAt = requestedAt,
            workspacesCompletedAt = requestedAt + 1000,
            mediaCompletedAt = requestedAt + 2000,
        )

        val actedOn = AccountDeletionReconciliationService.reconcileStuckDeletions(db)

        assertEquals(1, actedOn)
        assertNull(db.getUserById(userId), "finalize should have hard-deleted the user")
        assertEquals("COMPLETED", db.getAccountDeletionByUserId(userId)?.status)

        val events = db.outboxEventQueries.selectByAggregateId(userId).executeAsList()
        assertEquals(1, events.size, "should finalize, not nudge - no REQUESTED event")
        assertEquals(AccountDeletionEventTypes.FINALIZED, events[0].event_type)
    }

    @Test
    fun `a recently-requested deletion is left alone`() {
        insertAccountDeletionDirect(requestedAt = System.currentTimeMillis())

        val actedOn = AccountDeletionReconciliationService.reconcileStuckDeletions(db)

        assertEquals(0, actedOn)
        assertTrue(db.outboxEventQueries.selectByAggregateId(userId).executeAsList().isEmpty())
        assertNotNull(db.getUserById(userId))
    }

    @Test
    fun `an already-completed deletion is left alone even if old`() {
        val requestedAt = System.currentTimeMillis() - (30 * oneDayMs)
        insertAccountDeletionDirect(
            requestedAt = requestedAt,
            workspacesCompletedAt = requestedAt,
            mediaCompletedAt = requestedAt,
            status = "COMPLETED",
        )

        val actedOn = AccountDeletionReconciliationService.reconcileStuckDeletions(db)

        assertEquals(0, actedOn)
        assertTrue(db.outboxEventQueries.selectByAggregateId(userId).executeAsList().isEmpty())
    }
}
