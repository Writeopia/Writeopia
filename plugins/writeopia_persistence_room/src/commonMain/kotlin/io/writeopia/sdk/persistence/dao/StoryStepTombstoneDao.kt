package io.writeopia.sdk.persistence.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.writeopia.sdk.persistence.entity.story.STORY_STEP_TOMBSTONE_ENTITY
import io.writeopia.sdk.persistence.entity.story.StoryStepTombstoneEntity

@Dao
interface StoryStepTombstoneDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(tombstone: StoryStepTombstoneEntity)

    @Query("SELECT * FROM $STORY_STEP_TOMBSTONE_ENTITY WHERE workspace_id = :workspaceId ORDER BY deleted_at")
    suspend fun loadByWorkspaceId(workspaceId: String): List<StoryStepTombstoneEntity>

    @Query("SELECT step_id FROM $STORY_STEP_TOMBSTONE_ENTITY WHERE step_id IN (:stepIds)")
    suspend fun stepIdsIn(stepIds: List<String>): List<String>

    @Query("DELETE FROM $STORY_STEP_TOMBSTONE_ENTITY WHERE step_id IN (:stepIds)")
    suspend fun deleteByStepIds(stepIds: List<String>)
}
