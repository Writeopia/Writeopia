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

val MIGRATION_31_32 = object : Migration(31, 32) {
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
