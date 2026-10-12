package io.writeopia.sdk.persistence.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.writeopia.sdk.persistence.entity.tag.DOCUMENT_TAG_LINK_ENTITY
import io.writeopia.sdk.persistence.entity.tag.DocumentTagLinkEntity

@Dao
interface DocumentTagLinkDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertLinks(vararg links: DocumentTagLinkEntity)

    @Query("SELECT tag_id FROM $DOCUMENT_TAG_LINK_ENTITY WHERE document_id = :documentId")
    suspend fun loadTagIdsByDocumentId(documentId: String): List<String>

    @Query("DELETE FROM $DOCUMENT_TAG_LINK_ENTITY WHERE document_id = :documentId")
    suspend fun deleteByDocumentId(documentId: String)

    @Query("DELETE FROM $DOCUMENT_TAG_LINK_ENTITY WHERE document_id IN (:documentIds)")
    suspend fun deleteByDocumentIds(documentIds: List<String>)
}
