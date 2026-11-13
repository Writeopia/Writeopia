package io.writeopia.persistence.room.data.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Transaction
import androidx.room.Query
import io.writeopia.persistence.room.data.entities.WorkspaceEntity

@Dao
interface WorkspaceDao {

    @Query("SELECT * FROM workspace_entity WHERE id = :id LIMIT 1")
    suspend fun getWorkspaceById(id: String): WorkspaceEntity?

    @Query("SELECT * FROM workspace_entity WHERE selected = 1 LIMIT 1")
    suspend fun currentWorkspace(): WorkspaceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(workspace: WorkspaceEntity)

    @Query("SELECT * FROM workspace_entity WHERE userId = :userId ORDER BY name COLLATE NOCASE")
    suspend fun workspacesOfUser(userId: String): List<WorkspaceEntity>

    @Query("UPDATE workspace_entity SET name = :name WHERE id = :id")
    suspend fun updateName(id: String, name: String): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(workspace: WorkspaceEntity): Long

    @Query("DELETE FROM workspace_entity WHERE userId = :userId AND selected = 0 AND id NOT IN (:keepIds)")
    suspend fun deleteUnselectedNotIn(userId: String, keepIds: List<String>)

    @Transaction
    suspend fun cacheNames(userId: String, workspaces: List<WorkspaceEntity>) {
        workspaces.forEach { workspace ->
            if (updateName(workspace.id, workspace.name) == 0) {
                insertIfAbsent(workspace)
            }
        }

        deleteUnselectedNotIn(userId, workspaces.map { it.id })
    }

    @Query("UPDATE workspace_entity SET selected = 0")
    suspend fun unselectAllWorkspaces()

    @Query("UPDATE workspace_entity SET lastEventSyncAt = :lastEventSync WHERE id = :workspaceId")
    suspend fun updateLastEventSync(workspaceId: String, lastEventSync: Long)
}
