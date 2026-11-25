package io.writeopia.sdk.persistence.dao

import androidx.room.Dao
import androidx.room.Query
import io.writeopia.sdk.models.DOCUMENT_ENTITY
import io.writeopia.sdk.persistence.entity.story.STORY_STEP_FTS
import io.writeopia.sdk.persistence.entity.story.STORY_UNIT_ENTITY

/** A step whose text matched a search: the document it belongs to, and enough of the step to show it. */
data class StoryStepFtsMatch(
    val documentId: String,
    val title: String,
    val text: String?,
    val position: String,
)

@Dao
interface StoryStepFtsDao {

    /**
     * The steps of a workspace whose text matches, for the global search: joined with the live
     * steps and their documents, deleted documents left out. Only the steps that hold prose:
     * message, check item, list item, code block. The title is matched by the title search.
     * [match] is a MATCH expression built by `FtsQuery` (tokens by prefix), never raw user text.
     */
    @Query(
        """
        SELECT d.id AS documentId, d.title AS title, s.text AS text, s.position AS position
        FROM $STORY_STEP_FTS
        JOIN $STORY_UNIT_ENTITY AS s ON s.id = $STORY_STEP_FTS.id
        JOIN $DOCUMENT_ENTITY AS d ON d.id = s.document_id
        WHERE $STORY_STEP_FTS MATCH :match
          AND d.workspace_id = :workspaceId
          AND d.is_deleted = 0
          AND s.type IN ('message', 'check_item', 'unordered_list_item', 'code_block')
        ORDER BY d.id, s.position
        """
    )
    suspend fun searchContent(match: String, workspaceId: String): List<StoryStepFtsMatch>
}
