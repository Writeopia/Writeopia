package io.writeopia.api.documents.documents.service

import io.writeopia.api.core.auth.models.AccountDeletionTopics
import io.writeopia.api.core.auth.models.UserStatus
import io.writeopia.api.core.auth.repository.insertUser
import io.writeopia.api.documents.documents.configureTestPersistence
import io.writeopia.api.documents.documents.repository.AccountDeletionWorkspaceAction
import io.writeopia.api.documents.documents.repository.getAccountDeletionWorkspaceRows
import io.writeopia.sdk.models.workspace.Role
import io.writeopia.sql.WriteopiaDbBackend
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * Covers the sole-admin decision (DELETE_WORKSPACE vs REMOVE_MEMBERSHIP) and the resulting
 * cascade/membership-removal, using the injectable `publish` parameter (see
 * AccountDeletionWorkspaceService's doc) instead of the real PubsubPublisher.
 */
class AccountDeletionWorkspaceServiceTest {

    private lateinit var db: WriteopiaDbBackend
    private val userId = "test-user-${UUID.randomUUID()}"
    private val otherAdminId = "other-admin-${UUID.randomUUID()}"
    private val soleAdminWorkspaceId = "workspace-sole-${UUID.randomUUID()}"
    private val sharedWorkspaceId = "workspace-shared-${UUID.randomUUID()}"
    private val editorWorkspaceId = "workspace-editor-${UUID.randomUUID()}"
    private val documentId = "document-${UUID.randomUUID()}"
    private val editorFavoriteDocumentId = "favorite-document-${UUID.randomUUID()}"

    @BeforeTest
    fun setUp() {
        db = configureTestPersistence()

        db.insertUser(
            id = userId,
            name = "Test User",
            username = "user_name",
            email = "test-${UUID.randomUUID()}@example.com",
            password = "p",
            salt = "s",
            status = UserStatus.DELETION_PENDING,
        )
        db.insertUser(
            id = otherAdminId,
            name = "Other Admin",
            username = "user_name",
            email = "other-${UUID.randomUUID()}@example.com",
            password = "p",
            salt = "s",
            status = UserStatus.ACTIVE,
        )

        // Workspace where the test user is the SOLE admin - should be fully torn down.
        db.workspaceEntityQueries.insert(id = soleAdminWorkspaceId, name = "Sole admin workspace", icon = null, icon_tint = null)
        db.workspaceToUserQueries.insertWorkspaceToUser(workspace_id = soleAdminWorkspaceId, user_id = userId, role = Role.ADMIN.value)
        db.documentEntityQueries.insert(
            id = documentId,
            title = "Doc",
            created_at = 0,
            last_updated_at = 0,
            last_synced = 0,
            workspace_id = soleAdminWorkspaceId,
            favorite = false,
            parent_document_id = "",
            icon = null,
            icon_tint = null,
            is_locked = false,
            company_id = null,
            deleted = false,
            published = false,
        )

        // Workspace shared with another admin (user is themselves ADMIN, but not the sole one)
        // - the user should just be removed as a member.
        db.workspaceEntityQueries.insert(id = sharedWorkspaceId, name = "Shared workspace", icon = null, icon_tint = null)
        db.workspaceToUserQueries.insertWorkspaceToUser(workspace_id = sharedWorkspaceId, user_id = userId, role = Role.ADMIN.value)
        db.workspaceToUserQueries.insertWorkspaceToUser(workspace_id = sharedWorkspaceId, user_id = otherAdminId, role = Role.ADMIN.value)

        // Workspace where the user is a plain EDITOR, not an admin at all - a separate code
        // path through the same REMOVE_MEMBERSHIP branch as the shared-admin case above. Also
        // covers the per-user cleanup (favorites, tutorial status) that branch is supposed to
        // do, which the shared-admin case above doesn't exercise.
        db.workspaceEntityQueries.insert(id = editorWorkspaceId, name = "Editor workspace", icon = null, icon_tint = null)
        db.workspaceToUserQueries.insertWorkspaceToUser(workspace_id = editorWorkspaceId, user_id = userId, role = Role.EDITOR.value)
        db.workspaceToUserQueries.insertWorkspaceToUser(workspace_id = editorWorkspaceId, user_id = otherAdminId, role = Role.ADMIN.value)
        db.userFavoriteEntityQueries.insert(
            user_id = userId,
            document_id = editorFavoriteDocumentId,
            workspace_id = editorWorkspaceId,
            created_at = 0,
        )
        db.workspaceTutorialStatusQueries.setTutorialStatus(
            workspace_id = editorWorkspaceId,
            user_id = userId,
            tutorials_created = 1,
            created_at = 0,
        )
    }

    @AfterTest
    fun tearDown() {
        db.accountDeletionWorkspaceQueries.deleteByUserId(userId)
        db.documentEntityQueries.hardDeleteByWorkspaceId(soleAdminWorkspaceId)
        db.workspaceToUserQueries.deleteByWorkspaceId(soleAdminWorkspaceId)
        db.workspaceEntityQueries.delete(soleAdminWorkspaceId)
        db.workspaceToUserQueries.deleteByWorkspaceId(sharedWorkspaceId)
        db.workspaceEntityQueries.delete(sharedWorkspaceId)
        db.userFavoriteEntityQueries.deleteByWorkspace(editorWorkspaceId)
        db.workspaceTutorialStatusQueries.deleteByWorkspace(editorWorkspaceId)
        db.workspaceToUserQueries.deleteByWorkspaceId(editorWorkspaceId)
        db.workspaceEntityQueries.delete(editorWorkspaceId)
        db.userEntityQueries.deleteUser(userId)
        db.userEntityQueries.deleteUser(otherAdminId)
    }

    @Test
    fun `sole-admin workspaces are torn down, non-sole-admin workspaces just lose the membership - whether co-admin or plain editor`() = runTest {
        val published = mutableListOf<Triple<String, String, String>>()

        AccountDeletionWorkspaceService.handleAccountDeletionRequested(
            userId = userId,
            writeopiaDb = db,
            debugMode = false,
            publish = { topicId, orderingKey, payload, _ -> published.add(Triple(topicId, orderingKey, payload)) },
        )

        // Sole-admin workspace: gone entirely, including its documents.
        assertNull(db.workspaceEntityQueries.getWorkspaceById(soleAdminWorkspaceId).executeAsOneOrNull())
        assertTrue(db.documentEntityQueries.selectAllIdsByWorkspaceId(soleAdminWorkspaceId).executeAsList().isEmpty())

        // Shared workspace (user was co-admin): still exists, other admin still a member, test
        // user is not.
        assertNotNull(db.workspaceEntityQueries.getWorkspaceById(sharedWorkspaceId).executeAsOneOrNull())
        assertNull(db.workspaceToUserQueries.getUserRoleInWorkspace(sharedWorkspaceId, userId).executeAsOneOrNull())
        assertEquals(
            Role.ADMIN.value,
            db.workspaceToUserQueries.getUserRoleInWorkspace(sharedWorkspaceId, otherAdminId).executeAsOneOrNull(),
        )

        // Editor workspace (user was a plain, non-admin member): still exists, other admin
        // still a member, test user's membership AND their per-user data in it (favorites,
        // tutorial status) are gone.
        assertNotNull(db.workspaceEntityQueries.getWorkspaceById(editorWorkspaceId).executeAsOneOrNull())
        assertNull(db.workspaceToUserQueries.getUserRoleInWorkspace(editorWorkspaceId, userId).executeAsOneOrNull())
        assertEquals(
            Role.ADMIN.value,
            db.workspaceToUserQueries.getUserRoleInWorkspace(editorWorkspaceId, otherAdminId).executeAsOneOrNull(),
        )
        assertTrue(
            db.userFavoriteEntityQueries.selectByUserAndWorkspace(userId, editorWorkspaceId).executeAsList().isEmpty(),
            "the user's favorite in the workspace they left should be gone",
        )
        assertNull(
            db.workspaceTutorialStatusQueries.getTutorialStatus(editorWorkspaceId, userId).executeAsOneOrNull(),
            "the user's tutorial-status row in the workspace they left should be gone",
        )

        // Tracking rows reflect the right action per workspace, all marked completed.
        val rows = db.getAccountDeletionWorkspaceRows(userId).associateBy { it.workspaceId }
        assertEquals(AccountDeletionWorkspaceAction.DELETE_WORKSPACE, rows[soleAdminWorkspaceId]?.action)
        assertTrue(rows[soleAdminWorkspaceId]?.completed == true)
        assertEquals(AccountDeletionWorkspaceAction.REMOVE_MEMBERSHIP, rows[sharedWorkspaceId]?.action)
        assertTrue(rows[sharedWorkspaceId]?.completed == true)
        assertEquals(AccountDeletionWorkspaceAction.REMOVE_MEMBERSHIP, rows[editorWorkspaceId]?.action)
        assertTrue(rows[editorWorkspaceId]?.completed == true)

        assertEquals(1, published.size)
        assertEquals(AccountDeletionTopics.WORKSPACES_COMPLETED, published[0].first)
        assertEquals(userId, published[0].second)
    }

    @Test
    fun `redelivery resumes rather than re-enumerating, and is still safe to call again`() = runTest {
        var callCount = 0
        val fakePublish: suspend (String, String, String, Boolean) -> Unit = { _, _, _, _ -> callCount++ }

        AccountDeletionWorkspaceService.handleAccountDeletionRequested(userId, db, false, fakePublish)
        val rowsAfterFirst = db.getAccountDeletionWorkspaceRows(userId)

        // Simulates a redelivered Pub/Sub message for the same user after everything already
        // completed - must not error, must not duplicate/re-enumerate the tracking rows.
        AccountDeletionWorkspaceService.handleAccountDeletionRequested(userId, db, false, fakePublish)
        val rowsAfterSecond = db.getAccountDeletionWorkspaceRows(userId)

        assertEquals(rowsAfterFirst.size, rowsAfterSecond.size)
        assertEquals(2, callCount, "completion is republished on redelivery - that's expected, not a bug")
    }
}
