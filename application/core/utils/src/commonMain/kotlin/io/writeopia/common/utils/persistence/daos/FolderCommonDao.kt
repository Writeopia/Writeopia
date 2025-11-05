package io.writeopia.common.utils.persistence.daos

import io.writeopia.sdk.models.document.Folder
import kotlinx.coroutines.flow.Flow

interface FolderCommonDao {
    suspend fun upsertFolder(folderEntity: Folder)

    suspend fun getFolderById(id: String): Folder?

    suspend fun search(query: String, workspaceId: String): List<Folder>

    suspend fun getLastUpdated(): List<Folder>

    suspend fun getFolderByParentId(id: String): List<Folder>

    fun listenForFolderByParentId(id: String): Flow<List<Folder>>

    suspend fun deleteById(id: String): Int

    suspend fun deleteByParentId(id: String): Int
}
