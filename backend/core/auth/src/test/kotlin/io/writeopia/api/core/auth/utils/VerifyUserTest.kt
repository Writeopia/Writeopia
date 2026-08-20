package io.writeopia.api.core.auth.utils

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.RoutingCall
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.RoutingResponse
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.writeopia.api.core.auth.repository.isUserAdminInWorkspace
import io.writeopia.api.core.auth.repository.isUserInWorkspace
import io.writeopia.sql.WriteopiaDbBackend
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for runIfAdmin and runIfMember permission check functions.
 *
 * These tests verify that:
 * 1. When permission check passes, the block is executed and function returns
 * 2. When permission check fails, 403 Forbidden is returned
 * 3. The function does NOT fall through to 403 after executing the block (the bug that was fixed)
 */
class VerifyUserTest {

    /**
     * This test verifies the fix for the bug where runIfAdmin would always
     * respond with 403 Forbidden even after successfully executing the block.
     *
     * The bug was:
     * ```kotlin
     * if (shouldContinue || debug) {
     *     block()
     *     // Missing return here!
     * }
     * this.call.respond(HttpStatusCode.Forbidden) // Always executed!
     * ```
     *
     * The fix adds a return statement after block() to prevent falling through.
     */
    @Test
    fun `runIfAdmin should return after executing block for admin user`() = runTest {
        // Given: a mock context where user is admin
        val db = mockk<WriteopiaDbBackend>()
        val call = mockk<RoutingCall>(relaxed = true)
        val response = mockk<RoutingResponse>(relaxed = true)
        val context = mockk<RoutingContext>()

        every { context.call } returns call
        every { call.response } returns response

        // Mock the database query that checks admin status
        every {
            db.workspaceEntityQueries.getWorkspacesByUserIdIfAdmin("user123").executeAsList()
        } returns listOf(
            mockk {
                every { workspace_id } returns "workspace456"
            }
        )

        var blockExecuted = false
        var respondCalledWithForbidden = false

        coEvery { call.respond(HttpStatusCode.Forbidden) } answers {
            respondCalledWithForbidden = true
        }
        coEvery { call.respond(HttpStatusCode.Accepted, any<Any>()) } returns Unit

        // When: runIfAdmin is called with an admin user
        context.runIfAdmin("user123", "workspace456", db, debug = false) {
            blockExecuted = true
            call.respond(HttpStatusCode.Accepted, "Export started")
        }

        // Then: block should be executed and Forbidden should NOT be called
        assertTrue(blockExecuted, "Block should be executed for admin user")
        assertFalse(respondCalledWithForbidden, "Forbidden should NOT be called after block execution")
    }

    @Test
    fun `runIfAdmin should respond with 403 for non-admin user`() = runTest {
        // Given: a mock context where user is NOT admin
        val db = mockk<WriteopiaDbBackend>()
        val call = mockk<RoutingCall>(relaxed = true)
        val response = mockk<RoutingResponse>(relaxed = true)
        val context = mockk<RoutingContext>()

        every { context.call } returns call
        every { call.response } returns response

        // Mock the database query to return empty (user is not admin)
        every {
            db.workspaceEntityQueries.getWorkspacesByUserIdIfAdmin("user123").executeAsList()
        } returns emptyList()

        var blockExecuted = false
        val statusSlot = slot<HttpStatusCode>()

        coEvery { call.respond(capture(statusSlot)) } returns Unit

        // When: runIfAdmin is called with a non-admin user
        context.runIfAdmin("user123", "workspace456", db, debug = false) {
            blockExecuted = true
        }

        // Then: block should NOT be executed and should respond with 403
        assertFalse(blockExecuted, "Block should NOT be executed for non-admin user")
        assertEquals(HttpStatusCode.Forbidden, statusSlot.captured)
    }

    @Test
    fun `runIfAdmin should execute block in debug mode even for non-admin`() = runTest {
        // Given: a mock context where user is NOT admin but debug mode is on
        val db = mockk<WriteopiaDbBackend>()
        val call = mockk<RoutingCall>(relaxed = true)
        val response = mockk<RoutingResponse>(relaxed = true)
        val context = mockk<RoutingContext>()

        every { context.call } returns call
        every { call.response } returns response

        // Mock the database query to return empty (user is not admin)
        every {
            db.workspaceEntityQueries.getWorkspacesByUserIdIfAdmin("user123").executeAsList()
        } returns emptyList()

        var blockExecuted = false
        var respondCalledWithForbidden = false

        coEvery { call.respond(HttpStatusCode.Forbidden) } answers {
            respondCalledWithForbidden = true
        }
        coEvery { call.respond(HttpStatusCode.Accepted, any<Any>()) } returns Unit

        // When: runIfAdmin is called with debug = true
        context.runIfAdmin("user123", "workspace456", db, debug = true) {
            blockExecuted = true
            call.respond(HttpStatusCode.Accepted, "Export started")
        }

        // Then: block should be executed (debug bypasses check) and Forbidden should NOT be called
        assertTrue(blockExecuted, "Block should be executed in debug mode")
        assertFalse(respondCalledWithForbidden, "Forbidden should NOT be called in debug mode")
    }

    @Test
    fun `runIfMember should return after executing block for member user`() = runTest {
        // Given: a mock context where user is a member
        val db = mockk<WriteopiaDbBackend>()
        val call = mockk<RoutingCall>(relaxed = true)
        val response = mockk<RoutingResponse>(relaxed = true)
        val context = mockk<RoutingContext>()

        every { context.call } returns call
        every { call.response } returns response

        // Mock the database query that checks member status
        every {
            db.workspaceEntityQueries.getWorkspacesByUserId("user123").executeAsList()
        } returns listOf(
            mockk {
                every { workspace_id } returns "workspace456"
            }
        )

        var blockExecuted = false
        var respondCalledWithForbidden = false

        coEvery { call.respond(HttpStatusCode.Forbidden) } answers {
            respondCalledWithForbidden = true
        }
        coEvery { call.respond(HttpStatusCode.OK, any<Any>()) } returns Unit

        // When: runIfMember is called with a member user
        context.runIfMember("user123", "workspace456", db, debug = false) {
            blockExecuted = true
            call.respond(HttpStatusCode.OK, "Success")
        }

        // Then: block should be executed and Forbidden should NOT be called
        assertTrue(blockExecuted, "Block should be executed for member user")
        assertFalse(respondCalledWithForbidden, "Forbidden should NOT be called after block execution")
    }

    @Test
    fun `runIfMember should respond with 403 for non-member user`() = runTest {
        // Given: a mock context where user is NOT a member
        val db = mockk<WriteopiaDbBackend>()
        val call = mockk<RoutingCall>(relaxed = true)
        val response = mockk<RoutingResponse>(relaxed = true)
        val context = mockk<RoutingContext>()

        every { context.call } returns call
        every { call.response } returns response

        // Mock the database query to return empty (user is not a member)
        every {
            db.workspaceEntityQueries.getWorkspacesByUserId("user123").executeAsList()
        } returns emptyList()

        var blockExecuted = false
        val statusSlot = slot<HttpStatusCode>()

        coEvery { call.respond(capture(statusSlot)) } returns Unit

        // When: runIfMember is called with a non-member user
        context.runIfMember("user123", "workspace456", db, debug = false) {
            blockExecuted = true
        }

        // Then: block should NOT be executed and should respond with 403
        assertFalse(blockExecuted, "Block should NOT be executed for non-member user")
        assertEquals(HttpStatusCode.Forbidden, statusSlot.captured)
    }

    @Test
    fun `runIfAdmin should not call respond with Forbidden after block responds`() = runTest {
        // This test specifically verifies the bug fix where respond(Forbidden)
        // was being called AFTER the block already responded
        val db = mockk<WriteopiaDbBackend>()
        val call = mockk<RoutingCall>(relaxed = true)
        val response = mockk<RoutingResponse>(relaxed = true)
        val context = mockk<RoutingContext>()

        every { context.call } returns call
        every { call.response } returns response

        every {
            db.workspaceEntityQueries.getWorkspacesByUserIdIfAdmin("user123").executeAsList()
        } returns listOf(
            mockk {
                every { workspace_id } returns "workspace456"
            }
        )

        coEvery { call.respond(any<HttpStatusCode>(), any<Any>()) } returns Unit
        coEvery { call.respond(any<HttpStatusCode>()) } returns Unit

        // When
        context.runIfAdmin("user123", "workspace456", db, debug = false) {
            call.respond(HttpStatusCode.Accepted, "Export started")
        }

        // Then: verify respond was called only once with Accepted, never with Forbidden
        coVerify(exactly = 1) { call.respond(HttpStatusCode.Accepted, "Export started") }
        coVerify(exactly = 0) { call.respond(HttpStatusCode.Forbidden) }
    }
}
