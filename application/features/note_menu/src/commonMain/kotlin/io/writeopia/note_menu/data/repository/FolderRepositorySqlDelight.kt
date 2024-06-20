package io.writeopia.note_menu.data.repository

import io.writeopia.note_menu.data.model.Folder
import io.writeopia.note_menu.extensions.toModel
import io.writeopia.sqldelight.dao.FolderSqlDelightDao

class FolderRepositorySqlDelight(
    private val folderDao: FolderSqlDelightDao
) : FolderRepository {

    override suspend fun getRootFolders(userId: String): List<Folder> =
        folderDao.getRootFolders(userId).map { folderEntity ->
            folderEntity.toModel()
        }

    override suspend fun getChildrenFolders(userId: String, parentId: String): List<Folder> =
        folderDao.getChildrenFolders(userId, parentId).map { folderEntity ->
            folderEntity.toModel()
        }
}
