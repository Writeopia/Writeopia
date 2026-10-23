package io.writeopia.global.shell.menu

import io.writeopia.sdk.models.document.MenuItem
import kotlinx.coroutines.flow.Flow

/**
 * Where the side menu gets its folders and documents from: the database of the device
 * ([LocalSideMenuSource]) or the backend only, kept in memory ([BackendSideMenuSource]).
 */
interface SideMenuSource {

    /** The children of each folder loaded so far, keyed by the id of the folder. */
    fun menuItemsPerFolderId(): Flow<Map<String, List<MenuItem>>>

    /**
     * Loads the folder the person navigates to, each time they do. Suspends until cancelled.
     * Does nothing when [menuItemsPerFolderId] already follows the navigation.
     */
    suspend fun followNavigation()

    /** Loads the children of a folder expanded in the side menu. */
    suspend fun expandFolder(folderId: String)

    /** Stops loading the children of a folder collapsed in the side menu. */
    fun collapseFolder(folderId: String)
}
