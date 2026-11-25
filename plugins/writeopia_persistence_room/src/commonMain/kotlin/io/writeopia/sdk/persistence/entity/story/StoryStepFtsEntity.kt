package io.writeopia.sdk.persistence.entity.story

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.FtsOptions

const val STORY_STEP_FTS: String = "STORY_STEP_FTS_TABLE"

/**
 * The full-text index of the text of the story steps, for the searches inside the app, like
 * `storyStepEntityFts` of the SQLDelight database. Only the text is indexed (unicode61: letters
 * of any alphabet fold their case, any punctuation separates the words); the id finds the row
 * of a step. Kept complete by the triggers of [StoryStepFtsTriggers] on [StoryStepEntity], so
 * nothing writes to it from Kotlin and no code path can leave it stale.
 */
@Fts4(tokenizer = FtsOptions.TOKENIZER_UNICODE61, notIndexed = ["id", "document_id", "position"])
@Entity(tableName = STORY_STEP_FTS)
data class StoryStepFtsEntity(
    @ColumnInfo(name = "text") val text: String,
    @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "document_id") val documentId: String,
    @ColumnInfo(name = "position") val position: String,
)

/**
 * The triggers that keep [StoryStepFtsEntity] complete, whatever writes a step (the editor, the
 * sync, an import). The insert trigger also drops the old row, because an upsert that replaces
 * a row fires no delete trigger. Room creates the table; the triggers are created by the
 * migration that added the index and on every open (`CREATE TRIGGER IF NOT EXISTS`), so a fresh
 * install has them too.
 */
object StoryStepFtsTriggers {

    /** The statements that create the triggers, one per element. */
    fun create(): List<String> = listOf(
        """
        CREATE TRIGGER IF NOT EXISTS story_step_fts_insert AFTER INSERT ON $STORY_UNIT_ENTITY
        BEGIN
          DELETE FROM $STORY_STEP_FTS WHERE id = new.id;
          INSERT INTO $STORY_STEP_FTS (text, id, document_id, position)
          SELECT new.text, new.id, new.document_id, new.position WHERE new.text IS NOT NULL;
        END
        """.trimIndent(),
        """
        CREATE TRIGGER IF NOT EXISTS story_step_fts_update AFTER UPDATE ON $STORY_UNIT_ENTITY
        WHEN new.text IS NOT old.text OR new.position IS NOT old.position OR new.document_id IS NOT old.document_id
        BEGIN
          DELETE FROM $STORY_STEP_FTS WHERE id = old.id;
          INSERT INTO $STORY_STEP_FTS (text, id, document_id, position)
          SELECT new.text, new.id, new.document_id, new.position WHERE new.text IS NOT NULL;
        END
        """.trimIndent(),
        """
        CREATE TRIGGER IF NOT EXISTS story_step_fts_delete AFTER DELETE ON $STORY_UNIT_ENTITY
        BEGIN
          DELETE FROM $STORY_STEP_FTS WHERE id = old.id;
        END
        """.trimIndent(),
    )

    /** Fills the index from the steps, after it was created or emptied. */
    fun rebuild(): List<String> = listOf(
        "DELETE FROM $STORY_STEP_FTS",
        "INSERT INTO $STORY_STEP_FTS (text, id, document_id, position) " +
            "SELECT text, id, document_id, position FROM $STORY_UNIT_ENTITY WHERE text IS NOT NULL",
    )
}
