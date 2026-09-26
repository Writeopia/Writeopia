package io.writeopia.persistence.room

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

val MIGRATION_29_30 = object : Migration(29, 30) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS COMMENT_ENTITY_TABLE (
                id TEXT NOT NULL,
                conversation_id TEXT NOT NULL,
                document_id TEXT NOT NULL,
                comment_position INTEGER NOT NULL,
                text TEXT NOT NULL,
                PRIMARY KEY(document_id, id)
            )
            """.trimIndent()
        )
    }
}
