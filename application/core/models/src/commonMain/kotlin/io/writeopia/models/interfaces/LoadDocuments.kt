package io.writeopia.models.interfaces

import io.writeopia.sdk.models.document.MenuItem
import kotlinx.coroutines.flow.Flow

interface LoadDocuments {

    suspend fun listenForMenuItemsByParentId(
        parentId: String,
        userId: String,
    ): Flow<Map<String, List<MenuItem>>>
}
