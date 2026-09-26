package io.writeopia.api.documents.documents.repository

import io.writeopia.sql.WriteopiaDbBackend

object AccountDeletionWorkspaceAction {
    const val DELETE_WORKSPACE = "DELETE_WORKSPACE"
    const val REMOVE_MEMBERSHIP = "REMOVE_MEMBERSHIP"
}

data class AccountDeletionWorkspaceRow(
    val userId: String,
    val workspaceId: String,
    val action: String,
    val completed: Boolean,
)

private fun io.writeopia.sql.Account_deletion_workspace.toModel() =
    AccountDeletionWorkspaceRow(
        userId = user_id,
        workspaceId = workspace_id,
        action = action,
        completed = completed,
    )

// ON CONFLICT DO NOTHING in the underlying query (see AccountDeletionWorkspace.sq) makes this
// safe to call again for a (userId, workspaceId) pair already inserted - enumeration on a
// redelivered account-deletion-requested message is idempotent.
fun WriteopiaDbBackend.insertAccountDeletionWorkspace(userId: String, workspaceId: String, action: String) {
    this.accountDeletionWorkspaceQueries.insert(
        user_id = userId,
        workspace_id = workspaceId,
        action = action,
        completed = false,
        completed_at = null,
    )
}

fun WriteopiaDbBackend.getAccountDeletionWorkspaceRows(userId: String): List<AccountDeletionWorkspaceRow> =
    this.accountDeletionWorkspaceQueries.selectByUserId(userId).executeAsList().map { it.toModel() }

fun WriteopiaDbBackend.getIncompleteAccountDeletionWorkspaceRows(userId: String): List<AccountDeletionWorkspaceRow> =
    this.accountDeletionWorkspaceQueries.selectIncompleteByUserId(userId).executeAsList().map { it.toModel() }

// Non-suspend: called as the literal last statement inside the writeopiaDb.transaction {}
// block that does a workspace's (or a membership's) teardown - see
// AccountDeletionWorkspaceService.
fun WriteopiaDbBackend.markAccountDeletionWorkspaceCompleted(userId: String, workspaceId: String, completedAt: Long) {
    this.accountDeletionWorkspaceQueries.markCompleted(completedAt, userId, workspaceId)
}

/**
 * Unconditional hard-delete of everything in [workspaceId]: story steps (including ones
 * belonging to documents the user had already soft-deleted - see selectAllIdsByWorkspaceId's
 * doc), documents, folders, all members' favorites and tutorial-status rows, all
 * memberships, and finally the workspace row itself. Non-suspend - must be called inside a
 * writeopiaDb.transaction {} block (see AccountDeletionWorkspaceService), NOT standalone,
 * since a crash partway through must not leave a half-deleted workspace.
 */
fun WriteopiaDbBackend.hardDeleteWorkspaceContents(workspaceId: String) {
    val documentIds = this.documentEntityQueries.selectAllIdsByWorkspaceId(workspaceId).executeAsList()
    if (documentIds.isNotEmpty()) {
        this.storyStepEntityQueries.deleteByDocumentIds(documentIds)
    }
    this.documentEntityQueries.hardDeleteByWorkspaceId(workspaceId)
    this.folderEntityQueries.deleteByWorkspace(workspaceId)
    this.userFavoriteEntityQueries.deleteByWorkspace(workspaceId)
    this.workspaceTutorialStatusQueries.deleteByWorkspace(workspaceId)
    this.workspaceToUserQueries.deleteByWorkspaceId(workspaceId)
    this.workspaceEntityQueries.delete(workspaceId)
}

/**
 * Removes just [userId]'s membership and per-user data in [workspaceId], leaving the
 * workspace and every other member's data untouched. Non-suspend - called inside a
 * writeopiaDb.transaction {} block.
 */
fun WriteopiaDbBackend.removeUserFromWorkspaceContents(userId: String, workspaceId: String) {
    this.workspaceToUserQueries.removeUserFromWorkspace(workspaceId, userId)
    this.userFavoriteEntityQueries.deleteByUserAndWorkspace(userId, workspaceId)
    this.workspaceTutorialStatusQueries.deleteByWorkspaceAndUser(workspaceId, userId)
}

/**
 * Final sweep once every workspace row for a deletion is completed: catches any leftover
 * per-user rows in workspaces that were kept (REMOVE_MEMBERSHIP branch already cleans its own
 * workspace, but this is a cheap unconditional safety net rather than tracking exactly which
 * workspaces still need it). Non-suspend - called inside a writeopiaDb.transaction {} block.
 */
fun WriteopiaDbBackend.sweepRemainingUserAccountData(userId: String) {
    this.userFavoriteEntityQueries.deleteByUser(userId)
    this.workspaceTutorialStatusQueries.deleteByUser(userId)
}
