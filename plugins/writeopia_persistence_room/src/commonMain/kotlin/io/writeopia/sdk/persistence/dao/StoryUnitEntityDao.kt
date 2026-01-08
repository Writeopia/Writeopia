package io.writeopia.sdk.persistence.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import io.writeopia.sdk.persistence.entity.story.STORY_UNIT_ENTITY
import io.writeopia.sdk.persistence.entity.story.StoryStepEntity

@Dao
interface StoryUnitEntityDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStoryUnits(vararg storyStep: StoryStepEntity)

    @Update
    suspend fun updateStoryStep(storyStep: StoryStepEntity)

    @Query("DELETE FROM $STORY_UNIT_ENTITY WHERE $STORY_UNIT_ENTITY.document_id = :documentId")
    suspend fun deleteDocumentContent(documentId: String)

    @Query(
        "SELECT * FROM $STORY_UNIT_ENTITY WHERE $STORY_UNIT_ENTITY.document_id = :documentId " +
            "ORDER BY position"
    )
    suspend fun loadDocumentContent(documentId: String): List<StoryStepEntity>

    @Query("SELECT * FROM $STORY_UNIT_ENTITY WHERE $STORY_UNIT_ENTITY.id = :storyId")
    suspend fun queryById(storyId: String): StoryStepEntity?

    @Query(
        "SELECT * FROM $STORY_UNIT_ENTITY WHERE $STORY_UNIT_ENTITY.parent_id = :parentId " +
            "ORDER BY position"
    )
    suspend fun queryInnerSteps(parentId: String): List<StoryStepEntity>

    @Query("UPDATE $STORY_UNIT_ENTITY  SET url = :url WHERE id = :id")
    suspend fun updateUrl(id: String, url: String?)

    @Query(
        "SELECT * FROM $STORY_UNIT_ENTITY " +
            "WHERE path IS NOT NULL AND path != '' " +
            "AND (url IS NULL OR url = '')"
    )
    suspend fun getUnsyncedSteps(): List<StoryStepEntity>
}
