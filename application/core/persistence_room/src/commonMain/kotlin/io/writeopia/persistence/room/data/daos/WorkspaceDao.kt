package io.writeopia.persistence.room.data.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.writeopia.persistence.room.data.entities.WorkspaceEntity

@Dao
interface WorkspaceDao {

    @Query("SELECT * FROM workspace_entity WHERE id = :id LIMIT 1")
    suspend fun getWorkspaceById(id: String): WorkspaceEntity?

    @Query("SELECT * FROM workspace_entity WHERE selected = 1 LIMIT 1")
    suspend fun selectCurrentWorkspace(): WorkspaceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(workspace: WorkspaceEntity)
}
