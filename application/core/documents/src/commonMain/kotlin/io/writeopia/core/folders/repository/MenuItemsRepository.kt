package io.writeopia.core.folders.repository

import io.writeopia.core.folders.api.DocumentsApi
import io.writeopia.sdk.models.document.Folder
import io.writeopia.sdk.models.document.MenuItem
import io.writeopia.sdk.models.utils.ResultData
import io.writeopia.sdk.serialization.extensions.toModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Shared repository for menu items (folders and documents) fetched from the backend.
 * Both the sidebar and center menu can observe this data source.
 */
class MenuItemsRepository(
    private val documentsApi: DocumentsApi
) {
    private val _menuItemsPerFolderId = MutableStateFlow<Map<String, List<MenuItem>>>(emptyMap())
    val menuItemsPerFolderId: StateFlow<Map<String, List<MenuItem>>> = _menuItemsPerFolderId.asStateFlow()

    suspend fun loadFolderContents(
        folderId: String,
        workspaceId: String,
        token: String
    ): ResultData<List<MenuItem>> {
        val result = documentsApi.getFolderContents(folderId, workspaceId, token)

        return if (result is ResultData.Complete) {
            val contents = result.data
            val folders: List<MenuItem> = contents.folders.map { it.toModel() }
            val documents: List<MenuItem> = contents.documents.map { it.toModel() }
            val allItems = folders + documents

            // Update the shared state
            val newMap = _menuItemsPerFolderId.value.toMutableMap()
            newMap[folderId] = allItems
            _menuItemsPerFolderId.value = newMap

            ResultData.Complete(allItems)
        } else {
            ResultData.Error()
        }
    }

    fun updateFolderInCache(folderId: String, folder: Folder) {
        val currentMap = _menuItemsPerFolderId.value.toMutableMap()

        // Find and update the folder in all parent folder lists
        currentMap.forEach { (parentId, items) ->
            val updatedItems = items.map { item ->
                if (item.id == folderId && item is Folder) {
                    folder
                } else {
                    item
                }
            }
            currentMap[parentId] = updatedItems
        }

        _menuItemsPerFolderId.value = currentMap
    }

    fun clearCache() {
        _menuItemsPerFolderId.value = emptyMap()
    }

    companion object {
        private var instance: MenuItemsRepository? = null

        fun singleton(documentsApi: DocumentsApi): MenuItemsRepository =
            instance ?: MenuItemsRepository(documentsApi).also {
                instance = it
            }
    }
}
