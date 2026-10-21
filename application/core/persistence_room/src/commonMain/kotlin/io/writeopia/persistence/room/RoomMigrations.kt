package io.writeopia.persistence.room

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

val MIGRATION_29_30 = object : Migration(29, 30) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS COMMENT_ENTITY_TABLE (
                id TEXT NOT NULL PRIMARY KEY,
                conversation_id TEXT NOT NULL,
                document_id TEXT NOT NULL,
                comment_position INTEGER NOT NULL,
                text TEXT NOT NULL
            )
            """.trimIndent()
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS index_COMMENT_ENTITY_TABLE_document_id " +
                "ON COMMENT_ENTITY_TABLE(document_id)"
        )
    }
}

val MIGRATION_30_31 = object : Migration(30, 31) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "ALTER TABLE COMMENT_ENTITY_TABLE ADD COLUMN deleted INTEGER NOT NULL DEFAULT 0"
        )
    }
}

// Folders now remember when they were last sent to the backend, so a sync only pushes the
// ones changed since. Existing rows start as NULL (never synced) and are sent once.
val MIGRATION_31_32 = object : Migration(31, 32) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "ALTER TABLE FOLDER_ENTITY_TABLE ADD COLUMN last_synced_at INTEGER"
        )
    }
}

// The document tags: the link rows of a document and the workspace tag catalogue.
val MIGRATION_32_33 = object : Migration(32, 33) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS DOCUMENT_TAG_LINK_TABLE (
                document_id TEXT NOT NULL,
                tag_id TEXT NOT NULL,
                PRIMARY KEY(document_id, tag_id)
            )
            """.trimIndent()
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS index_DOCUMENT_TAG_LINK_TABLE_document_id " +
                "ON DOCUMENT_TAG_LINK_TABLE(document_id)"
        )
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS DOCUMENT_TAG_ENTITY_TABLE (
                id TEXT NOT NULL PRIMARY KEY,
                workspace_id TEXT NOT NULL,
                name TEXT NOT NULL,
                description TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                last_updated_at INTEGER NOT NULL,
                last_synced_at INTEGER,
                deleted INTEGER NOT NULL
            )
            """.trimIndent()
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS index_DOCUMENT_TAG_ENTITY_TABLE_workspace_id " +
                "ON DOCUMENT_TAG_ENTITY_TABLE(workspace_id)"
        )
    }
}

// When this device last received the contents of each folder, the root included. Starts
// empty, so every folder fetches its contents again once.
val MIGRATION_33_34 = object : Migration(33, 34) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS FOLDER_CONTENT_SYNC_TABLE (
                folder_id TEXT NOT NULL,
                workspace_id TEXT NOT NULL,
                content_synced_at INTEGER NOT NULL,
                PRIMARY KEY(folder_id, workspace_id)
            )
            """.trimIndent()
        )
    }
}

/**
 * Steps carry the time of their last edit, so the sync sends only the changed ones, and the
 * steps deleted here are remembered until the backend is told.
 */
val MIGRATION_34_35 = object : Migration(34, 35) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE STORY_UNIT_ENTITY_TABLE ADD COLUMN last_updated_at INTEGER")
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS STORY_STEP_TOMBSTONE_TABLE (
                step_id TEXT NOT NULL,
                document_id TEXT NOT NULL,
                workspace_id TEXT NOT NULL,
                deleted_at INTEGER NOT NULL,
                PRIMARY KEY(step_id)
            )
            """.trimIndent()
        )
    }
}

/**
 * Story step positions are whole numbers, 10000 apart (POSITION_GAP in the SDK), in an
 * INTEGER column. The steps of each document are renumbered in their order, children among
 * their siblings. SQLite can't change the type of a column, so the table is rebuilt.
 */
val MIGRATION_35_36 = object : Migration(35, 36) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            """
            CREATE TABLE STORY_UNIT_ENTITY_POSITIONS (
                id TEXT NOT NULL PRIMARY KEY,
                localId TEXT NOT NULL,
                type TEXT NOT NULL,
                parent_id TEXT,
                url TEXT,
                path TEXT,
                text TEXT,
                checked INTEGER,
                position INTEGER NOT NULL,
                document_id TEXT NOT NULL,
                is_group INTEGER NOT NULL,
                has_inner_steps INTEGER NOT NULL,
                background_color INTEGER,
                tags TEXT NOT NULL,
                spans TEXT NOT NULL,
                link_to_document TEXT,
                last_updated_at INTEGER
            )
            """.trimIndent()
        )
        connection.execSQL(
            """
            INSERT INTO STORY_UNIT_ENTITY_POSITIONS (
                id, localId, type, parent_id, url, path, text, checked, position, document_id,
                is_group, has_inner_steps, background_color, tags, spans, link_to_document, last_updated_at
            )
            SELECT
                s.id, s.localId, s.type, s.parent_id, s.url, s.path, s.text, s.checked,
                (
                    SELECT COUNT(*)
                    FROM STORY_UNIT_ENTITY_TABLE o
                    WHERE o.document_id = s.document_id
                      AND IFNULL(o.parent_id, '') = IFNULL(s.parent_id, '')
                      AND (o.position < s.position OR (o.position = s.position AND o.id < s.id))
                ) * 10000,
                s.document_id, s.is_group, s.has_inner_steps, s.background_color, s.tags, s.spans,
                s.link_to_document, s.last_updated_at
            FROM STORY_UNIT_ENTITY_TABLE s
            """.trimIndent()
        )
        connection.execSQL("DROP TABLE STORY_UNIT_ENTITY_TABLE")
        connection.execSQL("ALTER TABLE STORY_UNIT_ENTITY_POSITIONS RENAME TO STORY_UNIT_ENTITY_TABLE")
    }
}
