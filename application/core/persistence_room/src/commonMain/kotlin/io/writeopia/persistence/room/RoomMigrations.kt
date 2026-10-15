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

// The time this device last received a folder's contents, kept apart from last_synced_at.
// Starts empty, so every folder fetches its contents again once, which also repairs folders
// that were marked as synced without their contents ever being received.
val MIGRATION_33_34 = object : Migration(33, 34) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "ALTER TABLE FOLDER_ENTITY_TABLE ADD COLUMN content_synced_at INTEGER"
        )
    }
}
